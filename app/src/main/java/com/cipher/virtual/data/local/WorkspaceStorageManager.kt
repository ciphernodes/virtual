package com.cipher.virtual.data.local

import android.content.Context
import com.cipher.virtual.data.hardware.DeviceProfile
import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.data.model.VirtualSpaceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Snapshot of file and storage metrics inside a virtual instance workspace.
 */
data class InstanceWorkspaceStats(
    val directoryPath: String,
    val sizeBytes: Long,
    val fileCount: Int,
    val lastSessionRecordedAt: Long?
)

/**
 * Resolved directory layout for an isolated virtual workspace instance.
 *
 * Every instance receives independent `data`, `cache`, `databases`, and `shared_prefs`
 * subdirectories under the host app's internal storage (plus optional external storage mirrors)
 * so concurrent or cloned instances never collide on local state.
 */
data class WorkspaceInstancePaths(
    val instanceId: String,
    val baseDir: File,
    val dataDir: File,
    val cacheDir: File,
    val databasesDir: File,
    val sharedPrefsDir: File,
    val externalBaseDir: File? = null,
    val externalDataDir: File? = null,
    val externalCacheDir: File? = null
) {
    /**
     * Resolves an isolated database file path inside this instance's [databasesDir].
     */
    fun resolveDatabaseFile(databaseName: String): File {
        val safeName = sanitizeChildSegment(databaseName, fallback = "workspace.db")
        return File(databasesDir, safeName)
    }

    /**
     * Resolves an isolated preference XML file path inside this instance's [sharedPrefsDir].
     */
    fun resolvePreferenceFile(preferenceName: String): File {
        val safeName = sanitizeChildSegment(preferenceName, fallback = "default")
        val fileName = if (safeName.endsWith(".xml", ignoreCase = true)) safeName else "$safeName.xml"
        return File(sharedPrefsDir, fileName)
    }

    /**
     * Returns all internal and optional external directories belonging to this instance.
     */
    fun allDirectories(): List<File> = listOfNotNull(
        baseDir,
        dataDir,
        cacheDir,
        databasesDir,
        sharedPrefsDir,
        externalBaseDir,
        externalDataDir,
        externalCacheDir
    )

    private fun sanitizeChildSegment(raw: String, fallback: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") fallback else cleaned
    }
}

/**
 * Manages isolated per-instance workspace directories under the host app's internal
 * and optional external storage roots (`workspaces/<instanceId>/data`, `cache`,
 * `databases`, and `shared_prefs`).
 *
 * All path operations sanitize instance identifiers and verify canonical containment inside
 * the managed workspaces root to prevent directory traversal.
 *
 * @param rootDir Base internal directory (typically `context.filesDir`).
 * @param externalRootDir Optional external app-specific directory (typically `context.getExternalFilesDir(null)`).
 */
class WorkspaceStorageManager(
    private val rootDir: File,
    private val externalRootDir: File? = null
) {

    /**
     * Convenience constructor configuring both internal `filesDir` and optional external `filesDir`
     * from an Android [Context].
     */
    constructor(context: Context) : this(
        rootDir = context.applicationContext.filesDir,
        externalRootDir = runCatching { context.applicationContext.getExternalFilesDir(null) }.getOrNull()
    )

    private val workspacesRoot: File
        get() = File(rootDir, WORKSPACES_FOLDER)

    private val externalWorkspacesRoot: File?
        get() = externalRootDir?.let { File(it, WORKSPACES_FOLDER) }

    /**
     * Returns the shared registry JSON file location under [workspacesRoot].
     */
    fun registryFile(): File = File(workspacesRoot, REGISTRY_FILE)

    /**
     * Validates and sanitizes an [instanceId] so it forms a safe single path segment inside
     * [workspacesRoot].
     *
     * @throws IllegalArgumentException if [instanceId] is blank, contains path separators,
     * traversal tokens, or resolves outside the managed root.
     */
    @Throws(IllegalArgumentException::class)
    fun sanitizeInstanceId(instanceId: String): String {
        val trimmed = instanceId.trim()
        require(trimmed.isNotEmpty()) { "Instance ID must not be blank." }
        require(!trimmed.contains('/') && !trimmed.contains('\\') && !trimmed.contains('\u0000')) {
            "Instance ID '$instanceId' contains illegal path characters."
        }
        require(trimmed != "." && trimmed != ".." && !trimmed.contains("..")) {
            "Instance ID '$instanceId' contains illegal traversal sequence."
        }
        require(INSTANCE_ID_REGEX.matches(trimmed)) {
            "Instance ID '$instanceId' must match [A-Za-z0-9._-]+."
        }

        val rootCanonical = workspacesRoot.canonicalFile
        val candidateCanonical = File(workspacesRoot, trimmed).canonicalFile
        require(candidateCanonical.parentFile == rootCanonical) {
            "Resolved instance path escapes managed workspaces directory."
        }
        return trimmed
    }

    /**
     * Computes the isolated [WorkspaceInstancePaths] for [instanceId] without modifying disk state.
     *
     * @throws IllegalArgumentException if [instanceId] fails path-safety validation.
     */
    @Throws(IllegalArgumentException::class)
    fun getInstancePaths(instanceId: String): WorkspaceInstancePaths {
        val safeId = sanitizeInstanceId(instanceId)
        val baseDir = File(workspacesRoot, safeId)
        val extBaseDir = externalWorkspacesRoot?.let { File(it, safeId) }
        return WorkspaceInstancePaths(
            instanceId = safeId,
            baseDir = baseDir,
            dataDir = File(baseDir, DATA_DIR),
            cacheDir = File(baseDir, CACHE_DIR),
            databasesDir = File(baseDir, DATABASES_DIR),
            sharedPrefsDir = File(baseDir, SHARED_PREFS_DIR),
            externalBaseDir = extBaseDir,
            externalDataDir = extBaseDir?.let { File(it, DATA_DIR) },
            externalCacheDir = extBaseDir?.let { File(it, CACHE_DIR) }
        )
    }

    /**
     * Returns `true` if the base workspace directory for [instanceId] currently exists on disk.
     */
    fun instanceExists(instanceId: String): Boolean = runCatching {
        getInstancePaths(instanceId).baseDir.isDirectory
    }.getOrDefault(false)

    /**
     * Ensures all isolated directories (`data`, `cache`, `databases`, `shared_prefs`, and
     * optional external mirrors) exist for [instanceId], creating them if necessary.
     *
     * Safe to invoke from any thread; runs blocking IO on [Dispatchers.IO].
     */
    suspend fun initializeInstanceStorage(instanceId: String): Result<WorkspaceInstancePaths> =
        withContext(Dispatchers.IO) {
            initializeInstanceStorageSync(instanceId)
        }

    /**
     * Synchronous helper used by both coroutine APIs and Android context wrappers to ensure
     * all isolated subdirectories exist for [instanceId].
     */
    fun initializeInstanceStorageSync(instanceId: String): Result<WorkspaceInstancePaths> = runCatching {
        val paths = getInstancePaths(instanceId)
        ensureDirectoryExists(workspacesRoot)
        externalWorkspacesRoot?.let { ensureDirectoryExists(it) }
        paths.allDirectories().forEach { dir ->
            ensureDirectoryExists(dir)
        }
        paths
    }

    /**
     * Collects file count, total byte size, and latest modification timestamp across an
     * instance's internal and external workspace directories.
     */
    suspend fun inspectInstanceStorage(instanceId: String): Result<InstanceWorkspaceStats> =
        withContext(Dispatchers.IO) {
            runCatching {
                val paths = getInstancePaths(instanceId)
                var totalBytes = 0L
                var fileCount = 0
                var latestModified: Long? = null

                listOfNotNull(paths.baseDir, paths.externalBaseDir)
                    .filter { it.exists() }
                    .forEach { root ->
                        root.walkTopDown()
                            .filter { it.isFile }
                            .forEach { file ->
                                totalBytes += file.length()
                                fileCount++
                                val modified = file.lastModified()
                                if (modified > 0L && (latestModified == null || modified > latestModified!!)) {
                                    latestModified = modified
                                }
                            }
                    }

                InstanceWorkspaceStats(
                    directoryPath = paths.baseDir.absolutePath,
                    sizeBytes = totalBytes,
                    fileCount = fileCount,
                    lastSessionRecordedAt = latestModified
                )
            }
        }

    /**
     * Safely wipes all stored files inside [instanceId]'s isolated directories and re-creates
     * clean empty `data`, `cache`, `databases`, and `shared_prefs` subdirectories.
     */
    suspend fun resetInstanceStorage(instanceId: String): Result<WorkspaceInstancePaths> =
        withContext(Dispatchers.IO) {
            runCatching {
                val paths = getInstancePaths(instanceId)
                deleteDirectoryIfPresent(paths.baseDir)
                paths.externalBaseDir?.let { deleteDirectoryIfPresent(it) }
                initializeInstanceStorageSync(paths.instanceId).getOrThrow()
            }
        }

    /**
     * Permanently deletes all internal and optional external storage directories for [instanceId].
     */
    suspend fun deleteInstanceStorage(instanceId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val paths = getInstancePaths(instanceId)
                val internalDeleted = deleteDirectoryIfPresent(paths.baseDir)
                val externalDeleted = paths.externalBaseDir?.let { deleteDirectoryIfPresent(it) } ?: true
                if (!internalDeleted || !externalDeleted) {
                    throw IOException("Failed to completely remove storage for instance '${paths.instanceId}'.")
                }
                true
            }
        }

    /**
     * Returns the legacy per-space folder used by Room-backed space metadata snapshots.
     */
    fun spaceDirectory(spaceId: Long): File = File(workspacesRoot, "space_$spaceId")

    /**
     * Returns the legacy per-app folder within [spaceDirectory] used by session metadata recording.
     */
    fun instanceDirectory(spaceId: Long, appId: Long): File =
        File(spaceDirectory(spaceId), "instance_$appId")

    /**
     * Records a virtual app launch session metadata snapshot inside [instanceDirectory].
     */
    suspend fun recordSessionMetadata(
        space: VirtualSpaceEntity,
        app: VirtualAppEntity,
        profile: DeviceProfile?
    ): File = withContext(Dispatchers.IO) {
        val dir = instanceDirectory(space.id, app.id)
        ensureDirectoryExists(dir)
        val configFile = File(dir, SESSION_CONFIG_FILE)
        val json = buildString {
            append('{')
            append("\"spaceId\":").append(space.id).append(',')
            append("\"spaceName\":\"").append(escape(space.name)).append("\",")
            append("\"appId\":").append(app.id).append(',')
            append("\"packageName\":\"").append(escape(app.packageName)).append("\",")
            append("\"instanceLabel\":\"").append(escape(app.instanceLabel)).append("\",")
            append("\"performanceMode\":\"").append(escape(app.performanceMode)).append("\",")
            append("\"virtualDpi\":").append(space.virtualDpi).append(',')
            append("\"isolatedStorage\":").append(space.isolatedStorage).append(',')
            append("\"recordedAt\":").append(System.currentTimeMillis())
            if (profile != null) {
                append(",\"profileId\":\"").append(escape(profile.profileId)).append("\",")
                append("\"profileModel\":\"").append(escape(profile.model)).append("\",")
                append("\"profileManufacturer\":\"").append(escape(profile.manufacturer)).append("\",")
                append("\"sdkInt\":").append(profile.sdkInt)
            }
            append('}')
        }
        configFile.writeText(json)
        configFile
    }

    /**
     * Inspects file metrics inside a legacy [spaceId]/[appId] instance workspace.
     */
    suspend fun inspectInstanceWorkspace(spaceId: Long, appId: Long): InstanceWorkspaceStats =
        withContext(Dispatchers.IO) {
            val dir = instanceDirectory(spaceId, appId)
            if (!dir.exists()) {
                return@withContext InstanceWorkspaceStats(
                    directoryPath = dir.absolutePath,
                    sizeBytes = 0L,
                    fileCount = 0,
                    lastSessionRecordedAt = null
                )
            }
            var totalBytes = 0L
            var count = 0
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                totalBytes += file.length()
                count++
            }
            val configFile = File(dir, SESSION_CONFIG_FILE)
            val recordedAt = if (configFile.exists()) configFile.lastModified() else null
            InstanceWorkspaceStats(
                directoryPath = dir.absolutePath,
                sizeBytes = totalBytes,
                fileCount = count,
                lastSessionRecordedAt = recordedAt
            )
        }

    /**
     * Clears a legacy [spaceId]/[appId] instance workspace directory.
     */
    suspend fun resetInstanceWorkspace(spaceId: Long, appId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val dir = instanceDirectory(spaceId, appId)
            runCatching { deleteDirectoryIfPresent(dir) }.getOrDefault(false)
        }

    /**
     * Deletes all legacy instance folders under [spaceDirectory].
     */
    suspend fun deleteSpaceWorkspaces(spaceId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val dir = spaceDirectory(spaceId)
            runCatching { deleteDirectoryIfPresent(dir) }.getOrDefault(false)
        }

    private fun ensureDirectoryExists(dir: File) {
        if (dir.exists()) {
            if (!dir.isDirectory) {
                throw IOException("Expected directory at ${dir.absolutePath}, but found a regular file.")
            }
            return
        }
        if (!dir.mkdirs() && !dir.isDirectory) {
            throw IOException("Unable to create workspace directory: ${dir.absolutePath}")
        }
    }

    private fun deleteDirectoryIfPresent(dir: File): Boolean {
        if (!dir.exists()) return true
        val deleted = dir.deleteRecursively()
        if (!deleted && dir.exists()) {
            throw IOException("Unable to delete workspace directory: ${dir.absolutePath}")
        }
        return true
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private const val WORKSPACES_FOLDER = "workspaces"
        private const val DATA_DIR = "data"
        private const val CACHE_DIR = "cache"
        private const val DATABASES_DIR = "databases"
        private const val SHARED_PREFS_DIR = "shared_prefs"
        private const val REGISTRY_FILE = "workspace_registry.json"
        private const val SESSION_CONFIG_FILE = "session_config.json"
        private val INSTANCE_ID_REGEX = Regex("^[A-Za-z0-9._-]{1,96}$")
    }
}
