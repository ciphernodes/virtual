package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ciphernodes.ciphervirtual.monitoring.ResourceMonitorSampler
import com.ciphernodes.ciphervirtual.monitoring.ResourceMonitorService
import com.ciphernodes.ciphervirtual.security.IntegrityCheckService
import com.ciphernodes.ciphervirtual.security.IntegrityManager
import com.example.data.hardware.DeviceAppScanner
import com.example.data.hardware.DeviceBridgeManager
import com.example.data.hardware.DeviceProfileManager
import com.example.data.local.OctoDatabase
import com.example.data.local.PreferencesStore
import com.example.data.local.WorkspaceStorageManager
import com.example.data.repository.OctoRepository
import com.example.ui.OctoViewModel
import com.example.ui.OctoViewModelFactory
import com.example.ui.navigation.OctoApp
import com.example.ui.theme.OctoSpaceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: OctoViewModel by viewModels {
        val db = OctoDatabase.getInstance(applicationContext)
        val scanner = DeviceAppScanner(applicationContext)
        val bridgeManager = DeviceBridgeManager(applicationContext)
        val workspaceStorage = WorkspaceStorageManager(applicationContext.filesDir)
        val integrityManager = IntegrityManager(applicationContext)
        val resourceSampler = ResourceMonitorSampler(applicationContext)
        val preferencesStore = PreferencesStore(applicationContext)
        val deviceProfileManager = DeviceProfileManager(applicationContext)
        OctoViewModelFactory(
            repository = OctoRepository(db.octoDao(), scanner, bridgeManager, workspaceStorage),
            integrityManager = integrityManager,
            resourceSampler = resourceSampler,
            preferencesStore = preferencesStore,
            deviceProfileManager = deviceProfileManager,
            stringLookup = { resId, args ->
                if (args.isEmpty()) applicationContext.getString(resId)
                else applicationContext.getString(resId, *args)
            }
        )
    }

    private var integrityBound = false
    private var resourceBound = false

    private val integrityConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? IntegrityCheckService.LocalBinder
            viewModel.attachIntegrityService(binder?.getService())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            viewModel.attachIntegrityService(null)
        }
    }

    private val resourceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? ResourceMonitorService.LocalBinder
            viewModel.attachResourceService(binder?.getService())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            viewModel.attachResourceService(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.darkThemeEnabled) {
                if (uiState.darkThemeEnabled) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    )
                }
            }

            OctoSpaceTheme(darkTheme = uiState.darkThemeEnabled) {
                OctoApp(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setForegroundActive(true)
        integrityBound = bindService(
            Intent(this, IntegrityCheckService::class.java),
            integrityConnection,
            Context.BIND_AUTO_CREATE
        )
        resourceBound = bindService(
            Intent(this, ResourceMonitorService::class.java),
            resourceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDeviceBridge()
    }

    override fun onStop() {
        super.onStop()
        viewModel.setForegroundActive(false)
        viewModel.attachIntegrityService(null)
        viewModel.attachResourceService(null)
        if (integrityBound) {
            runCatching { unbindService(integrityConnection) }
            integrityBound = false
        }
        if (resourceBound) {
            runCatching { unbindService(resourceConnection) }
            resourceBound = false
        }
    }
}
