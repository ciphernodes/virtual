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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    val resourceTelemetry: ResourceTelemetrySnapshot = ResourceTelemetrySnapshot()
) {
    val activeSpace: VirtualSpaceEntity?
        get() = spaces.firstOrNull { it.id == selectedSpaceId } ?: spaces.firstOrNull()
}

@OptIn(ExperimentalCoroutinesApi::class)
class OctoViewModel(
    private val repository: OctoRepository,
    private val integrityManager: IntegrityManager,
    private val resourceSampler: ResourceMonitorSampler,
    private val preferencesStore: PreferencesStore? = null
) : ViewModel() {

    private val selectedSpaceIdFlow = MutableStateFlow(preferencesStore?.getSelectedSpaceId())
    private val installedAppsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val isScanningAppsFlow = MutableStateFlow(false)
    private val appIconsFlow = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private val deviceBridgeFlow = MutableStateFlow(DeviceBridgeSnapshot())
    private val attachedVaultUrisFlow = MutableStateFlow(preferencesStore?.getAttachedVaultUris().orEmpty())
    private val launchSessionAppFlow = MutableStateFlow<VirtualAppEntity?>(null)
    private val darkThemeFlow = MutableStateFlow(preferencesStore?.isDarkThemeEnabled() ?: true)
    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private val periodicIntegrityEnabledFlow = MutableStateFlow(
        preferencesStore?.isPeriodicIntegrityEnabled() ?: true
    )
    private val integrityStatusFlow = MutableStateFlow(
        integrityManager.status.value.copy(
            isPeriodicEnabled = preferencesStore?.isPeriodicIntegrityEnabled() ?: true
        )
    )
    private val resourceTelemetryFlow = MutableStateFlow(ResourceTelemetrySnapshot())

    private var boundIntegrityService: IntegrityCheckService? = null
    private var boundResourceService: ResourceMonitorService? = null
    private var integrityCollectorJob: Job? = null
    private var resourceCollectorJob: Job? = null

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
        attachedVaultUrisFlow
    ) { bridge, uris ->
        BridgeData(
            deviceBridge = bridge,
            attachedVaultUris = uris
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
        uiFlagsFlow
    ) { core, scanner, bridge, flags ->
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
            resourceTelemetry = flags.resourceTelemetry
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OctoUiState(darkThemeEnabled = darkThemeFlow.value)
    )

    init {
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
                val boundRes = boundResourceService
                if (boundRes != null) {
                    boundRes.updateTrackedContainers(spaces, apps)
                } else {
                    resourceTelemetryFlow.value = resourceSampler.captureSnapshot(spaces, apps)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(3000L)
                if (boundResourceService == null) {
                    refreshResourceTelemetry()
                }
            }
        }
        viewModelScope.launch {
            repository.allVirtualApps.collect { apps ->
                val missing = apps.map { it.packageName }.distinct()
                    .filter { !appIconsFlow.value.containsKey(it) }
                if (missing.isNotEmpty()) {
                    val newIcons = missing.mapNotNull { pkg ->
                        repository.getAppIcon(pkg)?.let { pkg to it }
                    }.toMap()
                    if (newIcons.isNotEmpty()) {
                        appIconsFlow.update { it + newIcons }
                    }
                }
            }
        }
    }

    fun selectSpace(spaceId: Long) {
        selectedSpaceIdFlow.value = spaceId
        preferencesStore?.setSelectedSpaceId(spaceId)
    }

    fun createSpace(name: String, tag: String, accentIndex: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.createSpace(name, tag, accentIndex)
            selectedSpaceIdFlow.value = newId
            preferencesStore?.setSelectedSpaceId(newId)
            statusMessageFlow.value = "Space \"${name.trim()}\" initialized"
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
            statusMessageFlow.value = "Updated space \"$trimmedName\""
        }
    }

    fun updateSpace(space: VirtualSpaceEntity) {
        viewModelScope.launch {
            repository.updateSpace(space)
        }
    }

    fun resetSpaceDefaults(space: VirtualSpaceEntity) {
        viewModelScope.launch {
            repository.updateSpace(
                space.copy(
                    deviceModelPreset = "ROG Phone 8 Pro",
                    virtualDpi = 440,
                    isolatedStorage = true,
                    frameRateHud = true,
                    permissionBridgeEnabled = true
                )
            )
            statusMessageFlow.value = "Restored default sandbox settings for ${space.name}"
        }
    }

    fun deleteSpace(spaceId: Long) {
        viewModelScope.launch {
            if (uiState.value.spaces.size <= 1) {
                statusMessageFlow.value = "At least one Virtual Space must remain active"
                return@launch
            }
            repository.deleteSpace(spaceId)
            if (selectedSpaceIdFlow.value == spaceId) {
                selectedSpaceIdFlow.value = null
                preferencesStore?.setSelectedSpaceId(null)
            }
            statusMessageFlow.value = "Virtual Space removed"
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            isScanningAppsFlow.value = true
            val apps = repository.scanDeviceApps()
            installedAppsFlow.value = apps
            val icons = apps.mapNotNull { info -> info.icon?.let { info.packageName to it } }.toMap()
            if (icons.isNotEmpty()) {
                appIconsFlow.update { it + icons }
            }
            isScanningAppsFlow.value = false
        }
    }

    fun addAppToActiveSpace(installedApp: InstalledAppInfo) {
        val active = uiState.value.activeSpace ?: return
        viewModelScope.launch {
            repository.addAppToSpace(active.id, installedApp)
            statusMessageFlow.value = "Cloned ${installedApp.label} into ${active.name}"
        }
    }

    fun duplicateVirtualApp(app: VirtualAppEntity) {
        viewModelScope.launch {
            repository.duplicateVirtualApp(app)
            statusMessageFlow.value = "Created new instance of ${app.displayName}"
        }
    }

    fun removeVirtualApp(app: VirtualAppEntity) {
        viewModelScope.launch {
            repository.removeVirtualApp(app.id)
            statusMessageFlow.value = "Removed ${app.displayName} (${app.instanceLabel})"
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

    fun launchAppFromSession(app: VirtualAppEntity) {
        viewModelScope.launch {
            repository.recordLaunch(app.id)
            val launched = repository.launchPackage(app.packageName)
            launchSessionAppFlow.value = null
            statusMessageFlow.value = if (launched) {
                "Launched ${app.displayName} (${app.instanceLabel})"
            } else {
                "Package ${app.packageName} cannot be launched directly on this device"
            }
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
            statusMessageFlow.value = "$granted of $total requested permissions granted"
        }
    }

    fun onVaultUriPicked(uri: Uri?) {
        if (uri != null) {
            attachedVaultUrisFlow.update { current ->
                val updated = if (current.contains(uri)) current else (listOf(uri) + current).take(20)
                preferencesStore?.setAttachedVaultUris(updated)
                updated
            }
            statusMessageFlow.value = "Imported media/document into Virtual Vault"
        }
    }

    fun removeVaultUri(uri: Uri) {
        attachedVaultUrisFlow.update { current ->
            val updated = current.filterNot { it == uri }
            preferencesStore?.setAttachedVaultUris(updated)
            updated
        }
        statusMessageFlow.value = "Removed attachment from Virtual Vault"
    }

    fun onStorageTreePicked(uri: Uri?) {
        if (uri != null) {
            repository.persistStorageTreeUri(uri)
            refreshDeviceBridge()
            statusMessageFlow.value = "Storage folder access granted to Cipher Virtual"
        }
    }

    fun toggleTheme() {
        darkThemeFlow.update { current ->
            val next = !current
            preferencesStore?.setDarkThemeEnabled(next)
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
            val state = uiState.value
            service.updateTrackedContainers(state.spaces, state.allVirtualApps)
            service.startMonitoring()
            resourceCollectorJob = viewModelScope.launch {
                service.telemetry.collect { snapshot ->
                    resourceTelemetryFlow.value = snapshot
                }
            }
        } else {
            resourceCollectorJob = null
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
        preferencesStore?.setPeriodicIntegrityEnabled(enabled)
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
        val attachedVaultUris: List<Uri>
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
    private val preferencesStore: PreferencesStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OctoViewModel::class.java)) {
            return OctoViewModel(repository, integrityManager, resourceSampler, preferencesStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
