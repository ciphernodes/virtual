package com.example.ui.components

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PermMedia
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.hardware.DeviceProfile
import com.example.data.model.VirtualSpaceEntity
import com.example.monitoring.ResourceTelemetrySnapshot
import com.example.monitoring.StabilityLevel
import com.example.security.IntegrityStatusSnapshot
import com.example.security.IntegrityVerificationState
import androidx.compose.ui.draw.clip
import com.example.ui.theme.LocalNeomorphicColors
import com.example.ui.theme.neomorphic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlayIntegrityCard(
    integrityStatus: IntegrityStatusSnapshot,
    onRunIntegrityCheck: () -> Unit,
    onTogglePeriodicIntegrity: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val badge = when (integrityStatus.state) {
        IntegrityVerificationState.VERIFIED -> DashboardBadge(
            stringResource(R.string.badge_verified),
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        IntegrityVerificationState.TOKEN_ISSUED -> DashboardBadge(
            stringResource(R.string.badge_token_issued),
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        IntegrityVerificationState.VERIFYING -> DashboardBadge(
            stringResource(R.string.badge_verifying),
            Icons.Rounded.Refresh,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        IntegrityVerificationState.CONFIGURATION_REQUIRED -> DashboardBadge(
            stringResource(R.string.badge_setup_needed),
            Icons.Rounded.WarningAmber,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        IntegrityVerificationState.VERDICT_FAILED -> DashboardBadge(
            stringResource(R.string.badge_failed),
            Icons.Rounded.WarningAmber,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        IntegrityVerificationState.ERROR -> DashboardBadge(
            stringResource(R.string.badge_unverified),
            Icons.Rounded.WarningAmber,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        IntegrityVerificationState.IDLE -> DashboardBadge(
            stringResource(R.string.badge_idle),
            Icons.Rounded.VerifiedUser,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val neoColors = LocalNeomorphicColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .neomorphic(
                color = MaterialTheme.colorScheme.surface,
                lightShadowColor = neoColors.lightShadow,
                darkShadowColor = neoColors.darkShadow,
                cornerRadius = 22.dp,
                elevation = 6.dp
            )
            .clip(MaterialTheme.shapes.large)
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
                    label = badge.label,
                    icon = badge.icon,
                    containerColor = badge.containerColor,
                    contentColor = badge.contentColor
                )
            }

            Text(
                text = integrityStatus.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            integrityStatus.lastCheckedAtMillis?.let { checkedAt ->
                val formattedTime = timeFormatter.format(Date(checkedAt))
                val storeLabel = stringResource(
                    if (integrityStatus.playStorePresent) R.string.store_detected else R.string.store_unavailable
                )
                Text(
                    text = stringResource(R.string.integrity_last_check_format, formattedTime, storeLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            integrityStatus.lastTokenPreview?.let { preview ->
                Text(
                    text = stringResource(R.string.integrity_token_format, preview),
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
                            label = stringResource(R.string.integrity_app_verdict_format, appVerdict),
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
                        Text(
                            text = stringResource(R.string.periodic_integrity_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.periodic_integrity_subtitle_format, integrityStatus.intervalMinutes),
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
}

@Composable
fun ResourceMonitorCard(
    resourceTelemetry: ResourceTelemetrySnapshot,
    isTelemetryPaused: Boolean,
    onRefreshResources: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val (stabilityLabel, stabilityContainer, stabilityContent) = when (resourceTelemetry.stability) {
        StabilityLevel.OPTIMAL -> Triple(
            stringResource(R.string.stability_optimal),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        StabilityLevel.MODERATE -> Triple(
            stringResource(R.string.stability_moderate),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        StabilityLevel.HIGH_LOAD -> Triple(
            stringResource(R.string.stability_high_load),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    val neoColors = LocalNeomorphicColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .neomorphic(
                color = MaterialTheme.colorScheme.surface,
                lightShadowColor = neoColors.lightShadow,
                darkShadowColor = neoColors.darkShadow,
                cornerRadius = 22.dp,
                elevation = 6.dp
            )
            .clip(MaterialTheme.shapes.large)
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
                    Text(stringResource(R.string.resource_monitor_title), style = MaterialTheme.typography.titleMedium)
                }
                TelemetryPill(
                    label = if (isTelemetryPaused) stringResource(R.string.stability_paused) else stabilityLabel,
                    icon = Icons.Rounded.Speed,
                    containerColor = stabilityContainer,
                    contentColor = stabilityContent
                )
            }

            LiveSparklineChart(
                samples = resourceTelemetry.cpuHistory.ifEmpty { listOf(resourceTelemetry.processCpuPercent) },
                lineColor = MaterialTheme.colorScheme.primary,
                unitSuffix = stringResource(R.string.unit_cpu_percent),
                maxValueHint = 25f
            )

            DashboardMetricRow(
                label = stringResource(R.string.device_ram_label),
                value = stringResource(
                    R.string.ram_usage_format,
                    resourceTelemetry.systemUsedRamMb,
                    resourceTelemetry.systemTotalRamMb
                )
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
                    label = stringResource(R.string.internal_storage_label),
                    value = stringResource(
                        R.string.storage_usage_format,
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
                val batteryStatus = stringResource(
                    if (resourceTelemetry.isCharging) R.string.battery_charging else R.string.battery_discharging
                )
                TelemetryPill(
                    label = stringResource(R.string.battery_format, resourceTelemetry.batteryPercent, batteryStatus),
                    icon = Icons.Rounded.BatteryChargingFull,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TelemetryPill(
                    label = stringResource(R.string.thermal_format, resourceTelemetry.thermalLabel),
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
                        label = stringResource(R.string.space_usage_label_format, usage.spaceName, usage.appCount),
                        value = stringResource(R.string.space_usage_value_format, usage.estimatedRamMb, usage.cpuSharePercent)
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
                Text(stringResource(R.string.sample_ram_cpu_action))
            }
        }
    }
}

@Composable
fun HostProfileCard(
    activeSpace: VirtualSpaceEntity?,
    activeDeviceProfile: DeviceProfile?,
    hostDisplaySummary: String,
    networkSummary: String,
    modifier: Modifier = Modifier
) {
    val neoColors = LocalNeomorphicColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .neomorphic(
                color = MaterialTheme.colorScheme.surface,
                lightShadowColor = neoColors.lightShadow,
                darkShadowColor = neoColors.darkShadow,
                cornerRadius = 22.dp,
                elevation = 6.dp
            )
            .clip(MaterialTheme.shapes.large)
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
                Text(stringResource(R.string.host_workspace_profile_title), style = MaterialTheme.typography.titleMedium)
            }
            DashboardMetricRow(
                label = stringResource(R.string.host_hardware_label),
                value = "${Build.MANUFACTURER} ${Build.MODEL}"
            )
            DashboardMetricRow(
                label = stringResource(R.string.android_os_label),
                value = stringResource(R.string.android_os_value_format, Build.VERSION.RELEASE, Build.VERSION.SDK_INT)
            )
            DashboardMetricRow(
                label = stringResource(R.string.host_panel_label),
                value = hostDisplaySummary,
                icon = Icons.Rounded.Storage
            )
            DashboardMetricRow(
                label = stringResource(R.string.network_link_label),
                value = networkSummary,
                icon = Icons.Rounded.Wifi
            )
            DashboardMetricRow(
                label = stringResource(R.string.active_space_preset_label),
                value = activeSpace?.let {
                    stringResource(R.string.space_preset_value_format, it.name, it.deviceModelPreset, it.virtualDpi)
                } ?: stringResource(R.string.no_active_space)
            )
            activeDeviceProfile?.let { profile ->
                DashboardMetricRow(
                    label = stringResource(R.string.profile_model_label),
                    value = "${profile.manufacturer} ${profile.model}"
                )
                DashboardMetricRow(
                    label = stringResource(R.string.device_board_label),
                    value = "${profile.brand}/${profile.device} • ${profile.board} (${profile.hardware})"
                )
                DashboardMetricRow(
                    label = stringResource(R.string.profile_os_id_label),
                    value = stringResource(
                        R.string.profile_os_id_value_format,
                        profile.androidVersion,
                        profile.sdkInt,
                        profile.profileId.take(8)
                    )
                )
            }
        }
    }
}

@Composable
fun VirtualVaultCard(
    attachedVaultUris: List<Uri>,
    hasStorageFolderGrant: Boolean,
    onVaultUriPicked: (Uri?) -> Unit,
    onRemoveVaultUri: (Uri) -> Unit,
    onStorageTreePicked: (Uri?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        onVaultUriPicked(uri)
    }
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        onStorageTreePicked(uri)
    }

    val neoColors = LocalNeomorphicColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .neomorphic(
                color = MaterialTheme.colorScheme.surface,
                lightShadowColor = neoColors.lightShadow,
                darkShadowColor = neoColors.darkShadow,
                cornerRadius = 22.dp,
                elevation = 6.dp
            )
            .clip(MaterialTheme.shapes.large)
            .testTag("virtual_vault_card")
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
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.virtual_vault_title), style = MaterialTheme.typography.titleMedium)
                }
                TelemetryPill(
                    label = stringResource(
                        if (hasStorageFolderGrant) R.string.vault_storage_granted_badge
                        else R.string.vault_storage_not_granted_badge
                    ),
                    containerColor = if (hasStorageFolderGrant) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (hasStorageFolderGrant) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.testTag(
                        if (hasStorageFolderGrant) "vault_storage_granted_badge"
                        else "vault_storage_not_granted_badge"
                    )
                )
            }

            Text(
                text = stringResource(R.string.virtual_vault_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("vault_import_media_button")
                ) {
                    Icon(Icons.Rounded.PermMedia, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.vault_import_media_action))
                }
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        folderPickerLauncher.launch(null)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("vault_select_folder_button")
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.vault_grant_folder_action))
                }
            }

            Text(
                text = stringResource(R.string.vault_attachments_header_format, attachedVaultUris.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (attachedVaultUris.isEmpty()) {
                Text(
                    text = stringResource(R.string.vault_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    attachedVaultUris.forEachIndexed { index, uri ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: uri.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onRemoveVaultUri(uri) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("remove_vault_uri_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_remove_vault_uri),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
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

private data class DashboardBadge(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color
)
