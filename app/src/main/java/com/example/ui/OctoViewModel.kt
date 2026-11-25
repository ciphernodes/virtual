package com.example.ui

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ciphernodes.ciphervirtual.monitoring.ResourceMonitorSampler
import com.ciphernodes.ciphervirtual.monitoring.ResourceMonitorService
import com.ciphernodes.ciphervirtual.monitoring.ResourceTelemetrySnapshot
import com.ciphernodes.ciphervirtual.security.IntegrityCheckService
import com.ciphernodes.ciphervirtual.security.IntegrityManager
import com.ciphernodes.ciphervirtual.security.IntegrityStatusSnapshot
import com.example.R
import com.example.data.hardware.DeviceProfile
import com.example.data.hardware.DeviceProfileManager
import com.example.data.hardware.LaunchTargetMode
import com.example.data.local.InstanceWorkspaceStats
import com.example.data.local.PreferencesStore
import com.example.data.model.DeviceBridgeSnapshot
import com.example.data.model.InstalledAppInfo
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import com.example.data.repository.OctoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val isTelemetryPausedInBackground: Boolean = false
) {
    val activeSpace: VirtualSpaceEntity?
        get() = spaces.firstOrNull { it.id == selectedSpaceId } ?: spaces.firstOrNull()
}

@OptIn(ExperimentalCoroutinesApi::class)
class OctoViewModel(
    private val repository: OctoRepository,
    private val integrityManager: IntegrityManager,
    private val resourceSampler: ResourceMonitorSampler,
    private val preferencesStore: PreferencesStore? = null,
    private val deviceProfileManager: DeviceProfileManager? = null,
    private val stringLookup: ((Int, Array<out Any>) -> String)? = null
) : ViewModel() {

    private val selectedSpaceIdFlow = MutableStateFlow<Long?>(null)
    private val installedAppsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val isScanningAppsFlow = MutableStateFlow(false)
    private val appIconsFlow = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private val deviceBridgeFlow = MutableStateFlow(DeviceBridgeSnapshot())
    private val attachedVaultUrisFlow = MutableStateFlow<List<Uri>>(emptyList())
    private val launchSessionAppFlow = MutableStateFlow<VirtualAppEntity?>(null)
    private val darkThemeFlow = MutableStateFlow(true)
    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private val periodicIntegrityEnabledFlow = MutableStateFlow(true)
    private val integrityStatusFlow = MutableStateFlow(integrityManager.status.value)
    private val resourceTelemetryFlow = MutableStateFlow(ResourceTelemetrySnapshot())
    private val activeDeviceProfileFlow = MutableStateFlow(DeviceProfileManager.rogPhone8Pro())
    private val availableDeviceProfilesFlow = MutableStateFlow(
        deviceProfileManager?.getAvailableProfiles() ?: DeviceProfileManager.availablePresets()
    )
    private val inspectedWorkspaceStatsFlow = MutableStateFlow<InstanceWorkspaceStats?>(null)
    private val isForegroundActiveFlow = MutableStateFlow(true)

    private var boundIntegrityService: IntegrityCheckService? = null
    private var boundResourceService: ResourceMonitorService? = null
    private var integrityCollectorJob: Job? = null
    private var resourceCollectorJob: Job? = null
    private var fallbackTelemetryJob: Job? = null

    private val appsInSpaceFlow = combine(
        repository.spaces,
        selectedSpaceIdFlow
    ) { spaces, selectedId ->
        selectedId?.takeIf { id -> spaces.any { it.id == id } } ?: spaces.firstOrNull()?.id
    }.flatMapLatest { resolvedSpaceId ->
        if (resolvedSpaceId == null) flowOf(emptyList())
        else repository.observeAppsForSpace(resolvedSpaceId)
    }

    private val coreDataFlow = combine(
        repository.spaces,
        selectedSpaceIdFlow,
        appsInSpaceFlow,
        repository.allVirtualApps
    ) { spaces, selectedSpaceId, appsInSpace, allApps ->
        val validSelection = selectedSpaceId?.takeIf { id -> spaces.any { it.id == id } }
            ?: spaces.firstOrNull()?.id
        CoreData(
            spaces = spaces,
            selectedSpaceId = validSelection,
            appsInSpace = appsInSpace,
            allApps = allApps
        )
    }

    private val scannerDataFlow = combine(
        installedAppsFlow,
        isScanningAppsFlow,
        appIconsFlow
    ) { installed, isScanning, icons ->
        ScannerData(
            installedApps = installed,
            isScanning = isScanning,
            appIcons = icons
        )
    }

    private val bridgeDataFlow = combine(
        deviceBridgeFlow,
        attachedVaultUrisFlow,
        inspectedWorkspaceStatsFlow,
        isForegroundActiveFlow
    ) { bridge, uris, stats, foreground ->
        BridgeData(
            deviceBridge = bridge,
            attachedVaultUris = uris,
            inspectedWorkspaceStats = stats,
            isForegroundActive = foreground
        )
    }

    private val uiFlagsFlow = combine(
        launchSessionAppFlow,
        darkThemeFlow,
        statusMessageFlow,
        integrityStatusFlow,
        resourceTelemetryFlow
    ) { launchApp, darkTheme, statusMsg, integrity, telemetry ->
        UiFlagsData(
            launchSessionApp = launchApp,
            darkThemeEnabled = darkTheme,
            statusMessage = statusMsg,
            integrityStatus = integrity,
            resourceTelemetry = telemetry
        )
    }

    val uiState: StateFlow<OctoUiState> = combine(
        coreDataFlow,
        scannerDataFlow,
        bridgeDataFlow,
        uiFlagsFlow,
        combine(activeDeviceProfileFlow, availableDeviceProfilesFlow) { active, all -> active to all }
    ) { core, scanner, bridge, flags, (activeProfile, allProfiles) ->
        OctoUiState(
            spaces = core.spaces,
            selectedSpaceId = core.selectedSpaceId,
            appsInSpace = core.appsInSpace,
            allVirtualApps = core.allApps,
            installedApps = scanner.installedApps,
            isScanningApps = scanner.isScanning,
            appIcons = scanner.appIcons,
            deviceBridge = bridge.deviceBridge,
            attachedVaultUris = bridge.attachedVaultUris,
            launchSessionApp = flags.launchSessionApp,
            darkThemeEnabled = flags.darkThemeEnabled,
            statusMessage = flags.statusMessage,
            integrityStatus = flags.integrityStatus,
            resourceTelemetry = flags.resourceTelemetry,
            activeDeviceProfile = activeProfile,
            availableDeviceProfiles = allProfiles,
            inspectedWorkspaceStats = bridge.inspectedWorkspaceStats,
            isTelemetryPausedInBackground = !bridge.isForegroundActive
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OctoUiState(darkThemeEnabled = darkThemeFlow.value)
    )

    init {
        preferencesStore?.let { store ->
            viewModelScope.launch {
                store.darkThemeFlow.distinctUntilChanged().collect { darkThemeFlow.value = it }
            }
            viewModelScope.launch {
                store.selectedSpaceIdFlow.distinctUntilChanged().collect { savedId ->
                    if (savedId != null && selectedSpaceIdFlow.value == null) {
                        selectedSpaceIdFlow.value = savedId
                        activeDeviceProfileFlow.value =
                            deviceProfileManager?.getActiveProfile(savedId) ?: DeviceProfileManager.rogPhone8Pro()
                    }
                }
            }
            viewModelScope.launch {
                store.periodicIntegrityFlow.distinctUntilChanged().collect { enabled ->
                    periodicIntegrityEnabledFlow.value = enabled
                    integrityStatusFlow.update { it.copy(isPeriodicEnabled = enabled) }
                }
            }
            viewModelScope.launch {
                store.attachedVaultUrisFlow.collect { attachedVaultUrisFlow.value = it }
            }
        }

        viewModelScope.launch {
            integrityManager.status.collect { snapshot ->
                if (boundIntegrityService == null) {
                    integrityStatusFlow.value = snapshot.copy(
                        isPeriodicEnabled = periodicIntegrityEnabledFlow.value
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.ensureDefaultSpaces()
            refreshInstalledApps()
            refreshDeviceBridge()
            refreshResourceTelemetry()
        }

        viewModelScope.launch {
            combine(repository.spaces, repository.allVirtualApps) { spaces, apps ->
                spaces to apps
            }.collect { (spaces, apps) ->
                val active = selectedSpaceIdFlow.value?.let { id -> spaces.firstOrNull { it.id == id } }
                    ?: spaces.firstOrNull()
                if (active != null && deviceProfileManager != null) {
                    activeDeviceProfileFlow.value =
                        deviceProfileManager.resolveForPresetName(active.deviceModelPreset, active.id)
                }
                val boundRes = boundResourceService
                if (boundRes != null) {
                    boundRes.updateTrackedContainers(spaces, apps)
                } else {
                    resourceTelemetryFlow.value = resourceSampler.captureSnapshot(spaces, apps)
                }
            }
        }

        viewModelScope.launch {
            repository.allVirtualApps.collect { apps ->
                val missing = apps.map { it.packageName }.distinct()
                    .filter { !appIconsFlow.value.containsKey(it) }
                if (missing.isNotEmpty()) {
                    val newIcons = repository.loadAppIcons(missing)
                    if (newIcons.isNotEmpty()) {
                        appIconsFlow.update { it + newIcons }
                    }
                }
            }
        }

        restartFallbackPollingIfNeeded()
    }

    fun setForegroundActive(active: Boolean) {
        isForegroundActiveFlow.value = active
        val resourceService = boundResourceService
        if (resourceService != null) {
            if (active) {
                resourceService.startMonitoring()
            } else {
                resourceService.stopMonitoring()
            }
        } else {
            restartFallbackPollingIfNeeded()
        }
    }

    private fun restartFallbackPollingIfNeeded() {
        fallbackTelemetryJob?.cancel()
        if (boundResourceService != null || !isForegroundActiveFlow.value) {
            fallbackTelemetryJob = null
            return
        }
        fallbackTelemetryJob = viewModelScope.launch {
            while (isActive) {
                delay(3000L)
                if (boundResourceService == null && isForegroundActiveFlow.value) {
                    refreshResourceTelemetry()
                }
            }
        }
    }

    fun selectSpace(spaceId: Long) {
        selectedSpaceIdFlow.value = spaceId
        viewModelScope.launch { preferencesStore?.setSelectedSpaceId(spaceId) }
        val space = uiState.value.spaces.firstOrNull { it.id == spaceId }
        if (space != null && deviceProfileManager != null) {
            activeDeviceProfileFlow.value =
                deviceProfileManager.resolveForPresetName(space.deviceModelPreset, space.id)
        }
    }

    fun createSpace(name: String, tag: String, accentIndex: Int) {
        if (name.isBlank()) return
        val trimmed = name.trim()
        viewModelScope.launch {
            val newId = repository.createSpace(trimmed, tag, accentIndex)
            val defaultPreset = DeviceProfileManager.rogPhone8Pro()
            deviceProfileManager?.selectPreset(defaultPreset.presetKey, newId)?.let {
                activeDeviceProfileFlow.value = it
            }
            selectedSpaceIdFlow.value = newId
            preferencesStore?.setSelectedSpaceId(newId)
            statusMessageFlow.value = formatText(
                R.string.status_space_created,
                "Space \"$trimmed\" initialized",
                trimmed
            )
        }
    }

    fun editSpace(space: VirtualSpaceEntity, name: String, tag: String, accentIndex: Int) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val normalizedTag = tag.trim().uppercase().take(6).ifEmpty { "SPACE" }
        viewModelScope.launch {
            repository.updateSpace(
                space.copy(
                    name = trimmedName,
                    tag = normalizedTag,
                    accentIndex = accentIndex
                )
            )
            statusMessageFlow.value = formatText(
                R.string.status_space_updated,
                "Updated space \"$trimmedName\"",
                trimmedName
            )
        }
    }

    fun updateSpace(space: VirtualSpaceEntity) {
        viewModelScope.launch {
            repository.updateSpace(space)
        }
    }

    fun resetSpaceDefaults(space: VirtualSpaceEntity) {
        viewModelScope.launch {
            val defaultProfile = DeviceProfileManager.rogPhone8Pro()
            deviceProfileManager?.selectPreset(defaultProfile.presetKey, space.id)?.let {
                activeDeviceProfileFlow.value = it
            }
            repository.updateSpace(
                space.copy(
                    deviceModelPreset = defaultProfile.presetKey,
                    virtualDpi = defaultProfile.densityDpi,
                    isolatedStorage = true,
                    frameRateHud = true,
                    permissionBridgeEnabled = true
                )
            )
            statusMessageFlow.value = formatText(
                R.string.status_defaults_restored,
                "Restored default sandbox settings for ${space.name}",
                space.name
            )
        }
    }

    fun selectDeviceProfile(space: VirtualSpaceEntity, profile: DeviceProfile) {
        viewModelScope.launch {
            val resolved = deviceProfileManager?.selectPreset(profile.presetKey, space.id) ?: profile
            activeDeviceProfileFlow.value = resolved
            repository.updateSpace(
                space.copy(
                    deviceModelPreset = resolved.presetKey,
                    virtualDpi = resolved.densityDpi
                )
            )
        }
    }

    fun saveCustomDeviceProfile(space: VirtualSpaceEntity, profile: DeviceProfile) {
        viewModelScope.launch {
            val saved = deviceProfileManager?.saveCustomProfile(profile, space.id) ?: profile
            activeDeviceProfileFlow.value = saved
            availableDeviceProfilesFlow.value =
                deviceProfileManager?.getAvailableProfiles() ?: DeviceProfileManager.availablePresets()
            repository.updateSpace(
                space.copy(
                    deviceModelPreset = saved.presetKey,
                    virtualDpi = saved.densityDpi
                )
            )
            statusMessageFlow.value = formatText(
                R.string.status_custom_profile_saved,
                "Saved custom profile \"${saved.displayName}\"",
                saved.displayName
            )
        }
    }

    fun deleteCustomDeviceProfile(space: VirtualSpaceEntity?, profileId: String) {
        viewModelScope.launch {
            deviceProfileManager?.deleteCustomProfile(profileId, space?.id)
            availableDeviceProfilesFlow.value =
                deviceProfileManager?.getAvailableProfiles() ?: DeviceProfileManager.availablePresets()
            val fallback = deviceProfileManager?.getActiveProfile(space?.id)
                ?: DeviceProfileManager.rogPhone8Pro()
            activeDeviceProfileFlow.value = fallback
            if (space != null) {
                repository.updateSpace(
                    space.copy(
                        deviceModelPreset = fallback.presetKey,
                        virtualDpi = fallback.densityDpi
                    )
                )
            }
            statusMessageFlow.value = formatText(
                R.string.status_custom_profile_removed,
                "Removed custom hardware profile"
            )
        }
    }

    fun deleteSpace(spaceId: Long) {
        viewModelScope.launch {
            if (uiState.value.spaces.size <= 1) {
                statusMessageFlow.value = formatText(
                    R.string.status_space_min_one,
                    "At least one Virtual Space must remain active"
                )
                return@launch
            }
            repository.deleteSpace(spaceId)
            if (selectedSpaceIdFlow.value == spaceId) {
                selectedSpaceIdFlow.value = null
                preferencesStore?.setSelectedSpaceId(null)
            }
            statusMessageFlow.value = formatText(
                R.string.status_space_removed,
                "Virtual Space removed"
            )
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            isScanningAppsFlow.value = true
            val apps = repository.scanDeviceApps()
            installedAppsFlow.value = apps
            isScanningAppsFlow.value = false
        }
    }

    fun requestIconsForPackages(packageNames: List<String>) {
        val missing = packageNames.distinct().filter { !appIconsFlow.value.containsKey(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val loaded = repository.loadAppIcons(missing)
            if (loaded.isNotEmpty()) {
                appIconsFlow.update { it + loaded }
                installedAppsFlow.update { current ->
                    current.map { info ->
                        val icon = loaded[info.packageName]
                        if (icon != null && info.icon == null) info.copy(icon = icon) else info
                    }
                }
            }
        }
    }

    fun addAppToActiveSpace(installedApp: InstalledAppInfo) {
        val active = uiState.value.activeSpace ?: return
        viewModelScope.launch {
            repository.addAppToSpace(active.id, installedApp)
            statusMessageFlow.value = formatText(
                R.string.status_app_cloned,
                "Cloned ${installedApp.label} into ${active.name}",
                installedApp.label,
                active.name
            )
        }
    }

    fun duplicateVirtualApp(app: VirtualAppEntity) {
        viewModelScope.launch {
            repository.duplicateVirtualApp(app)
            statusMessageFlow.value = formatText(
                R.string.status_app_duplicated,
                "Created new instance of ${app.displayName}",
                app.displayName
            )
        }
    }

    fun removeVirtualApp(app: VirtualAppEntity) {
        viewModelScope.launch {
            repository.removeVirtualApp(app)
            statusMessageFlow.value = formatText(
                R.string.status_app_removed,
                "Removed ${app.displayName} (${app.instanceLabel})",
                app.displayName,
                app.instanceLabel
            )
        }
    }

    fun updateVirtualApp(app: VirtualAppEntity) {
        viewModelScope.launch {
            repository.updateVirtualApp(app)
            if (launchSessionAppFlow.value?.id == app.id) {
                launchSessionAppFlow.value = app
            }
        }
    }

    fun openLaunchSession(app: VirtualAppEntity) {
        launchSessionAppFlow.value = app
    }

    fun dismissLaunchSession() {
        launchSessionAppFlow.value = null
    }

    fun hasWorkProfileTarget(packageName: String): Boolean =
        repository.hasWorkProfileTarget(packageName)

    fun launchAppFromSession(app: VirtualAppEntity, preferWorkProfile: Boolean = false) {
        viewModelScope.launch {
            val space = uiState.value.spaces.firstOrNull { it.id == app.spaceId } ?: uiState.value.activeSpace
            val profile = uiState.value.activeDeviceProfile
            repository.recordLaunch(app, space, profile)
            val mode = repository.launchPackage(app.packageName, preferWorkProfile)
            launchSessionAppFlow.value = null
            if (inspectedWorkspaceStatsFlow.value != null && space != null) {
                inspectedWorkspaceStatsFlow.value = repository.inspectInstanceWorkspace(space.id, app.id)
            }
            statusMessageFlow.value = when (mode) {
                LaunchTargetMode.WORK_PROFILE ->
                    "Launched ${app.displayName} (${app.instanceLabel}) in Work Profile"
                LaunchTargetMode.HOST_LAUNCHER ->
                    formatText(
                        R.string.status_app_launched,
                        "Launched ${app.displayName} (${app.instanceLabel})",
                        app.displayName,
                        app.instanceLabel
                    )
                null ->
                    formatText(
                        R.string.status_app_launch_failed,
                        "Package ${app.packageName} cannot be launched directly on this device",
                        app.packageName
                    )
            }
        }
    }

    fun inspectInstanceWorkspace(spaceId: Long, appId: Long) {
        viewModelScope.launch {
            inspectedWorkspaceStatsFlow.value = repository.inspectInstanceWorkspace(spaceId, appId)
        }
    }

    fun resetInstanceWorkspace(app: VirtualAppEntity) {
        viewModelScope.launch {
            repository.resetInstanceWorkspace(app.spaceId, app.id)
            inspectedWorkspaceStatsFlow.value = repository.inspectInstanceWorkspace(app.spaceId, app.id)
            statusMessageFlow.value = formatText(
                R.string.status_workspace_reset,
                "Cleared local session data for ${app.displayName} (${app.instanceLabel})",
                app.displayName,
                app.instanceLabel
            )
        }
    }

    fun refreshDeviceBridge() {
        viewModelScope.launch {
            deviceBridgeFlow.value = repository.inspectDeviceBridge()
        }
    }

    fun getMissingRuntimePermissions(): Array<String> = repository.getMissingRuntimePermissions()

    fun onPermissionsResult(results: Map<String, Boolean>) {
        refreshDeviceBridge()
        val granted = results.values.count { it }
        val total = results.size
        if (total > 0) {
            statusMessageFlow.value = formatText(
                R.string.status_permissions_granted,
                "$granted of $total requested permissions granted",
                granted,
                total
            )
        }
    }

    fun onVaultUriPicked(uri: Uri?) {
        if (uri != null) {
            attachedVaultUrisFlow.update { current ->
                val updated = if (current.contains(uri)) current else (listOf(uri) + current).take(20)
                viewModelScope.launch { preferencesStore?.setAttachedVaultUris(updated) }
                updated
            }
            statusMessageFlow.value = formatText(
                R.string.status_vault_imported,
                "Imported media/document into Virtual Vault"
            )
        }
    }

    fun removeVaultUri(uri: Uri) {
        attachedVaultUrisFlow.update { current ->
            val updated = current.filterNot { it == uri }
            viewModelScope.launch { preferencesStore?.setAttachedVaultUris(updated) }
            updated
        }
        statusMessageFlow.value = formatText(
            R.string.status_vault_removed,
            "Removed attachment from Virtual Vault"
        )
    }

    fun onStorageTreePicked(uri: Uri?) {
        if (uri != null) {
            repository.persistStorageTreeUri(uri)
            refreshDeviceBridge()
            statusMessageFlow.value = formatText(
                R.string.status_storage_granted,
                "Storage folder access granted to Cipher Virtual"
            )
        }
    }

    fun toggleTheme() {
        darkThemeFlow.update { current ->
            val next = !current
            viewModelScope.launch { preferencesStore?.setDarkThemeEnabled(next) }
            next
        }
    }

    fun attachIntegrityService(service: IntegrityCheckService?) {
        integrityCollectorJob?.cancel()
        boundIntegrityService = service
        if (service != null) {
            integrityManager.stopPeriodicChecks()
            if (periodicIntegrityEnabledFlow.value) {
                service.startPeriodicVerification()
            } else {
                service.stopPeriodicVerification()
            }
            integrityCollectorJob = viewModelScope.launch {
                service.statusFlow.collect { snapshot ->
                    integrityStatusFlow.value = snapshot
                }
            }
        } else {
            integrityCollectorJob = null
        }
    }

    fun attachResourceService(service: ResourceMonitorService?) {
        resourceCollectorJob?.cancel()
        boundResourceService = service
        if (service != null) {
            fallbackTelemetryJob?.cancel()
            fallbackTelemetryJob = null
            val state = uiState.value
            service.updateTrackedContainers(state.spaces, state.allVirtualApps)
            if (isForegroundActiveFlow.value) {
                service.startMonitoring()
            } else {
                service.stopMonitoring()
            }
            resourceCollectorJob = viewModelScope.launch {
                service.telemetry.collect { snapshot ->
                    resourceTelemetryFlow.value = snapshot
                }
            }
        } else {
            resourceCollectorJob = null
            restartFallbackPollingIfNeeded()
        }
    }

    fun runIntegrityCheckNow() {
        val service = boundIntegrityService
        if (service != null) {
            service.verifyNow(trigger = "manual")
        } else {
            viewModelScope.launch {
                val result = integrityManager.verifyEnvironmentIntegrity(trigger = "manual")
                statusMessageFlow.value = result.summary
            }
        }
    }

    fun setPeriodicIntegrityEnabled(enabled: Boolean) {
        periodicIntegrityEnabledFlow.value = enabled
        viewModelScope.launch { preferencesStore?.setPeriodicIntegrityEnabled(enabled) }
        val service = boundIntegrityService
        if (service != null) {
            if (enabled) service.startPeriodicVerification() else service.stopPeriodicVerification()
        } else {
            integrityManager.setPeriodicEnabled(enabled, viewModelScope)
        }
    }

    fun refreshResourceTelemetry() {
        val service = boundResourceService
        val currentState = uiState.value
        if (service != null) {
            service.updateTrackedContainers(currentState.spaces, currentState.allVirtualApps)
        } else {
            resourceTelemetryFlow.value = resourceSampler.captureSnapshot(
                spaces = currentState.spaces,
                virtualApps = currentState.allVirtualApps
            )
        }
    }

    fun clearStatusMessage() {
        statusMessageFlow.value = null
    }

    override fun onCleared() {
        integrityCollectorJob?.cancel()
        resourceCollectorJob?.cancel()
        fallbackTelemetryJob?.cancel()
        boundIntegrityService = null
        boundResourceService = null
        super.onCleared()
    }

    private fun formatText(resId: Int, fallback: String, vararg args: Any): String {
        return stringLookup?.invoke(resId, args) ?: fallback
    }

    private data class CoreData(
        val spaces: List<VirtualSpaceEntity>,
        val selectedSpaceId: Long?,
        val appsInSpace: List<VirtualAppEntity>,
        val allApps: List<VirtualAppEntity>
    )

    private data class ScannerData(
        val installedApps: List<InstalledAppInfo>,
        val isScanning: Boolean,
        val appIcons: Map<String, ImageBitmap>
    )

    private data class BridgeData(
        val deviceBridge: DeviceBridgeSnapshot,
        val attachedVaultUris: List<Uri>,
        val inspectedWorkspaceStats: InstanceWorkspaceStats?,
        val isForegroundActive: Boolean
    )

    private data class UiFlagsData(
        val launchSessionApp: VirtualAppEntity?,
        val darkThemeEnabled: Boolean,
        val statusMessage: String?,
        val integrityStatus: IntegrityStatusSnapshot,
        val resourceTelemetry: ResourceTelemetrySnapshot
    )
}

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
