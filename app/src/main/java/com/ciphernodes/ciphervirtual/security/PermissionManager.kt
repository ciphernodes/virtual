package com.ciphernodes.ciphervirtual.security

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
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
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_CALENDAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
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
                description = "Coarse & precise GPS location access for virtual space apps",
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
                description = "Read & sync device contacts inside dual account instances",
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
                description = "Read calendar schedules for dual work profiles",
                permissions = listOf(Manifest.permission.READ_CALENDAR),
                isGranted = hasAll(Manifest.permission.READ_CALENDAR)
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            items.add(
                DevicePermissionItem(
                    key = "bluetooth",
                    title = "Nearby Bluetooth",
                    description = "Connect audio & accessories (Android 12–16)",
                    permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT),
                    isGranted = hasAll(Manifest.permission.BLUETOOTH_CONNECT),
                    minApi = Build.VERSION_CODES.S
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            items.add(
                DevicePermissionItem(
                    key = "notifications",
                    title = "Notifications",
                    description = "Virtual space alerts & background status (Android 13–16)",
                    permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                    isGranted = hasAll(Manifest.permission.POST_NOTIFICATIONS),
                    minApi = Build.VERSION_CODES.TIRAMISU
                )
            )
        }

        return items
    }

    fun areAllRequiredPermissionsGranted(): Boolean {
        return getRequiredPermissionItems().all { it.isGranted }
    }

    fun getMissingRuntimePermissions(): Array<String> {
        return getRequiredPermissionItems()
            .filter { !it.isGranted }
            .flatMap { it.permissions }
            .distinct()
            .toTypedArray()
    }

    fun verifyPermissionsOrExit(activity: Activity?, hostContext: Context = context): Boolean {
        val missing = getMissingRuntimePermissions()
        if (missing.isNotEmpty() || !areAllRequiredPermissionsGranted()) {
            handlePermissionDenialAndExit(activity, hostContext)
            return false
        }
        return true
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
}
