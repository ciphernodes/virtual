package com.example.data.repository

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import com.example.data.hardware.DeviceAppScanner
import com.example.data.hardware.DeviceBridgeManager
import com.example.data.hardware.DeviceProfile
import com.example.data.hardware.LaunchTargetMode
import com.example.data.local.InstanceWorkspaceStats
import com.example.data.local.OctoDao
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
    private val workspaceStorage: WorkspaceStorageManager? = null
) {
    val spaces: Flow<List<VirtualSpaceEntity>> = dao.observeSpaces()
    val allVirtualApps: Flow<List<VirtualAppEntity>> = dao.observeAllApps()

    fun observeAppsForSpace(spaceId: Long): Flow<List<VirtualAppEntity>> =
        dao.observeAppsForSpace(spaceId)

    suspend fun ensureDefaultSpaces() {
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
    }

    suspend fun createSpace(name: String, tag: String, accentIndex: Int): Long {
        return dao.insertSpace(
            VirtualSpaceEntity(
                name = name.trim(),
                tag = tag.trim().uppercase().take(6).ifEmpty { "SPACE" },
                accentIndex = accentIndex
            )
        )
    }

    suspend fun updateSpace(space: VirtualSpaceEntity) = dao.updateSpace(space)

    suspend fun deleteSpace(spaceId: Long) {
        dao.deleteSpace(spaceId)
        workspaceStorage?.deleteSpaceWorkspaces(spaceId)
    }

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
        return dao.insertApp(
            VirtualAppEntity(
                spaceId = spaceId,
                packageName = installedApp.packageName,
                displayName = installedApp.label,
                instanceLabel = instanceLabel,
                performanceMode = if (installedApp.isGame) "Turbo" else "Balanced"
            )
        )
    }

    suspend fun duplicateVirtualApp(source: VirtualAppEntity): Long {
        val existingCount = dao.countPackageInSpace(source.spaceId, source.packageName)
        return dao.insertApp(
            VirtualAppEntity(
                spaceId = source.spaceId,
                packageName = source.packageName,
                displayName = source.displayName,
                instanceLabel = "Instance #${existingCount + 1}",
                performanceMode = source.performanceMode,
                notes = source.notes,
                lastProfileId = source.lastProfileId
            )
        )
    }

    suspend fun updateVirtualApp(app: VirtualAppEntity) = dao.updateApp(app)

    suspend fun recordLaunch(
        app: VirtualAppEntity,
        space: VirtualSpaceEntity?,
        profile: DeviceProfile?
    ) {
        dao.recordAppLaunch(app.id, System.currentTimeMillis(), profile?.profileId)
        if (space != null && space.isolatedStorage) {
            workspaceStorage?.recordSessionMetadata(space, app, profile)
        }
    }

    suspend fun removeVirtualApp(app: VirtualAppEntity) {
        dao.deleteApp(app.id)
        workspaceStorage?.resetInstanceWorkspace(app.spaceId, app.id)
    }

    suspend fun inspectInstanceWorkspace(spaceId: Long, appId: Long): InstanceWorkspaceStats? =
        workspaceStorage?.inspectInstanceWorkspace(spaceId, appId)

    suspend fun resetInstanceWorkspace(spaceId: Long, appId: Long): Boolean =
        workspaceStorage?.resetInstanceWorkspace(spaceId, appId) ?: false

    suspend fun inspectDeviceBridge(): DeviceBridgeSnapshot = bridgeManager.inspectDeviceBridge()

    fun getMissingRuntimePermissions(): Array<String> = bridgeManager.getMissingRuntimePermissions()

    fun persistStorageTreeUri(uri: Uri) = bridgeManager.persistStorageTreeUri(uri)
}
