package com.cipher.virtual.ui

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cipher.virtual.data.hardware.DeviceProfile
import com.cipher.virtual.data.hardware.DeviceProfileManager
import com.cipher.virtual.data.local.InstanceWorkspaceStats
import com.cipher.virtual.data.local.PreferencesStore
import com.cipher.virtual.data.model.DeviceBridgeSnapshot
import com.cipher.virtual.data.model.InstalledAppInfo
import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.data.model.VirtualSpaceEntity
import com.cipher.virtual.data.repository.OctoRepository
import com.cipher.virtual.monitoring.ResourceMonitorSampler
import com.cipher.virtual.monitoring.ResourceTelemetrySnapshot
import com.cipher.virtual.security.IntegrityManager
import com.cipher.virtual.security.IntegrityStatusSnapshot

data class OctoUiState(
    val spaces: List<VirtualSpaceEntity> = emptyList(),
    val selectedSpaceId: Long? = null,
    val appsInSpace: List<VirtualAppEntity> = emptyList(),
    val allVirtualApps: List<VirtualAppEntity> = emptyList(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isScanningApps: Boolean = false,
    val appIcons: Map<String, ImageBitmap> = emptyMap(),
    val deviceBridge: DeviceBridgeSnapshot = DeviceBridgeSnapshot(),
    val attachedVaultUris: List<Uri> = emptyList(),
    val launchSessionApp: VirtualAppEntity? = null,
    val darkThemeEnabled: Boolean = true,
    val statusMessage: String? = null,
    val integrityStatus: IntegrityStatusSnapshot = IntegrityStatusSnapshot(),
    val resourceTelemetry: ResourceTelemetrySnapshot = ResourceTelemetrySnapshot(),
    val activeDeviceProfile: DeviceProfile? = null,
    val availableDeviceProfiles: List<DeviceProfile> = DeviceProfileManager.availablePresets(),
    val inspectedWorkspaceStats: InstanceWorkspaceStats? = null,
    val inspectedWorkerSlot: String? = null,
    val isTelemetryPausedInBackground: Boolean = false,
    val isNativeEngineAvailable: Boolean = false,
    val nativeEngineLoadWarning: String? = null
) {
    val activeSpace: VirtualSpaceEntity?
        get() = spaces.firstOrNull { it.id == selectedSpaceId } ?: spaces.firstOrNull()
}

internal data class CoreData(
    val spaces: List<VirtualSpaceEntity>,
    val selectedSpaceId: Long?,
    val appsInSpace: List<VirtualAppEntity>,
    val allApps: List<VirtualAppEntity>
)

internal data class ScannerData(
    val installedApps: List<InstalledAppInfo>,
    val isScanning: Boolean,
    val appIcons: Map<String, ImageBitmap>
)

internal data class BridgeData(
    val deviceBridge: DeviceBridgeSnapshot,
    val attachedVaultUris: List<Uri>,
    val inspectedWorkspaceStats: InstanceWorkspaceStats?,
    val inspectedWorkerSlot: String?,
    val isForegroundActive: Boolean
)

internal data class UiFlagsData(
    val launchSessionApp: VirtualAppEntity?,
    val darkThemeEnabled: Boolean,
    val statusMessage: String?,
    val integrityStatus: IntegrityStatusSnapshot,
    val resourceTelemetry: ResourceTelemetrySnapshot
)

class OctoViewModelFactory(
    private val repository: OctoRepository,
    private val integrityManager: IntegrityManager,
    private val resourceSampler: ResourceMonitorSampler,
    private val preferencesStore: PreferencesStore,
    private val deviceProfileManager: DeviceProfileManager,
    private val stringLookup: (Int, Array<out Any>) -> String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OctoViewModel::class.java)) {
            return OctoViewModel(
                repository = repository,
                integrityManager = integrityManager,
                resourceSampler = resourceSampler,
                preferencesStore = preferencesStore,
                deviceProfileManager = deviceProfileManager,
                stringLookup = stringLookup
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
