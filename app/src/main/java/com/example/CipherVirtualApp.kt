package com.example

import android.app.Application
import com.example.data.hardware.DeviceAppScanner
import com.example.data.hardware.DeviceBridgeManager
import com.example.data.hardware.DeviceProfileManager
import com.example.data.local.OctoDatabase
import com.example.data.local.PreferencesStore
import com.example.data.local.WorkspaceManager
import com.example.data.local.WorkspaceProcessRouter
import com.example.data.local.WorkspaceStorageManager
import com.example.data.repository.OctoRepository
import com.example.monitoring.ResourceMonitorSampler
import com.example.security.IntegrityManager

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

    val repository: OctoRepository by lazy {
        OctoRepository(
            dao = database.octoDao(),
            scanner = appScanner,
            bridgeManager = bridgeManager,
            workspaceStorage = workspaceStorage,
            workspaceManager = workspaceManager,
            workspaceRouter = workspaceRouter
        )
    }
}
