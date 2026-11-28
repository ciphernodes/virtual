package com.cipher.virtual.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Room database entity and domain model representing an isolated virtual workspace instance.
 *
 * Persists instance metadata locally in the `workspace_instances` table so labels, bound package
 * names, creation timestamps, and active-instance routing state survive app restarts.
 *
 * @property instanceId Unique filesystem-safe identifier for the instance's storage root.
 * @property label Human-readable name displayed to users (e.g., "Work Account #2").
 * @property targetPackageName Android package identifier associated with this workspace slot.
 * @property createdAt Epoch timestamp in milliseconds when the instance was created.
 * @property isActive Whether this instance is currently selected as the active workspace.
 * @property workerSlot Assigned worker process slot suffix (e.g., `:w0` through `:w7`), if routed.
 */
@Entity(
    tableName = "workspace_instances",
    indices = [
        Index(value = ["targetPackageName"]),
        Index(value = ["isActive"])
    ]
)
data class WorkspaceInstance(
    @PrimaryKey val instanceId: String,
    val label: String,
    val targetPackageName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false,
    val workerSlot: String? = null
)

/**
 * Room Data Access Object for persisting and querying [WorkspaceInstance] records.
 */
@Dao
interface WorkspaceInstanceDao {

    @Query("SELECT * FROM workspace_instances ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<WorkspaceInstance>>

    @Query("SELECT * FROM workspace_instances ORDER BY createdAt ASC")
    suspend fun getAllSnapshot(): List<WorkspaceInstance>

    @Query("SELECT * FROM workspace_instances WHERE instanceId = :instanceId LIMIT 1")
    suspend fun getById(instanceId: String): WorkspaceInstance?

    @Query("SELECT * FROM workspace_instances WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): WorkspaceInstance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(instance: WorkspaceInstance)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(instances: List<WorkspaceInstance>)

    @Query("UPDATE workspace_instances SET isActive = 0")
    suspend fun clearActiveFlags()

    @Query("UPDATE workspace_instances SET isActive = 1 WHERE instanceId = :instanceId")
    suspend fun markActive(instanceId: String)

    @Query("UPDATE workspace_instances SET workerSlot = :workerSlot WHERE instanceId = :instanceId")
    suspend fun updateWorkerSlot(instanceId: String, workerSlot: String?)

    @Query("DELETE FROM workspace_instances WHERE instanceId = :instanceId")
    suspend fun deleteById(instanceId: String): Int

    @Query("DELETE FROM workspace_instances")
    suspend fun deleteAll()

    /**
     * Atomically activates [instanceId] while clearing active flags on all other instances.
     */
    @Transaction
    suspend fun switchActiveAtomically(instanceId: String) {
        clearActiveFlags()
        markActive(instanceId)
    }

    /**
     * Atomically replaces the persisted instance table with [instances].
     */
    @Transaction
    suspend fun replaceAllAtomically(instances: List<WorkspaceInstance>) {
        deleteAll()
        if (instances.isNotEmpty()) {
            upsertAll(instances)
        }
    }
}

/**
 * Manages the Room-backed registry and lifecycle of multi-instance workspaces.
 *
 * Coordinates instance creation, active-instance switching, isolated storage resets,
 * and deletion while keeping database metadata and isolated storage directory trees synchronized
 * via coroutine [Mutex] exclusion. When [dao] is omitted (e.g., in standalone JVM unit tests),
 * falls back to atomic JSON persistence at [registryFile].
 *
 * @param storageManager Underlying storage manager controlling per-instance directory trees.
 * @param dao Optional Room [WorkspaceInstanceDao] for persistent SQLite storage.
 * @param registryFile Fallback JSON file used when [dao] is not supplied or during migration.
 * @param clock Monotonic/wall-clock provider for timestamps (overridable in unit tests).
 */
class WorkspaceManager(
    private val storageManager: WorkspaceStorageManager,
    private val dao: WorkspaceInstanceDao? = null,
    private val registryFile: File = storageManager.registryFile(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Convenience constructor wiring both [WorkspaceStorageManager] and Room [WorkspaceInstanceDao]
     * from an Android [Context].
     */
    constructor(context: Context) : this(
        storageManager = WorkspaceStorageManager(context),
        dao = OctoDatabase.getInstance(context.applicationContext).workspaceInstanceDao()
    )

    private val mutex = Mutex()

    private val _instances = MutableStateFlow<List<WorkspaceInstance>>(emptyList())

    /**
     * Reactive stream of all currently registered workspace instances.
     */
    val instances: StateFlow<List<WorkspaceInstance>> = _instances.asStateFlow()

    private val _activeInstance = MutableStateFlow<WorkspaceInstance?>(null)

    /**
     * Reactive stream emitting the currently active [WorkspaceInstance], or `null` if none exists.
     */
    val activeInstance: StateFlow<WorkspaceInstance?> = _activeInstance.asStateFlow()

    /**
     * Loads persisted instances from Room (importing any legacy JSON registry records if the
     * Room table is initially empty), ensures each registered instance has its isolated storage
     * directories initialized, and populates [instances] and [activeInstance].
     */
    suspend fun initialize(): Result<List<WorkspaceInstance>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val loaded = loadAllLocked()
                val normalized = normalizeActiveInvariant(loaded)
                if (normalized != loaded) {
                    persistAllLocked(normalized)
                }
                normalized.forEach { instance ->
                    storageManager.initializeInstanceStorageSync(instance.instanceId).getOrThrow()
                }
                publishStateLocked(normalized)
                normalized
            }
        }
    }

    /**
     * Registers a new [WorkspaceInstance], creates its isolated storage tree (`data`, `cache`,
     * `databases`, `shared_prefs`), persists it in Room, and optionally marks it active.
     *
     * @param label Non-blank display label for the instance.
     * @param targetPackageName Target Android package name bound to the instance.
     * @param instanceId Optional explicit identifier; generated automatically when omitted.
     * @param activate When `true` (or when this is the first instance), switches active focus to it.
     */
    suspend fun createInstance(
        label: String,
        targetPackageName: String,
        instanceId: String = generateInstanceId(targetPackageName),
        activate: Boolean = true,
        workerSlot: String? = null
    ): Result<WorkspaceInstance> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val trimmedLabel = label.trim()
                val trimmedPackage = targetPackageName.trim()
                require(trimmedLabel.isNotEmpty()) { "Workspace label must not be blank." }
                require(trimmedPackage.isNotEmpty()) { "Target package name must not be blank." }

                val safeId = storageManager.sanitizeInstanceId(instanceId)
                val current = ensureLoadedLocked()
                if (current.any { it.instanceId == safeId }) {
                    throw IllegalStateException("Workspace instance '$safeId' already exists.")
                }

                storageManager.initializeInstanceStorageSync(safeId).getOrThrow()

                val shouldActivate = activate || current.none { it.isActive }
                val newInstance = WorkspaceInstance(
                    instanceId = safeId,
                    label = trimmedLabel,
                    targetPackageName = trimmedPackage,
                    createdAt = clock(),
                    isActive = shouldActivate,
                    workerSlot = workerSlot?.trim()?.takeIf { it.isNotEmpty() }
                )

                val updated = if (shouldActivate) {
                    current.map { it.copy(isActive = false) } + newInstance
                } else {
                    current + newInstance
                }

                try {
                    if (dao != null) {
                        if (shouldActivate) {
                            dao.clearActiveFlags()
                        }
                        dao.upsert(newInstance)
                    } else {
                        persistRegistryJsonLocked(updated)
                    }
                } catch (err: Throwable) {
                    storageManager.deleteInstanceStorage(safeId)
                    throw err
                }

                publishStateLocked(updated)
                newInstance
            }
        }
    }

    /**
     * Switches the active workspace to [instanceId], ensures its isolated storage directories exist,
     * and persists the active flag in Room.
     */
    suspend fun switchActiveInstance(instanceId: String): Result<WorkspaceInstance> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val safeId = storageManager.sanitizeInstanceId(instanceId)
                    val current = ensureLoadedLocked()
                    val target = current.firstOrNull { it.instanceId == safeId }
                        ?: throw NoSuchElementException("Workspace instance '$safeId' was not found.")

                    storageManager.initializeInstanceStorageSync(safeId).getOrThrow()

                    val updated = current.map { entry ->
                        entry.copy(isActive = entry.instanceId == safeId)
                    }
                    if (dao != null) {
                        dao.switchActiveAtomically(safeId)
                    } else {
                        persistRegistryJsonLocked(updated)
                    }
                    publishStateLocked(updated)
                    target.copy(isActive = true)
                }
            }
        }

    /**
     * Persists the assigned worker process slot (`:w0`..`:w7`) for [instanceId] in Room
     * (or the fallback JSON registry).
     */
    suspend fun updateWorkerSlot(instanceId: String, workerSlot: String?): Result<WorkspaceInstance> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val safeId = storageManager.sanitizeInstanceId(instanceId)
                    val normalizedSlot = workerSlot?.trim()?.takeIf { it.isNotEmpty() }
                    val current = ensureLoadedLocked()
                    val target = current.firstOrNull { it.instanceId == safeId }
                        ?: throw NoSuchElementException("Workspace instance '$safeId' was not found.")
                    val updatedTarget = target.copy(workerSlot = normalizedSlot)
                    val updated = current.map { entry ->
                        if (entry.instanceId == safeId) updatedTarget else entry
                    }
                    if (dao != null) {
                        dao.updateWorkerSlot(safeId, normalizedSlot)
                    } else {
                        persistRegistryJsonLocked(updated)
                    }
                    publishStateLocked(updated)
                    updatedTarget
                }
            }
        }

    /**
     * Clears all stored `data`, `cache`, `databases`, and `shared_prefs` contents for [instanceId]
     * while keeping the instance registered and re-initializing clean empty subdirectories.
     */
    suspend fun clearInstanceData(instanceId: String): Result<WorkspaceInstancePaths> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val safeId = storageManager.sanitizeInstanceId(instanceId)
                    val current = ensureLoadedLocked()
                    if (current.none { it.instanceId == safeId }) {
                        throw NoSuchElementException("Workspace instance '$safeId' was not found.")
                    }
                    storageManager.resetInstanceStorage(safeId).getOrThrow()
                }
            }
        }

    /**
     * Unregisters [instanceId] from Room and permanently deletes its isolated storage directories.
     *
     * If the removed instance was active and other instances remain, the first remaining instance
     * is automatically promoted to active.
     */
    suspend fun removeInstance(instanceId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val safeId = storageManager.sanitizeInstanceId(instanceId)
                    val current = ensureLoadedLocked()
                    val target = current.firstOrNull { it.instanceId == safeId }
                        ?: return@runCatching false

                    val remaining = current.filterNot { it.instanceId == safeId }
                    val updated = if (target.isActive && remaining.isNotEmpty()) {
                        remaining.mapIndexed { index, entry ->
                            entry.copy(isActive = index == 0)
                        }
                    } else {
                        remaining
                    }

                    if (dao != null) {
                        dao.deleteById(safeId)
                        if (target.isActive && updated.isNotEmpty()) {
                            dao.switchActiveAtomically(updated.first().instanceId)
                        }
                    } else {
                        persistRegistryJsonLocked(updated)
                    }
                    publishStateLocked(updated)
                    storageManager.deleteInstanceStorage(safeId).getOrThrow()
                    true
                }
            }
        }

    /**
     * Returns an immutable snapshot list of registered instances currently in memory.
     */
    fun listInstances(): List<WorkspaceInstance> = _instances.value

    /**
     * Looks up a registered [WorkspaceInstance] by [instanceId] in memory.
     */
    fun getInstance(instanceId: String): WorkspaceInstance? =
        _instances.value.firstOrNull { it.instanceId == instanceId }

    /**
     * Returns the currently active [WorkspaceInstance] in memory, or `null` if none is active.
     */
    fun getActiveInstance(): WorkspaceInstance? = _activeInstance.value

    /**
     * Generates a readable, collision-resistant, filesystem-safe instance identifier derived
     * from [targetPackageName].
     */
    fun generateInstanceId(targetPackageName: String): String {
        val pkgSlug = targetPackageName.trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(40)
            .ifBlank { "instance" }
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        return "${pkgSlug}_$suffix"
    }

    private suspend fun ensureLoadedLocked(): List<WorkspaceInstance> {
        val cached = _instances.value
        if (cached.isNotEmpty()) {
            return cached
        }
        val loaded = normalizeActiveInvariant(loadAllLocked())
        if (loaded.isNotEmpty()) {
            publishStateLocked(loaded)
        }
        return loaded
    }

    private suspend fun loadAllLocked(): List<WorkspaceInstance> {
        if (dao != null) {
            val dbRows = dao.getAllSnapshot()
            if (dbRows.isNotEmpty()) {
                return dbRows
            }
            val legacyRows = loadRegistryFromDiskLocked()
            if (legacyRows.isNotEmpty()) {
                dao.replaceAllAtomically(legacyRows)
                registryFile.delete()
                return dao.getAllSnapshot()
            }
            return emptyList()
        }
        return loadRegistryFromDiskLocked()
    }

    private suspend fun persistAllLocked(list: List<WorkspaceInstance>) {
        if (dao != null) {
            dao.replaceAllAtomically(list)
        } else {
            persistRegistryJsonLocked(list)
        }
    }

    private fun publishStateLocked(list: List<WorkspaceInstance>) {
        _instances.value = list
        _activeInstance.value = list.firstOrNull { it.isActive }
    }

    private fun normalizeActiveInvariant(list: List<WorkspaceInstance>): List<WorkspaceInstance> {
        if (list.isEmpty()) return emptyList()
        val firstActiveIndex = list.indexOfFirst { it.isActive }
        val chosenActiveIndex = if (firstActiveIndex >= 0) firstActiveIndex else 0
        return list.mapIndexed { index, item ->
            val expectedActive = index == chosenActiveIndex
            if (item.isActive == expectedActive) item else item.copy(isActive = expectedActive)
        }
    }

    private fun loadRegistryFromDiskLocked(): List<WorkspaceInstance> {
        if (!registryFile.exists()) return emptyList()
        val raw = registryFile.readText(StandardCharsets.UTF_8).trim()
        if (raw.isEmpty()) return emptyList()
        return parseInstancesJson(raw)
    }

    private fun persistRegistryJsonLocked(list: List<WorkspaceInstance>) {
        val parent = registryFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("Unable to create registry directory: ${parent.absolutePath}")
        }
        val payload = serializeInstancesJson(list)
        val tempFile = File(registryFile.parentFile, "${registryFile.name}.tmp")
        tempFile.writeText(payload, StandardCharsets.UTF_8)
        try {
            Files.move(
                tempFile.toPath(),
                registryFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Throwable) {
            if (!tempFile.renameTo(registryFile)) {
                tempFile.copyTo(registryFile, overwrite = true)
                tempFile.delete()
            }
        }
    }

    internal companion object {
        fun serializeInstancesJson(instances: List<WorkspaceInstance>): String =
            WorkspaceRegistryJson.serialize(instances)

        fun parseInstancesJson(raw: String): List<WorkspaceInstance> =
            WorkspaceRegistryJson.parse(raw)
    }
}
