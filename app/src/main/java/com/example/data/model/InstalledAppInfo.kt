package com.example.data.model

import androidx.compose.ui.graphics.ImageBitmap

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val isGame: Boolean,
    val icon: ImageBitmap?,
    val isSystemApp: Boolean = false,
    val targetSdk: Int = 0,
    val apkSizeMb: Float = 0f
)

data class DevicePermissionItem(
    val key: String,
    val title: String,
    val description: String,
    val permissions: List<String>,
    val isGranted: Boolean,
    val minApi: Int = 26
)

data class DeviceBridgeSnapshot(
    val initialized: Boolean = false,
    val permissions: List<DevicePermissionItem> = emptyList(),
    val grantedCount: Int = 0,
    val totalCount: Int = 0,
    val allRequiredGranted: Boolean = false,
    val isBatteryUnrestricted: Boolean = false,
    val hasStorageFolderGrant: Boolean = false,
    val contactsSummary: String = "Permission required",
    val locationSummary: String = "Permission required",
    val calendarSummary: String = "Permission required",
    val networkSummary: String = "Inspecting network…",
    val hostDisplaySummary: String = "Standard display"
)
