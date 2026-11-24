package com.example.data.repository

import androidx.compose.ui.graphics.ImageBitmap
import com.example.data.hardware.DeviceAppScanner
import com.example.data.hardware.DeviceBridgeManager
import com.example.data.local.OctoDao
import com.example.data.model.DeviceBridgeSnapshot
import com.example.data.model.InstalledAppInfo
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import kotlinx.coroutines.flow.Flow

class OctoRepository(
    private val dao: OctoDao,
    private val scanner: DeviceAppScanner,
    private val bridgeManager: DeviceBridgeManager
) {
    val spaces: Flow<List<VirtualSpaceEntity>> = dao.observeSpaces()
    val allVirtualApps: Flow<List<VirtualAppEntity>> = dao.observeAllApps()

    fun observeAppsForSpace(spaceId: Long): Flow<List<VirtualAppEntity>> =
        dao.observeAppsForSpace(spaceId)

    suspend fun ensureDefaultSpaces() {
        if (dao.getSpaceCount() == 0) {
            dao.insertSpace(
                VirtualSpaceEntity(
                    name = "Cipher Primary",
                    tag = "MAIN",
                    accentIndex = 0,
                    deviceModelPreset = "ROG Phone 8 Pro",
                    isolatedStorage = true,
                    frameRateHud = true,
                    permissionBridgeEnabled = true,
                    virtualDpi = 440
                )
            )
            dao.insertSpace(
                VirtualSpaceEntity(
                    name = "Dual Account",
                    tag = "DUAL",
                    accentIndex = 1,
                    deviceModelPreset = "Pixel 9 Pro",
                    isolatedStorage = true,
                    frameRateHud = false,
                    permissionBridgeEnabled = true,
                    virtualDpi = 420
                )
            )
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

    suspend fun deleteSpace(spaceId: Long) = dao.deleteSpace(spaceId)

    suspend fun scanDeviceApps(): List<InstalledAppInfo> = scanner.scanInstalledLauncherApps()

    fun getAppIcon(packageName: String): ImageBitmap? = scanner.getAppIcon(packageName)

    fun launchPackage(packageName: String): Boolean = scanner.launchInstalledApp(packageName)

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
                performanceMode = source.performanceMode
            )
        )
    }

    suspend fun updateVirtualApp(app: VirtualAppEntity) = dao.updateApp(app)

    suspend fun recordLaunch(appId: Long) = dao.recordAppLaunch(appId, System.currentTimeMillis())

    suspend fun removeVirtualApp(appId: Long) = dao.deleteApp(appId)

    suspend fun inspectDeviceBridge(): DeviceBridgeSnapshot = bridgeManager.inspectDeviceBridge()

    fun getMissingRuntimePermissions(): Array<String> = bridgeManager.getMissingRuntimePermissions()

    fun persistStorageTreeUri(uri: android.net.Uri) = bridgeManager.persistStorageTreeUri(uri)
}
