package com.example.data.local

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * Describes an assignment between a workspace instance and a logical worker process slot.
 *
 * @property instanceId Sanitized identifier of the assigned workspace instance.
 * @property workerProcessName Configured worker process suffix or name (e.g., `:w0` through `:w7`).
 * @property slotIndex Zero-based index into the router's configured worker slot list.
 * @property assignedAt Epoch timestamp in milliseconds when the slot was assigned.
 */
data class WorkerRouteAssignment(
    val instanceId: String,
    val workerProcessName: String,
    val slotIndex: Int,
    val assignedAt: Long
)

/**
 * Maps virtual workspace instances to isolated worker process slots (`:w0` through `:w7` by
 * default) and resolves isolated storage directories and Android [Context] wrappers for the
 * active or requested workspace instance.
 *
 * Thread-safe across concurrent callers via internal synchronization on slot assignments.
 *
 * @param workspaceManager Registry tracking available and active [WorkspaceInstance] entries.
 * @param storageManager Storage manager providing per-instance directory resolution.
 * @param workerNames Ordered pool of worker process slot names (defaults to `:w0`..`:w7`).
 * @param clock Monotonic/wall-clock provider for assignment timestamps.
 */
class WorkspaceProcessRouter(
    private val workspaceManager: WorkspaceManager,
    private val storageManager: WorkspaceStorageManager,
    workerNames: List<String> = DEFAULT_WORKER_NAMES,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Normalized list of worker process slot names managed by this router.
     */
    val configuredWorkerNames: List<String> = workerNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .also {
            require(it.isNotEmpty()) { "At least one non-blank worker process name must be configured." }
        }

    private val lock = Any()
    private val assignments = LinkedHashMap<String, WorkerRouteAssignment>()

    /**
     * Restores persisted [WorkspaceInstance.workerSlot] assignments from [WorkspaceManager]
     * into the in-memory routing table so process slot affinities survive app restarts.
     *
     * @return Number of persisted slot assignments hydrated into memory.
     */
    fun hydrateFromManager(): Int {
        val registered = workspaceManager.listInstances()
        var hydrated = 0
        synchronized(lock) {
            registered.forEach { instance ->
                val persistedSlot = instance.workerSlot?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                val slotIndex = configuredWorkerNames.indexOf(persistedSlot)
                if (slotIndex >= 0 && !assignments.containsKey(instance.instanceId)) {
                    assignments[instance.instanceId] = WorkerRouteAssignment(
                        instanceId = instance.instanceId,
                        workerProcessName = persistedSlot,
                        slotIndex = slotIndex,
                        assignedAt = instance.createdAt
                    )
                    hydrated++
                }
            }
        }
        return hydrated
    }

    /**
     * Assigns an isolated worker process slot to [instanceId], or returns its existing assignment
     * if already routed or persisted in [WorkspaceManager].
     *
     * When assigning a new slot, selects the least-loaded worker slot (breaking ties by lowest
     * slot index), filling `:w0`..`:w7` cleanly before reusing slots for additional instances.
     */
    fun assignWorkerSlot(instanceId: String): Result<WorkerRouteAssignment> = runCatching {
        val safeId = storageManager.sanitizeInstanceId(instanceId)
        val persistedSlot = workspaceManager.getInstance(safeId)?.workerSlot?.trim()
        synchronized(lock) {
            assignments[safeId]?.let { return@runCatching it }

            if (!persistedSlot.isNullOrEmpty()) {
                val persistedIndex = configuredWorkerNames.indexOf(persistedSlot)
                if (persistedIndex >= 0) {
                    val restored = WorkerRouteAssignment(
                        instanceId = safeId,
                        workerProcessName = configuredWorkerNames[persistedIndex],
                        slotIndex = persistedIndex,
                        assignedAt = clock()
                    )
                    assignments[safeId] = restored
                    return@runCatching restored
                }
            }

            val loadBySlot = IntArray(configuredWorkerNames.size)
            assignments.values.forEach { assignment ->
                if (assignment.slotIndex in loadBySlot.indices) {
                    loadBySlot[assignment.slotIndex]++
                }
            }

            var bestIndex = 0
            var minLoad = loadBySlot[0]
            for (idx in 1 until loadBySlot.size) {
                if (loadBySlot[idx] < minLoad) {
                    minLoad = loadBySlot[idx]
                    bestIndex = idx
                }
            }

            val created = WorkerRouteAssignment(
                instanceId = safeId,
                workerProcessName = configuredWorkerNames[bestIndex],
                slotIndex = bestIndex,
                assignedAt = clock()
            )
            assignments[safeId] = created
            created
        }
    }

    /**
     * Assigns a worker process slot to [instanceId] and persists that slot in [WorkspaceManager]
     * if the instance is registered and its stored `workerSlot` differs.
     */
    suspend fun assignAndPersistWorkerSlot(instanceId: String): Result<WorkerRouteAssignment> = runCatching {
        val assignment = assignWorkerSlot(instanceId).getOrThrow()
        val existing = workspaceManager.getInstance(assignment.instanceId)
        if (existing != null && existing.workerSlot != assignment.workerProcessName) {
            workspaceManager.updateWorkerSlot(assignment.instanceId, assignment.workerProcessName).getOrThrow()
        }
        assignment
    }

    /**
     * Returns the worker process slot name assigned to [instanceId], or `null` if unassigned.
     */
    fun getAssignedWorkerSlot(instanceId: String): String? = runCatching {
        val safeId = storageManager.sanitizeInstanceId(instanceId)
        synchronized(lock) { assignments[safeId]?.workerProcessName }
            ?: workspaceManager.getInstance(safeId)?.workerSlot
    }.getOrNull()

    /**
     * Returns the full [WorkerRouteAssignment] metadata for [instanceId], or `null` if unassigned.
     */
    fun getAssignment(instanceId: String): WorkerRouteAssignment? = runCatching {
        val safeId = storageManager.sanitizeInstanceId(instanceId)
        synchronized(lock) { assignments[safeId] }
    }.getOrNull()

    /**
     * Releases any worker slot mapping associated with [instanceId].
     *
     * @return `true` if a slot assignment was removed, `false` if none existed or ID was invalid.
     */
    fun releaseWorkerSlot(instanceId: String): Boolean = runCatching {
        val safeId = storageManager.sanitizeInstanceId(instanceId)
        synchronized(lock) { assignments.remove(safeId) != null }
    }.getOrDefault(false)

    /**
     * Removes slot mappings for instances no longer registered in [WorkspaceManager].
     *
     * @return Number of stale slot assignments removed.
     */
    fun pruneUnregisteredSlots(): Int {
        val registeredIds = workspaceManager.listInstances().map { it.instanceId }.toSet()
        synchronized(lock) {
            val before = assignments.size
            val iterator = assignments.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().key !in registeredIds) {
                    iterator.remove()
                }
            }
            return before - assignments.size
        }
    }

    /**
     * Returns an immutable snapshot of all active instance-to-worker assignments.
     */
    fun getAllAssignments(): Map<String, WorkerRouteAssignment> = synchronized(lock) {
        assignments.toMap()
    }

    /**
     * Resolves the isolated directory structure for [instanceId].
     *
     * @param ensureInitialized When `true`, ensures `data`, `cache`, `databases`, and `shared_prefs`
     * exist on disk before returning.
     */
    fun resolveInstancePaths(
        instanceId: String,
        ensureInitialized: Boolean = true
    ): Result<WorkspaceInstancePaths> {
        return if (ensureInitialized) {
            storageManager.initializeInstanceStorageSync(instanceId)
        } else {
            runCatching { storageManager.getInstancePaths(instanceId) }
        }
    }

    /**
     * Resolves the isolated directory structure for whichever [WorkspaceInstance] is currently
     * marked active in [WorkspaceManager].
     *
     * Fails with [IllegalStateException] if no active instance is selected.
     */
    fun resolveActiveInstancePaths(
        ensureInitialized: Boolean = true
    ): Result<WorkspaceInstancePaths> {
        val active = workspaceManager.getActiveInstance()
            ?: return Result.failure(IllegalStateException("No active workspace instance selected."))
        return resolveInstancePaths(active.instanceId, ensureInitialized)
    }

    /**
     * Resolves the isolated XML preference file path inside `shared_prefs` for [instanceId]
     * (or for the active instance when [instanceId] is `null`).
     */
    fun resolvePreferenceFile(
        preferenceName: String,
        instanceId: String? = null,
        ensureInitialized: Boolean = true
    ): Result<File> {
        val pathsResult = if (instanceId != null) {
            resolveInstancePaths(instanceId, ensureInitialized)
        } else {
            resolveActiveInstancePaths(ensureInitialized)
        }
        return pathsResult.map { it.resolvePreferenceFile(preferenceName) }
    }

    /**
     * Resolves an isolated database file path inside `databases` for [instanceId]
     * (or for the active instance when [instanceId] is `null`).
     */
    fun resolveDatabaseFile(
        databaseName: String,
        instanceId: String? = null,
        ensureInitialized: Boolean = true
    ): Result<File> {
        val pathsResult = if (instanceId != null) {
            resolveInstancePaths(instanceId, ensureInitialized)
        } else {
            resolveActiveInstancePaths(ensureInitialized)
        }
        return pathsResult.map { it.resolveDatabaseFile(databaseName) }
    }

    /**
     * Builds an isolated [WorkspaceContextWrapper] bound to [instanceId], assigning a worker slot
     * if one has not yet been allocated.
     */
    fun createInstanceContext(
        baseContext: Context,
        instanceId: String,
        ensureInitialized: Boolean = true
    ): Result<WorkspaceContextWrapper> = runCatching {
        val paths = resolveInstancePaths(instanceId, ensureInitialized).getOrThrow()
        val route = assignWorkerSlot(paths.instanceId).getOrThrow()
        WorkspaceContextWrapper(
            base = baseContext.applicationContext ?: baseContext,
            instancePaths = paths,
            workerSlot = route.workerProcessName
        )
    }

    /**
     * Builds an isolated [WorkspaceContextWrapper] bound to the currently active workspace instance.
     */
    fun resolveActiveContext(
        baseContext: Context,
        ensureInitialized: Boolean = true
    ): Result<WorkspaceContextWrapper> = runCatching {
        val active = workspaceManager.getActiveInstance()
            ?: throw IllegalStateException("No active workspace instance selected.")
        createInstanceContext(baseContext, active.instanceId, ensureInitialized).getOrThrow()
    }

    companion object {
        /**
         * Default pool of eight worker process slots (`:w0` through `:w7`).
         */
        val DEFAULT_WORKER_NAMES: List<String> = (0..7).map { ":w$it" }
    }
}

/**
 * Isolated [ContextWrapper] that redirects file, cache, database, external storage, and
 * file-backed [SharedPreferences] operations into a specific [WorkspaceInstancePaths] container.
 *
 * @property instancePaths Resolved isolated filesystem paths for this instance.
 * @property workerSlot Assigned worker process slot label (e.g., `:w0`), if routed through [WorkspaceProcessRouter].
 */
class WorkspaceContextWrapper(
    base: Context,
    val instancePaths: WorkspaceInstancePaths,
    val workerSlot: String? = null
) : ContextWrapper(base) {

    private val prefsCacheLock = Any()
    private val sharedPrefsCache = HashMap<String, WorkspaceSharedPreferences>()

    override fun getDataDir(): File = ensureDir(instancePaths.baseDir)

    override fun getFilesDir(): File = ensureDir(instancePaths.dataDir)

    override fun getCacheDir(): File = ensureDir(instancePaths.cacheDir)

    override fun getCodeCacheDir(): File = ensureDir(File(instancePaths.cacheDir, "code_cache"))

    override fun getNoBackupFilesDir(): File = ensureDir(File(instancePaths.dataDir, "no_backup"))

    override fun getDir(name: String, mode: Int): File {
        val safeName = sanitizeSegment(name, fallback = "dir")
        return ensureDir(File(instancePaths.dataDir, "app_$safeName"))
    }

    override fun getFileStreamPath(name: String): File {
        val safeName = sanitizeSegment(name, fallback = "file")
        return File(getFilesDir(), safeName)
    }

    @Throws(FileNotFoundException::class)
    override fun openFileInput(name: String): FileInputStream {
        val target = getFileStreamPath(name)
        return FileInputStream(target)
    }

    @Throws(FileNotFoundException::class)
    override fun openFileOutput(name: String, mode: Int): FileOutputStream {
        val target = getFileStreamPath(name)
        val append = (mode and MODE_APPEND) != 0
        return FileOutputStream(target, append)
    }

    override fun deleteFile(name: String): Boolean {
        val target = getFileStreamPath(name)
        return !target.exists() || target.delete()
    }

    override fun fileList(): Array<String> {
        val files = getFilesDir().list() ?: return emptyArray()
        return files
    }

    override fun getDatabasePath(name: String): File {
        ensureDir(instancePaths.databasesDir)
        return instancePaths.resolveDatabaseFile(name)
    }

    override fun databaseList(): Array<String> {
        return instancePaths.databasesDir.list() ?: emptyArray()
    }

    override fun deleteDatabase(name: String): Boolean {
        val dbFile = getDatabasePath(name)
        val sidecars = listOf("", "-journal", "-wal", "-shm").map { suffix ->
            File(dbFile.parentFile, "${dbFile.name}$suffix")
        }
        var deletedAny = false
        sidecars.forEach { file ->
            if (file.exists() && file.delete()) {
                deletedAny = true
            }
        }
        return deletedAny
    }

    override fun getExternalFilesDir(type: String?): File? {
        val extRoot = instancePaths.externalDataDir ?: return null
        val target = if (type.isNullOrBlank()) {
            extRoot
        } else {
            File(extRoot, sanitizeSegment(type, fallback = "files"))
        }
        return ensureDir(target)
    }

    override fun getExternalCacheDir(): File? {
        val extCache = instancePaths.externalCacheDir ?: return null
        return ensureDir(extCache)
    }

    /**
     * Returns the isolated XML preference file target inside [WorkspaceInstancePaths.sharedPrefsDir].
     */
    fun getSharedPreferencesFile(name: String): File {
        ensureDir(instancePaths.sharedPrefsDir)
        return instancePaths.resolvePreferenceFile(name)
    }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val isolatedFile = getSharedPreferencesFile(name)
        val cacheKey = isolatedFile.name
        return synchronized(prefsCacheLock) {
            val cached = sharedPrefsCache[cacheKey]
            if (cached != null) {
                cached
            } else {
                WorkspaceSharedPreferences(isolatedFile).also { created ->
                    sharedPrefsCache[cacheKey] = created
                }
            }
        }
    }

    override fun deleteSharedPreferences(name: String): Boolean {
        val isolatedFile = getSharedPreferencesFile(name)
        val cacheKey = isolatedFile.name
        val cached = synchronized(prefsCacheLock) {
            sharedPrefsCache.remove(cacheKey)
        }
        val fileRemoved = cached?.deleteBackingFiles()
            ?: (!isolatedFile.exists() || isolatedFile.delete())
        val tempFile = File(isolatedFile.parentFile, "${isolatedFile.name}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        val legacyHostRemoved = runCatching {
            super.deleteSharedPreferences(legacyScopedPreferencesName(name))
        }.getOrDefault(false)
        return fileRemoved || legacyHostRemoved
    }

    private fun legacyScopedPreferencesName(name: String): String {
        val safePref = sanitizeSegment(name, fallback = "default")
        return "ws_${instancePaths.instanceId}_$safePref"
    }

    private fun ensureDir(dir: File): File {
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) {
            throw IllegalStateException("Unable to create isolated directory: ${dir.absolutePath}")
        }
        if (dir.exists() && !dir.isDirectory) {
            throw IOException("Expected directory but found file at: ${dir.absolutePath}")
        }
        return dir
    }

    private fun sanitizeSegment(raw: String, fallback: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") fallback else cleaned
    }
}
