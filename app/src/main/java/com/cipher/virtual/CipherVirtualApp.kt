package com.cipher.virtual

import android.app.Application
import com.cipher.virtual.data.hardware.DeviceAppScanner
import com.cipher.virtual.data.hardware.DeviceBridgeManager
import com.cipher.virtual.data.hardware.DeviceProfileManager
import com.cipher.virtual.data.local.OctoDatabase
import com.cipher.virtual.data.local.PreferencesStore
import com.cipher.virtual.data.local.WorkspaceManager
import com.cipher.virtual.data.local.WorkspaceProcessRouter
import com.cipher.virtual.data.local.WorkspaceStorageManager
import com.cipher.virtual.data.repository.OctoRepository
import com.cipher.virtual.monitoring.ResourceMonitorSampler
import com.cipher.virtual.security.IntegrityManager
import com.cipher.virtual.virtualization.VirtualAppExecutionEngine

/**
 * Application-scoped dependency container ensuring that bounded services, Activities, and the
 * ViewModel share the same Room database, workspace manager/router, integrity verifier, and
 * resource monitor sampler across configuration changes and service bind cycles.
 */
class CipherVirtualApp : Application() {

    val database: OctoDatabase by lazy {
        OctoDatabase.getInstance(applicationContext)
    }

    val workspaceStorage: WorkspaceStorageManager by lazy {
        WorkspaceStorageManager(applicationContext)
    }

    val workspaceManager: WorkspaceManager by lazy {
        WorkspaceManager(
            storageManager = workspaceStorage,
            dao = database.workspaceInstanceDao()
        )
    }

    val workspaceRouter: WorkspaceProcessRouter by lazy {
        WorkspaceProcessRouter(
            workspaceManager = workspaceManager,
            storageManager = workspaceStorage
        )
    }

    val appScanner: DeviceAppScanner by lazy {
        DeviceAppScanner(applicationContext)
    }

    val bridgeManager: DeviceBridgeManager by lazy {
        DeviceBridgeManager(applicationContext)
    }

    val preferencesStore: PreferencesStore by lazy {
        PreferencesStore(applicationContext)
    }

    val deviceProfileManager: DeviceProfileManager by lazy {
        DeviceProfileManager(applicationContext)
    }

    val resourceSampler: ResourceMonitorSampler by lazy {
        ResourceMonitorSampler(applicationContext)
    }

    val integrityManager: IntegrityManager by lazy {
        IntegrityManager(applicationContext)
    }

    val virtualExecutionEngine: VirtualAppExecutionEngine by lazy {
        VirtualAppExecutionEngine(
            context = applicationContext,
            storageManager = workspaceStorage
        )
    }

    val repository: OctoRepository by lazy {
        OctoRepository(
            dao = database.octoDao(),
            scanner = appScanner,
            bridgeManager = bridgeManager,
            workspaceStorage = workspaceStorage,
            workspaceManager = workspaceManager,
            workspaceRouter = workspaceRouter,
            virtualExecutionEngine = virtualExecutionEngine
        )
    }
}
