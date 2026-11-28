package com.cipher.virtual.data.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.Display
import com.cipher.virtual.data.model.DeviceBridgeSnapshot
import com.cipher.virtual.security.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

class DeviceBridgeManager(
    private val context: Context,
    val permissionManager: PermissionManager = PermissionManager(context)
) {

    suspend fun inspectDeviceBridge(): DeviceBridgeSnapshot = withContext(Dispatchers.IO) {
        val perms = permissionManager.getRequiredPermissionItems()
        val grantedCount = perms.count { it.isGranted }
        val allGranted = perms.all { it.isGranted }
        val batteryUnrestricted = permissionManager.isBatteryUnrestricted()
        val storageGranted = permissionManager.hasStorageFolderGrant()
        val contactsSummary = readContactsSummary()
        val locationSummary = readLocationSummary()
        val calendarSummary = readCalendarSummary()
        val networkSummary = readNetworkSummary()
        val hostDisplaySummary = readHostDisplaySummary()
        DeviceBridgeSnapshot(
            initialized = true,
            permissions = perms,
            grantedCount = grantedCount,
            totalCount = perms.size,
            allRequiredGranted = allGranted,
            isBatteryUnrestricted = batteryUnrestricted,
            hasStorageFolderGrant = storageGranted,
            contactsSummary = contactsSummary,
            locationSummary = locationSummary,
            calendarSummary = calendarSummary,
            networkSummary = networkSummary,
            hostDisplaySummary = hostDisplaySummary
        )
    }

    fun getMissingRuntimePermissions(): Array<String> =
        permissionManager.getMissingRuntimePermissions()

    fun persistStorageTreeUri(uri: Uri) =
        permissionManager.persistStorageTreeUri(uri)

    private fun readContactsSummary(): String {
        if (!permissionManager.hasAll(Manifest.permission.READ_CONTACTS)) {
            return "Contacts permission not granted"
        }
        return runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                null
            )?.use { cursor ->
                "${cursor.count} contacts accessible"
            } ?: "0 contacts accessible"
        }.getOrElse { "Unavailable on this device" }
    }

    private fun readCalendarSummary(): String {
        if (!permissionManager.hasAll(Manifest.permission.READ_CALENDAR)) {
            return "Calendar permission not granted"
        }
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                null,
                null,
                null
            )?.use { cursor ->
                "${cursor.count} calendars synced"
            } ?: "0 calendars synced"
        }.getOrElse { "Unavailable on this device" }
    }

    private fun readNetworkSummary(): String {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "Network service unavailable"
            val network = cm.activeNetwork ?: return "Offline"
            val caps = cm.getNetworkCapabilities(network) ?: return "Offline"
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "Connected"
            }
            val validated = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                "Validated"
            } else {
                "Limited"
            }
            val metered = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                "Unmetered"
            } else {
                "Metered"
            }
            "$transport • $validated • $metered"
        }.getOrElse { "Network status unavailable" }
    }

    private fun readHostDisplaySummary(): String {
        return runCatching {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            val metrics = context.resources.displayMetrics
            val refreshHz = display?.refreshRate?.roundToInt() ?: 60
            val widthPx = display?.mode?.physicalWidth ?: metrics.widthPixels
            val heightPx = display?.mode?.physicalHeight ?: metrics.heightPixels
            "${widthPx}×${heightPx} • ${refreshHz} Hz • ${metrics.densityDpi} dpi"
        }.getOrElse { "Standard display" }
    }

    @SuppressLint("MissingPermission")
    private fun readLocationSummary(): String {
        val hasFine = permissionManager.hasAll(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = permissionManager.hasAll(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFine && !hasCoarse) {
            return "Location permission not granted"
        }
        return runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return "Location service unavailable"
            val providers = lm.getProviders(true)
            val lastLoc = providers.firstNotNullOfOrNull { provider ->
                runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            }
            if (lastLoc != null) {
                String.format(
                    Locale.US,
                    "%.4f, %.4f (%s)",
                    lastLoc.latitude,
                    lastLoc.longitude,
                    lastLoc.provider ?: "gps"
                )
            } else if (providers.isNotEmpty()) {
                "Active providers: ${providers.joinToString(", ")}"
            } else {
                "Location enabled (no cached fix yet)"
            }
        }.getOrElse { "Location status unavailable" }
    }
}
