package com.cipher.virtual.data.repository

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import com.cipher.virtual.data.hardware.DeviceAppScanner
import com.cipher.virtual.data.hardware.DeviceBridgeManager
import com.cipher.virtual.data.hardware.DeviceProfile
import com.cipher.virtual.data.hardware.LaunchTargetMode
import com.cipher.virtual.data.local.InstanceWorkspaceStats
import com.cipher.virtual.data.local.OctoDao
import com.cipher.virtual.data.local.WorkspaceManager
import com.cipher.virtual.data.local.WorkspaceProcessRouter
import com.cipher.virtual.data.local.WorkspaceStorageManager
import com.cipher.virtual.data.model.DeviceBridgeSnapshot
import com.cipher.virtual.data.model.InstalledAppInfo
import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.data.model.VirtualSpaceEntity
import com.cipher.virtual.virtualization.VirtualAppExecutionEngine
import com.cipher.virtual.virtualization.VirtualLaunchResult
import kotlinx.coroutines.flow.Flow

class OctoRepository(
    private val dao: OctoDao,
    private val scanner: DeviceAppScanner,
    private val bridgeManager: DeviceBridgeManager,
    private val workspaceStorage: WorkspaceStorageManager? = null,
    private val workspaceManager: WorkspaceManager? = null,
    private val workspaceRouter: WorkspaceProcessRouter? = null,
    private val virtualExecutionEngine: VirtualAppExecutionEngine? = null
) {
    val spaces: Flow<List<VirtualSpaceEntity>> = dao.observeSpaces()
    val allVirtualApps: Flow<List<VirtualAppEntity>> = dao.observeAllApps()

    fun observeAppsForSpace(spaceId: Long): Flow<List<VirtualAppEntity>> =
        dao.observeAppsForSpace(spaceId)

    suspend fun ensureDefaultSpaces() {
        workspaceManager?.initialize()
        val existingSpaces = dao.getAllSpacesSnapshot()
        if (existingSpaces.isEmpty()) {
            dao.insertSpace(
                VirtualSpaceEntity(
                    name = "Cipher Workspace",
                    tag = "MAIN",
                    accentIndex = 0,
                    deviceModelPreset = "ROG Phone 8 Pro",
                    isolatedStorage = true,
                    frameRateHud = true,
                    permissionBridgeEnabled = true,
                    virtualDpi = 440
                )
            )
        } else if (existingSpaces.size > 1) {
            val primary = existingSpaces.first()
            existingSpaces.drop(1).forEach { extra ->
                dao.moveAppsToSpace(extra.id, primary.id)
                dao.deleteSpace(extra.id)
                workspaceStorage?.deleteSpaceWorkspaces(extra.id)
            }
        }

        workspaceRouter?.hydrateFromManager()

        val allApps = dao.getAllAppsSnapshot().sortedBy { it.addedAt }
        val activeInstanceIds = HashSet<String>(allApps.size)
        allApps.forEachIndexed { index, app ->
            val instanceId = multiInstanceId(app.spaceId, app.id)
            activeInstanceIds.add(instanceId)
            val existingInstance = workspaceManager?.getInstance(instanceId)
            if (existingInstance == null) {
                registerManagedInstance(
                    spaceId = app.spaceId,
                    appId = app.id,
                    label = "${app.displayName} (${app.instanceLabel})",
                    packageName = app.packageName,
                    activate = index == 0 && workspaceManager?.getActiveInstance() == null
                )
            } else {
                val assignment = workspaceRouter?.assignAndPersistWorkerSlot(instanceId)?.getOrNull()
                val slot = assignment?.workerProcessName
                    ?: workspaceManager.getInstance(instanceId)?.workerSlot
                    ?: app.workerSlot
                val uid = computeVirtualUid(instanceId)
                if (app.workerSlot != slot || app.virtualUid != uid) {
                    dao.updateAppWorkerRouting(app.id, slot, uid)
                }
            }
        }

        // Remove orphaned Room-managed app instance entries that were deleted externally.
        workspaceManager?.listInstances()
            ?.filter { it.instanceId.startsWith("space_") && it.instanceId.contains("_app_") }
            ?.filter { it.instanceId !in activeInstanceIds }
            ?.forEach { stale ->
                workspaceRouter?.releaseWorkerSlot(stale.instanceId)
                workspaceManager.removeInstance(stale.instanceId)
            }

        workspaceRouter?.pruneUnregisteredSlots()
    }

    suspend fun updateSpace(space: VirtualSpaceEntity) = dao.updateSpace(space)

    suspend fun scanDeviceApps(): List<InstalledAppInfo> = scanner.scanInstalledLauncherApps()

    suspend fun loadAppIcons(packageNames: List<String>): Map<String, ImageBitmap> =
        scanner.loadAppIcons(packageNames)

    fun getAppIcon(packageName: String): ImageBitmap? = scanner.getAppIcon(packageName)

    fun hasWorkProfileTarget(packageName: String): Boolean =
        scanner.hasWorkProfileTarget(packageName)

    fun launchPackage(
        packageName: String,
        preferWorkProfile: Boolean = false
    ): LaunchTargetMode? = scanner.launchInstalledApp(packageName, preferWorkProfile)

    suspend fun addAppToSpace(
        spaceId: Long,
        installedApp: InstalledAppInfo
    ): Long {
        val existingCount = dao.countPackageInSpace(spaceId, installedApp.packageName)
        val instanceLabel = "Instance #${existingCount + 1}"
        val insertedId = dao.insertApp(
            VirtualAppEntity(
                spaceId = spaceId,
                packageName = installedApp.packageName,
                displayName = installedApp.label,
                instanceLabel = instanceLabel,
                performanceMode = if (installedApp.isGame) "Turbo" else "Balanced"
            )
        )
        registerManagedInstance(
            spaceId = spaceId,
            appId = insertedId,
            label = "${installedApp.label} ($instanceLabel)",
            packageName = installedApp.packageName,
            activate = true
        )
        return insertedId
    }

    suspend fun duplicateVirtualApp(source: VirtualAppEntity): Long {
        val existingCount = dao.countPackageInSpace(source.spaceId, source.packageName)
        val instanceLabel = "Instance #${existingCount + 1}"
        val insertedId = dao.insertApp(
            VirtualAppEntity(
                spaceId = source.spaceId,
                packageName = source.packageName,
                displayName = source.displayName,
                instanceLabel = instanceLabel,
                performanceMode = source.performanceMode,
                notes = source.notes,
                lastProfileId = source.lastProfileId
            )
        )
        registerManagedInstance(
            spaceId = source.spaceId,
            appId = insertedId,
            label = "${source.displayName} ($instanceLabel)",
            packageName = source.packageName,
            activate = true
        )
        return insertedId
    }

    suspend fun updateVirtualApp(app: VirtualAppEntity) = dao.updateApp(app)

    suspend fun recordLaunch(
        app: VirtualAppEntity,
        space: VirtualSpaceEntity?,
        profile: DeviceProfile?
    ) {
        dao.recordAppLaunch(app.id, System.currentTimeMillis(), profile?.profileId)
        dao.switchActiveAppAtomically(app.id)
        val instanceId = multiInstanceId(app.spaceId, app.id)
        if (workspaceManager?.getInstance(instanceId) == null) {
            registerManagedInstance(
                spaceId = app.spaceId,
                appId = app.id,
                label = "${app.displayName} (${app.instanceLabel})",
                packageName = app.packageName,
                activate = true
            )
        } else {
            workspaceManager.switchActiveInstance(instanceId)
            val assignment = workspaceRouter?.assignAndPersistWorkerSlot(instanceId)?.getOrNull()
            val slot = assignment?.workerProcessName
                ?: workspaceManager.getInstance(instanceId)?.workerSlot
                ?: app.workerSlot
            dao.updateAppWorkerRouting(app.id, slot, computeVirtualUid(instanceId))
        }
        if (space != null && space.isolatedStorage) {
            workspaceStorage?.recordSessionMetadata(space, app, profile)
        }
    }

    suspend fun removeVirtualApp(app: VirtualAppEntity) {
        dao.deleteApp(app.id)
        val instanceId = multiInstanceId(app.spaceId, app.id)
        workspaceRouter?.releaseWorkerSlot(instanceId)
        workspaceManager?.removeInstance(instanceId)
        workspaceStorage?.resetInstanceWorkspace(app.spaceId, app.id)
    }

    suspend fun inspectInstanceWorkspace(spaceId: Long, appId: Long): InstanceWorkspaceStats? {
        val storage = workspaceStorage ?: return null
        val instanceId = multiInstanceId(spaceId, appId)
        val multiStats = storage.inspectInstanceStorage(instanceId).getOrNull()
        val legacyStats = storage.inspectInstanceWorkspace(spaceId, appId)
        val combined = if (multiStats != null && (storage.instanceExists(instanceId) || legacyStats.fileCount == 0)) {
            InstanceWorkspaceStats(
                directoryPath = multiStats.directoryPath,
                sizeBytes = multiStats.sizeBytes + legacyStats.sizeBytes,
                fileCount = multiStats.fileCount + legacyStats.fileCount,
                lastSessionRecordedAt = listOfNotNull(
                    multiStats.lastSessionRecordedAt,
                    legacyStats.lastSessionRecordedAt
                ).maxOrNull()
            )
        } else {
            legacyStats
        }
        dao.updateAppStorageMetrics(appId, combined.sizeBytes, combined.fileCount)
        return combined
    }

    suspend fun resetInstanceWorkspace(spaceId: Long, appId: Long): Boolean {
        val instanceId = multiInstanceId(spaceId, appId)
        val managerCleared = workspaceManager?.clearInstanceData(instanceId)?.isSuccess
        val storageCleared = managerCleared ?: workspaceStorage?.resetInstanceStorage(instanceId)?.isSuccess ?: false
        val legacyCleared = workspaceStorage?.resetInstanceWorkspace(spaceId, appId) ?: false
        dao.updateAppStorageMetrics(appId, 0L, 0)
        return storageCleared || legacyCleared
    }

    suspend fun launchVirtualSandbox(
        app: VirtualAppEntity,
        preferRoot: Boolean = false,
        darkTheme: Boolean = true
    ): VirtualLaunchResult? {
        val engine = virtualExecutionEngine ?: return null
        val instanceId = multiInstanceId(app.spaceId, app.id)
        val workerSlot = getWorkerSlotForInstance(app.spaceId, app.id) ?: app.workerSlot
        val virtualUid = computeVirtualUid(instanceId)
        val updatedApp = app.copy(
            workerSlot = workerSlot,
            virtualUid = virtualUid,
            isActive = true
        )
        dao.switchActiveAppAtomically(app.id)
        dao.updateAppWorkerRouting(app.id, workerSlot, virtualUid)
        return engine.launchInstance(updatedApp, instanceId, preferRoot, darkTheme)
    }

    fun getWorkerSlotForInstance(spaceId: Long, appId: Long): String? {
        val instanceId = multiInstanceId(spaceId, appId)
        return workspaceRouter?.getAssignedWorkerSlot(instanceId)
            ?: workspaceManager?.getInstance(instanceId)?.workerSlot
    }

    suspend fun inspectDeviceBridge(): DeviceBridgeSnapshot = bridgeManager.inspectDeviceBridge()

    fun getMissingRuntimePermissions(): Array<String> = bridgeManager.getMissingRuntimePermissions()

    fun persistStorageTreeUri(uri: Uri) = bridgeManager.persistStorageTreeUri(uri)

    private suspend fun registerManagedInstance(
        spaceId: Long,
        appId: Long,
        label: String,
        packageName: String,
        activate: Boolean
    ) {
        val instanceId = multiInstanceId(spaceId, appId)
        if (workspaceManager != null) {
            workspaceManager.createInstance(
                label = label,
                targetPackageName = packageName,
                instanceId = instanceId,
                activate = activate
            )
        } else {
            workspaceStorage?.initializeInstanceStorage(instanceId)
        }
        val assignment = workspaceRouter?.assignAndPersistWorkerSlot(instanceId)?.getOrNull()
        val workerSlot = assignment?.workerProcessName
            ?: workspaceRouter?.getAssignedWorkerSlot(instanceId)
            ?: ":w0"
        val virtualUid = computeVirtualUid(instanceId)
        dao.updateAppWorkerRouting(appId, workerSlot, virtualUid)
        if (activate) {
            dao.switchActiveAppAtomically(appId)
        }
    }

    companion object {
        fun multiInstanceId(spaceId: Long, appId: Long): String = "space_${spaceId}_app_${appId}"

        fun computeVirtualUid(instanceId: String): Int =
            10000 + (instanceId.hashCode() and 0x7FFF) % 9000
    }
}
