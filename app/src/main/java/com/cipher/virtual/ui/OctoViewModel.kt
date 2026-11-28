package com.cipher.virtual.ui

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cipher.virtual.R
import com.cipher.virtual.data.hardware.DeviceProfile
import com.cipher.virtual.data.hardware.DeviceProfileManager
import com.cipher.virtual.data.hardware.LaunchTargetMode
import com.cipher.virtual.data.local.InstanceWorkspaceStats
import com.cipher.virtual.data.local.PreferencesStore
import com.cipher.virtual.data.model.DeviceBridgeSnapshot
import com.cipher.virtual.data.model.InstalledAppInfo
import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.data.model.VirtualSpaceEntity
import com.cipher.virtual.data.repository.OctoRepository
import com.cipher.virtual.monitoring.ResourceMonitorSampler
import com.cipher.virtual.monitoring.ResourceMonitorService
import com.cipher.virtual.security.IntegrityCheckService
import com.cipher.virtual.security.IntegrityManager
import com.cipher.virtual.virtualization.NativeVirtualEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class OctoViewModel(
    private val repository: OctoRepository,
    integrityManager: IntegrityManager,
    resourceSampler: ResourceMonitorSampler,
    private val preferencesStore: PreferencesStore? = null,
    deviceProfileManager: DeviceProfileManager? = null,
    private val stringLookup: ((Int, Array<out Any>) -> String)? = null
) : ViewModel() {

    private val installedAppsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val isScanningAppsFlow = MutableStateFlow(false)
    private val appIconsFlow = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private val deviceBridgeFlow = MutableStateFlow(DeviceBridgeSnapshot())
    private val attachedVaultUrisFlow = MutableStateFlow<List<Uri>>(emptyList())
    private val launchSessionAppFlow = MutableStateFlow<VirtualAppEntity?>(null)
    private val darkThemeFlow = MutableStateFlow(true)
    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private val inspectedWorkspaceStatsFlow = MutableStateFlow<InstanceWorkspaceStats?>(null)
    private val inspectedWorkerSlotFlow = MutableStateFlow<String?>(null)

    private val profileController = DeviceProfileStateController(
        repository = repository,
        deviceProfileManager = deviceProfileManager,
        formatText = { resId, fallback, args -> stringLookup?.invoke(resId, args) ?: fallback }
    )

    private val telemetryBridge = ServiceTelemetryBridge(
        scope = viewModelScope,
        integrityManager = integrityManager,
        resourceSampler = resourceSampler,
        onStatusMessage = { statusMessageFlow.value = it }
    )

    private val appsInSpaceFlow = repository.spaces.flatMapLatest { spaces ->
        val primarySpaceId = spaces.firstOrNull()?.id
        if (primarySpaceId == null) flowOf(emptyList())
        else repository.observeAppsForSpace(primarySpaceId)
    }

    private val coreDataFlow = combine(
        repository.spaces,
        appsInSpaceFlow,
        repository.allVirtualApps
    ) { spaces, appsInSpace, allApps ->
        CoreData(
            spaces = spaces,
            selectedSpaceId = spaces.firstOrNull()?.id,
            appsInSpace = appsInSpace,
            allApps = allApps
        )
    }

    private val scannerDataFlow = combine(
        installedAppsFlow,
        isScanningAppsFlow,
        appIconsFlow
    ) { installed, isScanning, icons ->
        ScannerData(installedApps = installed, isScanning = isScanning, appIcons = icons)
    }

    private val bridgeDataFlow = combine(
        deviceBridgeFlow,
        attachedVaultUrisFlow,
        inspectedWorkspaceStatsFlow,
        inspectedWorkerSlotFlow,
        telemetryBridge.isForegroundActive
    ) { bridge, uris, stats, workerSlot, foreground ->
        BridgeData(bridge, uris, stats, workerSlot, foreground)
    }

    private val uiFlagsFlow = combine(
        launchSessionAppFlow,
        darkThemeFlow,
        statusMessageFlow,
        telemetryBridge.integrityStatus,
        telemetryBridge.resourceTelemetry
    ) { launchApp, darkTheme, statusMsg, integrity, telemetry ->
        UiFlagsData(launchApp, darkTheme, statusMsg, integrity, telemetry)
    }

    val uiState: StateFlow<OctoUiState> = combine(
        coreDataFlow,
        scannerDataFlow,
        bridgeDataFlow,
        uiFlagsFlow,
        combine(profileController.activeProfile, profileController.availableProfiles) { active, all -> active to all }
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
            inspectedWorkerSlot = bridge.inspectedWorkerSlot,
            isTelemetryPausedInBackground = !bridge.isForegroundActive,
            isNativeEngineAvailable = NativeVirtualEngine.isNativeEngineAvailable(),
            nativeEngineLoadWarning = NativeVirtualEngine.getLoadFailureReason()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OctoUiState(
            darkThemeEnabled = darkThemeFlow.value,
            isNativeEngineAvailable = NativeVirtualEngine.isNativeEngineAvailable(),
            nativeEngineLoadWarning = NativeVirtualEngine.getLoadFailureReason()
        )
    )

    init {
        preferencesStore?.let { store ->
            viewModelScope.launch { store.darkThemeFlow.distinctUntilChanged().collect { darkThemeFlow.value = it } }
            viewModelScope.launch {
                store.periodicIntegrityFlow.distinctUntilChanged().collect { enabled ->
                    telemetryBridge.onPeriodicPreferenceChanged(enabled)
                }
            }
            viewModelScope.launch { store.attachedVaultUrisFlow.collect { attachedVaultUrisFlow.value = it } }
        }

        viewModelScope.launch {
            repository.ensureDefaultSpaces()
            refreshInstalledApps()
            refreshDeviceBridge()
            refreshResourceTelemetry()
        }

        viewModelScope.launch {
            combine(repository.spaces, repository.allVirtualApps) { spaces, apps -> spaces to apps }
                .collect { (spaces, apps) ->
                    profileController.syncForActiveSpace(spaces.firstOrNull())
                    telemetryBridge.updateTrackedContainers(spaces, apps)
                }
        }

        viewModelScope.launch {
            repository.allVirtualApps.collect { apps ->
                val missing = apps.map { it.packageName }.distinct().filter { !appIconsFlow.value.containsKey(it) }
                if (missing.isNotEmpty()) {
                    val newIcons = repository.loadAppIcons(missing)
                    if (newIcons.isNotEmpty()) {
                        appIconsFlow.update { it + newIcons }
                    }
                }
            }
        }

        telemetryBridge.restartFallbackPolling(
            spacesProvider = { uiState.value.spaces },
            appsProvider = { uiState.value.allVirtualApps }
        )
    }

    fun setForegroundActive(active: Boolean) {
        telemetryBridge.setForegroundActive(active)
        if (active) {
            telemetryBridge.restartFallbackPolling(
                spacesProvider = { uiState.value.spaces },
                appsProvider = { uiState.value.allVirtualApps }
            )
        }
    }

    fun updateSpace(space: VirtualSpaceEntity) {
        viewModelScope.launch { repository.updateSpace(space) }
    }

    fun resetSpaceDefaults(space: VirtualSpaceEntity) {
        viewModelScope.launch { statusMessageFlow.value = profileController.resetSpaceDefaults(space) }
    }

    fun selectDeviceProfile(space: VirtualSpaceEntity, profile: DeviceProfile) {
        viewModelScope.launch { profileController.selectPreset(space, profile) }
    }

    fun saveCustomDeviceProfile(space: VirtualSpaceEntity, profile: DeviceProfile) {
        viewModelScope.launch { statusMessageFlow.value = profileController.saveCustom(space, profile) }
    }

    fun deleteCustomDeviceProfile(space: VirtualSpaceEntity?, profileId: String) {
        viewModelScope.launch { statusMessageFlow.value = profileController.deleteCustom(space, profileId) }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            isScanningAppsFlow.value = true
            installedAppsFlow.value = repository.scanDeviceApps()
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

    fun hasWorkProfileTarget(packageName: String): Boolean = repository.hasWorkProfileTarget(packageName)

    fun launchAppFromSession(app: VirtualAppEntity, preferWorkProfile: Boolean = false) {
        viewModelScope.launch {
            val space = uiState.value.spaces.firstOrNull { it.id == app.spaceId } ?: uiState.value.activeSpace
            val profile = uiState.value.activeDeviceProfile
            repository.recordLaunch(app, space, profile)
            val mode = repository.launchPackage(app.packageName, preferWorkProfile)
            launchSessionAppFlow.value = null
            if (inspectedWorkspaceStatsFlow.value != null && space != null) {
                inspectedWorkspaceStatsFlow.value = repository.inspectInstanceWorkspace(space.id, app.id)
                inspectedWorkerSlotFlow.value = repository.getWorkerSlotForInstance(space.id, app.id)
            }
            statusMessageFlow.value = when (mode) {
                LaunchTargetMode.WORK_PROFILE -> formatText(
                    R.string.status_app_launched_work_profile,
                    "Launched ${app.displayName} (${app.instanceLabel}) in Work Profile",
                    app.displayName,
                    app.instanceLabel
                )
                LaunchTargetMode.HOST_LAUNCHER -> formatText(
                    R.string.status_app_launched,
                    "Launched ${app.displayName} (${app.instanceLabel})",
                    app.displayName,
                    app.instanceLabel
                )
                null -> formatText(
                    R.string.status_app_launch_failed,
                    "Package ${app.packageName} cannot be launched directly on this device",
                    app.packageName
                )
            }
        }
    }

    fun launchVirtualSandbox(app: VirtualAppEntity, preferRoot: Boolean = false) {
        viewModelScope.launch {
            val space = uiState.value.spaces.firstOrNull { it.id == app.spaceId } ?: uiState.value.activeSpace
            val profile = uiState.value.activeDeviceProfile
            repository.recordLaunch(app, space, profile)
            val result = repository.launchVirtualSandbox(
                app = app,
                preferRoot = preferRoot,
                darkTheme = uiState.value.darkThemeEnabled
            )
            launchSessionAppFlow.value = null
            if (inspectedWorkspaceStatsFlow.value != null && space != null) {
                inspectedWorkspaceStatsFlow.value = repository.inspectInstanceWorkspace(space.id, app.id)
                inspectedWorkerSlotFlow.value = repository.getWorkerSlotForInstance(space.id, app.id)
            }
            if (result != null) {
                statusMessageFlow.value = result.message
            } else {
                launchAppFromSession(app, preferWorkProfile = false)
            }
        }
    }

    fun inspectInstanceWorkspace(spaceId: Long, appId: Long) {
        viewModelScope.launch {
            inspectedWorkspaceStatsFlow.value = repository.inspectInstanceWorkspace(spaceId, appId)
            inspectedWorkerSlotFlow.value = repository.getWorkerSlotForInstance(spaceId, appId)
        }
    }

    fun resetInstanceWorkspace(app: VirtualAppEntity) {
        viewModelScope.launch {
            repository.resetInstanceWorkspace(app.spaceId, app.id)
            inspectedWorkspaceStatsFlow.value = repository.inspectInstanceWorkspace(app.spaceId, app.id)
            inspectedWorkerSlotFlow.value = repository.getWorkerSlotForInstance(app.spaceId, app.id)
            statusMessageFlow.value = formatText(
                R.string.status_workspace_reset,
                "Cleared local session data for ${app.displayName} (${app.instanceLabel})",
                app.displayName,
                app.instanceLabel
            )
        }
    }

    fun refreshDeviceBridge() {
        viewModelScope.launch { deviceBridgeFlow.value = repository.inspectDeviceBridge() }
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
        if (uri == null) return
        attachedVaultUrisFlow.update { current ->
            val updated = if (current.contains(uri)) current else (listOf(uri) + current).take(20)
            viewModelScope.launch { preferencesStore?.setAttachedVaultUris(updated) }
            updated
        }
        statusMessageFlow.value = formatText(R.string.status_vault_imported, "Imported media/document into Virtual Vault")
    }

    fun removeVaultUri(uri: Uri) {
        attachedVaultUrisFlow.update { current ->
            val updated = current.filterNot { it == uri }
            viewModelScope.launch { preferencesStore?.setAttachedVaultUris(updated) }
            updated
        }
        statusMessageFlow.value = formatText(R.string.status_vault_removed, "Removed attachment from Virtual Vault")
    }

    fun onStorageTreePicked(uri: Uri?) {
        if (uri == null) return
        repository.persistStorageTreeUri(uri)
        refreshDeviceBridge()
        statusMessageFlow.value = formatText(R.string.status_storage_granted, "Storage folder access granted to Cipher Virtual")
    }

    fun toggleTheme() {
        darkThemeFlow.update { current ->
            val next = !current
            viewModelScope.launch { preferencesStore?.setDarkThemeEnabled(next) }
            next
        }
    }

    fun attachIntegrityService(service: IntegrityCheckService?) =
        telemetryBridge.attachIntegrityService(service)

    fun attachResourceService(service: ResourceMonitorService?) {
        val state = uiState.value
        telemetryBridge.attachResourceService(service, state.spaces, state.allVirtualApps)
        if (service == null) {
            telemetryBridge.restartFallbackPolling(
                spacesProvider = { uiState.value.spaces },
                appsProvider = { uiState.value.allVirtualApps }
            )
        }
    }

    fun runIntegrityCheckNow() = telemetryBridge.runIntegrityCheckNow()

    fun setPeriodicIntegrityEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore?.setPeriodicIntegrityEnabled(enabled) }
        telemetryBridge.setPeriodicIntegrityEnabled(enabled)
    }

    fun refreshResourceTelemetry() {
        val state = uiState.value
        telemetryBridge.refreshResourceTelemetry(state.spaces, state.allVirtualApps)
    }

    fun clearStatusMessage() {
        statusMessageFlow.value = null
    }

    override fun onCleared() {
        telemetryBridge.clear()
        super.onCleared()
    }

    private fun formatText(resId: Int, fallback: String, vararg args: Any): String =
        stringLookup?.invoke(resId, args) ?: fallback
}
