package com.cipher.virtual.ui

import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.data.model.VirtualSpaceEntity
import com.cipher.virtual.monitoring.ResourceMonitorSampler
import com.cipher.virtual.monitoring.ResourceMonitorService
import com.cipher.virtual.monitoring.ResourceTelemetrySnapshot
import com.cipher.virtual.security.IntegrityCheckService
import com.cipher.virtual.security.IntegrityManager
import com.cipher.virtual.security.IntegrityStatusSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates bound [IntegrityCheckService] and [ResourceMonitorService] lifecycles,
 * shared singleton telemetry fallback sampling, and foreground polling state for [OctoViewModel].
 */
internal class ServiceTelemetryBridge(
    private val scope: CoroutineScope,
    private val integrityManager: IntegrityManager,
    private val resourceSampler: ResourceMonitorSampler,
    private val onStatusMessage: (String) -> Unit
) {
    private val _periodicIntegrityEnabled = MutableStateFlow(true)
    private val _integrityStatus = MutableStateFlow(integrityManager.status.value)
    private val _resourceTelemetry = MutableStateFlow(ResourceTelemetrySnapshot())
    private val _isForegroundActive = MutableStateFlow(true)

    val integrityStatus: StateFlow<IntegrityStatusSnapshot> = _integrityStatus.asStateFlow()
    val resourceTelemetry: StateFlow<ResourceTelemetrySnapshot> = _resourceTelemetry.asStateFlow()
    val isForegroundActive: StateFlow<Boolean> = _isForegroundActive.asStateFlow()

    private var boundIntegrityService: IntegrityCheckService? = null
    private var boundResourceService: ResourceMonitorService? = null
    private var integrityCollectorJob: Job? = null
    private var resourceCollectorJob: Job? = null
    private var fallbackTelemetryJob: Job? = null

    init {
        scope.launch {
            integrityManager.status.collect { snapshot ->
                if (boundIntegrityService == null) {
                    _integrityStatus.value = snapshot.copy(isPeriodicEnabled = _periodicIntegrityEnabled.value)
                }
            }
        }
        restartFallbackPollingIfNeeded()
    }

    fun onPeriodicPreferenceChanged(enabled: Boolean) {
        _periodicIntegrityEnabled.value = enabled
        _integrityStatus.update { it.copy(isPeriodicEnabled = enabled) }
    }

    fun setForegroundActive(active: Boolean) {
        _isForegroundActive.value = active
        val resourceService = boundResourceService
        if (resourceService != null) {
            if (active) resourceService.startMonitoring() else resourceService.stopMonitoring()
        } else {
            restartFallbackPollingIfNeeded()
        }
    }

    suspend fun updateTrackedContainers(
        spaces: List<VirtualSpaceEntity>,
        apps: List<VirtualAppEntity>
    ) {
        val boundRes = boundResourceService
        if (boundRes != null) {
            withContext(Dispatchers.Default) { boundRes.updateTrackedContainers(spaces, apps) }
        } else {
            _resourceTelemetry.value =
                withContext(Dispatchers.Default) { resourceSampler.captureSnapshot(spaces, apps) }
        }
    }

    fun attachIntegrityService(service: IntegrityCheckService?) {
        integrityCollectorJob?.cancel()
        boundIntegrityService = service
        if (service != null) {
            integrityManager.stopPeriodicChecks()
            if (_periodicIntegrityEnabled.value) {
                service.startPeriodicVerification()
            } else {
                service.stopPeriodicVerification()
            }
            integrityCollectorJob = scope.launch {
                service.statusFlow.collect { _integrityStatus.value = it }
            }
        } else {
            integrityCollectorJob = null
        }
    }

    fun attachResourceService(
        service: ResourceMonitorService?,
        spaces: List<VirtualSpaceEntity>,
        apps: List<VirtualAppEntity>
    ) {
        resourceCollectorJob?.cancel()
        boundResourceService = service
        if (service != null) {
            fallbackTelemetryJob?.cancel()
            fallbackTelemetryJob = null
            scope.launch(Dispatchers.Default) {
                service.updateTrackedContainers(spaces, apps)
            }
            if (_isForegroundActive.value) service.startMonitoring() else service.stopMonitoring()
            resourceCollectorJob = scope.launch {
                service.telemetry.collect { _resourceTelemetry.value = it }
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
            scope.launch {
                val result = integrityManager.verifyEnvironmentIntegrity(trigger = "manual")
                onStatusMessage(result.summary)
            }
        }
    }

    fun setPeriodicIntegrityEnabled(enabled: Boolean) {
        _periodicIntegrityEnabled.value = enabled
        val service = boundIntegrityService
        if (service != null) {
            if (enabled) service.startPeriodicVerification() else service.stopPeriodicVerification()
        } else {
            integrityManager.setPeriodicEnabled(enabled, scope)
        }
    }

    fun refreshResourceTelemetry(
        spaces: List<VirtualSpaceEntity>,
        apps: List<VirtualAppEntity>
    ) {
        val service = boundResourceService
        scope.launch(Dispatchers.Default) {
            if (service != null) {
                service.updateTrackedContainers(spaces, apps)
            } else {
                _resourceTelemetry.value = resourceSampler.captureSnapshot(
                    spaces = spaces,
                    virtualApps = apps
                )
            }
        }
    }

    fun restartFallbackPolling(
        spacesProvider: () -> List<VirtualSpaceEntity>,
        appsProvider: () -> List<VirtualAppEntity>
    ) {
        fallbackTelemetryJob?.cancel()
        if (boundResourceService != null || !_isForegroundActive.value) {
            fallbackTelemetryJob = null
            return
        }
        fallbackTelemetryJob = scope.launch {
            while (isActive) {
                delay(3000L)
                if (boundResourceService == null && _isForegroundActive.value) {
                    refreshResourceTelemetry(spacesProvider(), appsProvider())
                }
            }
        }
    }

    private fun restartFallbackPollingIfNeeded() {
        fallbackTelemetryJob?.cancel()
        if (boundResourceService != null || !_isForegroundActive.value) {
            fallbackTelemetryJob = null
        }
    }

    fun clear() {
        integrityCollectorJob?.cancel()
        resourceCollectorJob?.cancel()
        fallbackTelemetryJob?.cancel()
        boundIntegrityService = null
        boundResourceService = null
    }
}
