package com.ciphernodes.ciphervirtual.security

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.data.model.DevicePermissionItem

class PermissionManager(private val context: Context) {

    val requiredPermissions: List<String>
        get() = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.ANSWER_PHONE_CALLS)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.WRITE_CALL_LOG)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_CALENDAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

    fun getRequiredPermissionItems(): List<DevicePermissionItem> {
        val items = mutableListOf(
            DevicePermissionItem(
                key = "location",
                title = "Location Services",
                description = "Coarse and precise GPS access",
                permissions = listOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                isGranted = hasAny(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ),
            DevicePermissionItem(
                key = "contacts",
                title = "Contacts Directory",
                description = "Read and sync contacts",
                permissions = listOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS
                ),
                isGranted = hasAll(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS
                )
            ),
            DevicePermissionItem(
                key = "phone",
                title = "Phone & Calls",
                description = "Manage calls and phone state",
                permissions = listOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.ANSWER_PHONE_CALLS
                ),
                isGranted = hasAll(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.ANSWER_PHONE_CALLS
                )
            ),
            DevicePermissionItem(
                key = "call_log",
                title = "Call History",
                description = "Read and update call logs",
                permissions = listOf(
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.WRITE_CALL_LOG
                ),
                isGranted = hasAll(
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.WRITE_CALL_LOG
                )
            ),
            DevicePermissionItem(
                key = "sms",
                title = "SMS Messaging",
                description = "Read and send SMS messages",
                permissions = listOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS
                ),
                isGranted = hasAll(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS
                )
            ),
            DevicePermissionItem(
                key = "camera",
                title = "Camera Hardware",
                description = "QR scanning, video calls, and photo capture",
                permissions = listOf(Manifest.permission.CAMERA),
                isGranted = hasAll(Manifest.permission.CAMERA)
            ),
            DevicePermissionItem(
                key = "microphone",
                title = "Microphone Audio",
                description = "Voice chat, audio capture, and calls",
                permissions = listOf(Manifest.permission.RECORD_AUDIO),
                isGranted = hasAll(Manifest.permission.RECORD_AUDIO)
            ),
            DevicePermissionItem(
                key = "calendar",
                title = "Calendar Events",
                description = "Read calendar schedules",
                permissions = listOf(Manifest.permission.READ_CALENDAR),
                isGranted = hasAll(Manifest.permission.READ_CALENDAR)
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            items.add(
                DevicePermissionItem(
                    key = "bluetooth",
                    title = "Nearby Bluetooth",
                    description = "Scan, advertise, and connect Bluetooth devices",
                    permissions = listOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    ),
                    isGranted = hasAll(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    ),
                    minApi = Build.VERSION_CODES.S
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            items.add(
                DevicePermissionItem(
                    key = "notifications",
                    title = "Notifications",
                    description = "Alerts and background status updates",
                    permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                    isGranted = hasAll(Manifest.permission.POST_NOTIFICATIONS),
                    minApi = Build.VERSION_CODES.TIRAMISU
                )
            )
        }

        items.addAll(
            listOf(
                DevicePermissionItem(
                    key = "overlay",
                    title = "Display Over Other Apps",
                    description = "Floating overlay and picture-in-picture access",
                    permissions = listOf(Manifest.permission.SYSTEM_ALERT_WINDOW),
                    isGranted = canDrawOverlays(),
                    isSpecialAccess = true
                ),
                DevicePermissionItem(
                    key = "write_settings",
                    title = "Modify System Settings",
                    description = "Adjust display and device configuration settings",
                    permissions = listOf(Manifest.permission.WRITE_SETTINGS),
                    isGranted = canWriteSystemSettings(),
                    isSpecialAccess = true
                ),
                DevicePermissionItem(
                    key = "usage_stats",
                    title = "App Usage Access",
                    description = "Inspect active app session telemetry",
                    permissions = listOf(Manifest.permission.PACKAGE_USAGE_STATS),
                    isGranted = hasUsageStatsAccess(),
                    isSpecialAccess = true
                ),
                DevicePermissionItem(
                    key = "install_packages",
                    title = "Install Unknown Apps",
                    description = "Allow launching package installation flows",
                    permissions = listOf(Manifest.permission.REQUEST_INSTALL_PACKAGES),
                    isGranted = canRequestPackageInstalls(),
                    isSpecialAccess = true
                ),
                DevicePermissionItem(
                    key = "battery",
                    title = "Unrestricted Battery",
                    description = "Keep background monitoring active without throttling",
                    permissions = listOf(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS),
                    isGranted = isBatteryUnrestricted(),
                    isSpecialAccess = true
                )
            )
        )

        return items
    }

    fun areAllRequiredPermissionsGranted(): Boolean {
        return getRequiredPermissionItems().all { it.isGranted }
    }

    fun getMissingRuntimePermissions(): Array<String> {
        return getRequiredPermissionItems()
            .filter { !it.isGranted && !it.isSpecialAccess }
            .flatMap { it.permissions }
            .distinct()
            .toTypedArray()
    }

    fun getNextMissingSpecialAccessKey(): String? {
        return getRequiredPermissionItems()
            .firstOrNull { !it.isGranted && it.isSpecialAccess }
            ?.key
    }

    fun verifyPermissionsOrExit(activity: Activity?, hostContext: Context = context): Boolean {
        if (!areAllRequiredPermissionsGranted()) {
            handlePermissionDenialAndExit(activity, hostContext)
            return false
        }
        return true
    }

    fun canDrawOverlays(): Boolean {
        return runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
    }

    fun canWriteSystemSettings(): Boolean {
        return runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
    }

    fun canRequestPackageInstalls(): Boolean {
        return runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    }

    fun hasUsageStatsAccess(): Boolean {
        return runCatching {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    fun isBatteryUnrestricted(): Boolean {
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }.getOrDefault(false)
    }

    fun hasStorageFolderGrant(): Boolean {
        return runCatching {
            context.contentResolver.persistedUriPermissions.any {
                it.isReadPermission || it.isWritePermission
            }
        }.getOrDefault(false)
    }

    fun persistStorageTreeUri(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
    }

    fun openSpecialAccessSettings(key: String, hostContext: Context) {
        when (key) {
            "overlay" -> openOverlaySettings(hostContext)
            "write_settings" -> openWriteSettings(hostContext)
            "usage_stats" -> openUsageAccessSettings(hostContext)
            "install_packages" -> openInstallUnknownAppsSettings(hostContext)
            "battery" -> openBatteryOptimizationPrompt(hostContext)
            else -> openAppSettings(hostContext)
        }
    }

    fun openOverlaySettings(hostContext: Context) {
        launchPackageSettingsIntent(
            hostContext = hostContext,
            action = Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            fallbackAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        )
    }

    fun openWriteSettings(hostContext: Context) {
        launchPackageSettingsIntent(
            hostContext = hostContext,
            action = Settings.ACTION_MANAGE_WRITE_SETTINGS,
            fallbackAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        )
    }

    fun openUsageAccessSettings(hostContext: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { hostContext.startActivity(intent) }
            .onFailure { openAppSettings(hostContext) }
    }

    fun openInstallUnknownAppsSettings(hostContext: Context) {
        launchPackageSettingsIntent(
            hostContext = hostContext,
            action = Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            fallbackAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        )
    }

    @SuppressLint("BatteryLife")
    fun openBatteryOptimizationPrompt(hostContext: Context) {
        val directIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${hostContext.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching {
            hostContext.startActivity(directIntent)
        }.onFailure {
            runCatching { hostContext.startActivity(fallbackIntent) }
        }
    }

    fun openAppSettings(hostContext: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", hostContext.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { hostContext.startActivity(intent) }
    }

    fun handlePermissionDenialAndExit(activity: Activity?, hostContext: Context) {
        Toast.makeText(
            hostContext,
            "All required permissions must be allowed to use Cipher Virtual.",
            Toast.LENGTH_LONG
        ).show()
        activity?.finishAffinity()
    }

    fun hasAll(vararg permissions: String): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasAny(vararg permissions: String): Boolean {
        return permissions.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun launchPackageSettingsIntent(
        hostContext: Context,
        action: String,
        fallbackAction: String
    ) {
        val direct = Intent(action, Uri.parse("package:${hostContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(fallbackAction, Uri.fromParts("package", hostContext.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { hostContext.startActivity(direct) }
            .onFailure { runCatching { hostContext.startActivity(fallback) } }
    }
}
