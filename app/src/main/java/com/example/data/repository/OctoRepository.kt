package com.example.data.repository

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import com.example.data.hardware.DeviceAppScanner
import com.example.data.hardware.DeviceBridgeManager
import com.example.data.hardware.DeviceProfile
import com.example.data.hardware.LaunchTargetMode
import com.example.data.local.InstanceWorkspaceStats
import com.example.data.local.OctoDao
import com.example.data.local.WorkspaceManager
import com.example.data.local.WorkspaceProcessRouter
import com.example.data.local.WorkspaceStorageManager
import com.example.data.model.DeviceBridgeSnapshot
import com.example.data.model.InstalledAppInfo
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import kotlinx.coroutines.flow.Flow

class OctoRepository(
    private val dao: OctoDao,
    private val scanner: DeviceAppScanner,
    private val bridgeManager: DeviceBridgeManager,
    private val workspaceStorage: WorkspaceStorageManager? = null,
    private val workspaceManager: WorkspaceManager? = null,
    private val workspaceRouter: WorkspaceProcessRouter? = null
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
                workspaceRouter?.assignAndPersistWorkerSlot(instanceId)
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
            workspaceRouter?.assignAndPersistWorkerSlot(instanceId)
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
        return if (multiStats != null && (storage.instanceExists(instanceId) || legacyStats.fileCount == 0)) {
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
    }

    suspend fun resetInstanceWorkspace(spaceId: Long, appId: Long): Boolean {
        val instanceId = multiInstanceId(spaceId, appId)
        val managerCleared = workspaceManager?.clearInstanceData(instanceId)?.isSuccess
        val storageCleared = managerCleared ?: workspaceStorage?.resetInstanceStorage(instanceId)?.isSuccess ?: false
        val legacyCleared = workspaceStorage?.resetInstanceWorkspace(spaceId, appId) ?: false
        return storageCleared || legacyCleared
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
        workspaceRouter?.assignAndPersistWorkerSlot(instanceId)
    }

    companion object {
        fun multiInstanceId(spaceId: Long, appId: Long): String = "space_${spaceId}_app_${appId}"
    }
}
