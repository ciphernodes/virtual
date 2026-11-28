package com.cipher.virtual.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground service hosting virtualized application worker processes (:w0 through :w7)
 * with a standard IBinder implementation for process slot management and instance containment.
 */
class VirtualProcessHostService : Service() {

    private val binder = VirtualProcessBinder()
    private val activeWorkerSlots = ConcurrentHashMap<String, String>() // instanceId -> workerSlot

    inner class VirtualProcessBinder : Binder() {
        fun getService(): VirtualProcessHostService = this@VirtualProcessHostService

        fun allocateWorkerSlot(instanceId: String, preferredSlot: String? = null): String {
            return activeWorkerSlots.getOrPut(instanceId) {
                preferredSlot ?: findAvailableSlot()
            }
        }

        fun releaseWorkerSlot(instanceId: String) {
            activeWorkerSlots.remove(instanceId)
        }

        fun getActiveSlots(): Map<String, String> = activeWorkerSlots.toMap()

        private fun findAvailableSlot(): String {
            val usedSlots = activeWorkerSlots.values.toSet()
            for (i in 0..7) {
                val slot = ":w$i"
                if (slot !in usedSlots) return slot
            }
            return ":w0"
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), fgsType)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "virtual_process_host_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(com.cipher.virtual.R.string.virtual_process_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(com.cipher.virtual.R.string.virtual_process_channel_description)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(com.cipher.virtual.R.string.virtual_process_notification_title))
            .setContentText(getString(com.cipher.virtual.R.string.virtual_process_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 2048
    }
}
