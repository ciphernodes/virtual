package com.example.security

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.example.CipherVirtualApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

fun interface IntegrityStatusCallback {
    fun onStatusChanged(snapshot: IntegrityStatusSnapshot)
}

class IntegrityCheckService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): IntegrityCheckService = this@IntegrityCheckService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var integrityManager: IntegrityManager
    private val callbacks = CopyOnWriteArraySet<IntegrityStatusCallback>()

    val statusFlow: StateFlow<IntegrityStatusSnapshot>
        get() = integrityManager.status

    override fun onCreate() {
        super.onCreate()
        integrityManager = (application as? CipherVirtualApp)?.integrityManager
            ?: IntegrityManager(applicationContext)
        serviceScope.launch {
            integrityManager.status.collect { snapshot ->
                callbacks.forEach { callback ->
                    callback.onStatusChanged(snapshot)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PERIODIC -> {
                val intervalMillis = intent.getLongExtra(EXTRA_INTERVAL_MILLIS, DEFAULT_INTERVAL_MILLIS)
                startPeriodicVerification(intervalMillis)
            }
            ACTION_VERIFY_NOW -> {
                verifyNow()
            }
            ACTION_STOP_PERIODIC -> {
                stopPeriodicVerification()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        integrityManager.stopPeriodicChecks()
        callbacks.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    fun registerCallback(callback: IntegrityStatusCallback) {
        callbacks.add(callback)
        callback.onStatusChanged(integrityManager.status.value)
    }

    fun unregisterCallback(callback: IntegrityStatusCallback) {
        callbacks.remove(callback)
    }

    fun startPeriodicVerification(intervalMillis: Long = DEFAULT_INTERVAL_MILLIS) {
        integrityManager.startPeriodicChecks(serviceScope, intervalMillis)
    }

    fun stopPeriodicVerification() {
        integrityManager.stopPeriodicChecks()
    }

    fun verifyNow(trigger: String = "service") {
        serviceScope.launch {
            integrityManager.verifyEnvironmentIntegrity(trigger = trigger)
        }
    }

    companion object {
        const val ACTION_START_PERIODIC = "com.cipher.virtual.action.START_PERIODIC_INTEGRITY"
        const val ACTION_VERIFY_NOW = "com.cipher.virtual.action.VERIFY_INTEGRITY_NOW"
        const val ACTION_STOP_PERIODIC = "com.cipher.virtual.action.STOP_PERIODIC_INTEGRITY"
        const val EXTRA_INTERVAL_MILLIS = "extra_interval_millis"
        private const val DEFAULT_INTERVAL_MILLIS = 15L * 60L * 1000L
    }
}
