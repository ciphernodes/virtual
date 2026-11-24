package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ciphernodes.ciphervirtual.monitoring.ResourceTelemetrySnapshot
import com.ciphernodes.ciphervirtual.monitoring.StabilityLevel
import com.ciphernodes.ciphervirtual.security.IntegrityStatusSnapshot
import com.ciphernodes.ciphervirtual.security.IntegrityVerificationState
import com.example.R
import com.example.data.hardware.DeviceProfile
import com.example.data.model.VirtualSpaceEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SecurityDashboard(
    integrityStatus: IntegrityStatusSnapshot,
    resourceTelemetry: ResourceTelemetrySnapshot,
    activeSpace: VirtualSpaceEntity?,
    activeDeviceProfile: DeviceProfile?,
    hostDisplaySummary: String,
    networkSummary: String,
    isTelemetryPaused: Boolean = false,
    onRunIntegrityCheck: () -> Unit,
    onTogglePeriodicIntegrity: (Boolean) -> Unit,
    onRefreshResources: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val (badgeLabel, badgeIcon, badgeContainer, badgeContent) = when (integrityStatus.state) {
        IntegrityVerificationState.VERIFIED -> DashboardBadge(
            "VERIFIED",
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        IntegrityVerificationState.TOKEN_ISSUED -> DashboardBadge(
            "TOKEN ISSUED",
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        IntegrityVerificationState.VERIFYING -> DashboardBadge(
            "VERIFYING",
            Icons.Rounded.Refresh,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        IntegrityVerificationState.CONFIGURATION_REQUIRED -> DashboardBadge(
            "SETUP NEEDED",
            Icons.Rounded.WarningAmber,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        IntegrityVerificationState.VERDICT_FAILED -> DashboardBadge(
            "FAILED",
            Icons.Rounded.WarningAmber,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        IntegrityVerificationState.ERROR -> DashboardBadge(
            "UNVERIFIED",
            Icons.Rounded.WarningAmber,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        IntegrityVerificationState.IDLE -> DashboardBadge(
            "IDLE",
            Icons.Rounded.VerifiedUser,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val (stabilityLabel, stabilityContainer, stabilityContent) = when (resourceTelemetry.stability) {
        StabilityLevel.OPTIMAL -> Triple(
            "OPTIMAL",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        StabilityLevel.MODERATE -> Triple(
            "MODERATE",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        StabilityLevel.HIGH_LOAD -> Triple(
            "HIGH LOAD",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                .testTag("security_dashboard_integrity_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(R.string.play_integrity_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    TelemetryPill(
                        label = badgeLabel,
                        icon = badgeIcon,
                        containerColor = badgeContainer,
                        contentColor = badgeContent
                    )
                }

                Text(
                    text = integrityStatus.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                integrityStatus.lastCheckedAtMillis?.let { checkedAt ->
                    val formattedTime = timeFormatter.format(Date(checkedAt))
                    Text(
                        text = "Last check: $formattedTime • Play Store: ${if (integrityStatus.playStorePresent) "Detected" else "Unavailable"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                integrityStatus.lastTokenPreview?.let { preview ->
                    Text(
                        text = "Token: $preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                integrityStatus.lastVerdict?.let { verdict ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        verdict.appRecognitionVerdict?.let { appVerdict ->
                            TelemetryPill(
                                label = "App: $appVerdict",
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        verdict.deviceRecognitionVerdict?.let { deviceVerdict ->
                            TelemetryPill(
                                label = deviceVerdict,
                                containerColor = if (verdict.verified) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                                contentColor = if (verdict.verified) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text("Periodic Integrity Schedule", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Verify environment every ${integrityStatus.intervalMinutes} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = integrityStatus.isPeriodicEnabled,
                        onCheckedChange = onTogglePeriodicIntegrity,
                        modifier = Modifier.testTag("toggle_periodic_integrity")
                    )
                }

                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRunIntegrityCheck()
                    },
                    enabled = integrityStatus.state != IntegrityVerificationState.VERIFYING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("verify_play_integrity_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.play_integrity_verify_now))
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                .testTag("security_dashboard_resources_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Container Resource Monitor", style = MaterialTheme.typography.titleMedium)
                    }
                    TelemetryPill(
                        label = if (isTelemetryPaused) "PAUSED" else stabilityLabel,
                        icon = Icons.Rounded.Speed,
                        containerColor = stabilityContainer,
                        contentColor = stabilityContent
                    )
                }

                LiveSparklineChart(
                    samples = resourceTelemetry.cpuHistory.ifEmpty { listOf(resourceTelemetry.processCpuPercent) },
                    lineColor = MaterialTheme.colorScheme.primary,
                    unitSuffix = "% CPU",
                    maxValueHint = 25f
                )

                DashboardMetricRow(
                    label = "Device RAM",
                    value = "${resourceTelemetry.systemUsedRamMb} / ${resourceTelemetry.systemTotalRamMb} MB"
                )
                MemoryAllocationBar(
                    appHeapMb = resourceTelemetry.appHeapUsedMb,
                    nativeHeapMb = resourceTelemetry.nativeHeapMb,
                    systemUsedMb = resourceTelemetry.systemUsedRamMb,
                    systemTotalMb = resourceTelemetry.systemTotalRamMb
                )

                if (resourceTelemetry.storageTotalGb > 0f) {
                    val usedGb = (resourceTelemetry.storageTotalGb - resourceTelemetry.storageAvailableGb).coerceAtLeast(0f)
                    DashboardMetricRow(
                        label = "Internal Storage",
                        value = String.format(
                            Locale.US,
                            "%.1f / %.1f GB (%.1f GB free)",
                            usedGb,
                            resourceTelemetry.storageTotalGb,
                            resourceTelemetry.storageAvailableGb
                        )
                    )
                    LinearProgressIndicator(
                        progress = { resourceTelemetry.storageUsedRatio },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryPill(
                        label = "${resourceTelemetry.batteryPercent}% ${if (resourceTelemetry.isCharging) "Charging" else "Battery"}",
                        icon = Icons.Rounded.BatteryChargingFull,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryPill(
                        label = "Thermal: ${resourceTelemetry.thermalLabel}",
                        icon = Icons.Rounded.Thermostat,
                        containerColor = if (resourceTelemetry.thermalLabel == "Nominal") {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (resourceTelemetry.thermalLabel == "Nominal") {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (resourceTelemetry.spaceUsages.isNotEmpty()) {
                    resourceTelemetry.spaceUsages.forEach { usage ->
                        DashboardMetricRow(
                            label = "${usage.spaceName} (${usage.appCount} apps)",
                            value = "${usage.estimatedRamMb} MB • ${String.format(Locale.US, "%.1f%%", usage.cpuSharePercent)} CPU"
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRefreshResources()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("refresh_resource_monitor_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sample RAM & CPU")
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                .testTag("security_dashboard_host_profile_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text("Host & Workspace Profile", style = MaterialTheme.typography.titleMedium)
                }
                DashboardMetricRow(
                    label = "Host Hardware",
                    value = "${Build.MANUFACTURER} ${Build.MODEL}"
                )
                DashboardMetricRow(
                    label = "Android OS",
                    value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                )
                DashboardMetricRow(
                    label = "Host Panel",
                    value = hostDisplaySummary,
                    icon = Icons.Rounded.Storage
                )
                DashboardMetricRow(
                    label = "Network Link",
                    value = networkSummary,
                    icon = Icons.Rounded.Wifi
                )
                DashboardMetricRow(
                    label = "Active Space Preset",
                    value = activeSpace?.let { "${it.name} • ${it.deviceModelPreset} (${it.virtualDpi} dpi)" }
                        ?: "No active space"
                )
                activeDeviceProfile?.let { profile ->
                    DashboardMetricRow(
                        label = "Profile Model",
                        value = "${profile.manufacturer} ${profile.model}"
                    )
                    DashboardMetricRow(
                        label = "Device / Board",
                        value = "${profile.brand}/${profile.device} • ${profile.board} (${profile.hardware})"
                    )
                    DashboardMetricRow(
                        label = "Profile OS & ID",
                        value = "Android ${profile.androidVersion} (API ${profile.sdkInt}) • ${profile.profileId.take(8)}"
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardMetricRow(
    label: String,
    value: String,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class DashboardBadge<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
