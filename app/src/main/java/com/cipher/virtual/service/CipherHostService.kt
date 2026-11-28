package com.cipher.virtual.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cipher.virtual.MainActivity
import com.cipher.virtual.R

/**
 * Persistent foreground host service (`foregroundServiceType="dataSync"`) that calls
 * `startForeground()` immediately in `onCreate()`, survives Doze when battery-optimization
 * exemption is granted, and can be re-armed automatically on `BOOT_COMPLETED`.
 */
class CipherHostService : Service() {

    private val binder = LocalBinder()

    @Volatile
    var isForegroundActive: Boolean = false
        private set

    @Volatile
    var startedAtMillis: Long = 0L
        private set

    @Volatile
    var startCommandCount: Int = 0
        private set

    inner class LocalBinder : Binder() {
        fun getService(): CipherHostService = this@CipherHostService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startedAtMillis = System.currentTimeMillis()
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            isForegroundActive = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startCommandCount += 1
        if (!isForegroundActive) {
            promoteToForeground()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isForegroundActive = false
        super.onDestroy()
    }

    private fun promoteToForeground() {
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), fgsType)
        isForegroundActive = true
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.cipher_host_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.cipher_host_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.cipher_host_notification_title))
            .setContentText(getString(R.string.cipher_host_notification_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "CipherHostService"
        const val CHANNEL_ID = "cipher_host_sync_channel"
        const val NOTIFICATION_ID = 2096
        const val ACTION_START = "com.cipher.virtual.action.START_HOST_SERVICE"
        const val ACTION_STOP = "com.cipher.virtual.action.STOP_HOST_SERVICE"
        const val EXTRA_TRIGGER_REASON = "extra_trigger_reason"

        fun createStartIntent(context: Context, triggerReason: String = "manual"): Intent {
            return Intent(context, CipherHostService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIGGER_REASON, triggerReason)
            }
        }

        fun start(context: Context, triggerReason: String = "manual"): Boolean {
            return runCatching {
                ContextCompat.startForegroundService(context, createStartIntent(context, triggerReason))
                true
            }.onFailure { err ->
                runCatching {
                    Log.w(TAG, "Unable to start CipherHostService ($triggerReason): ${err.message}")
                }
            }.getOrDefault(false)
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, CipherHostService::class.java))
            }
        }
    }
}
