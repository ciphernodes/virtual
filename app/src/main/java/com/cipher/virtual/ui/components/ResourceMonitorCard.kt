package com.cipher.virtual.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cipher.virtual.R
import com.cipher.virtual.monitoring.ResourceTelemetrySnapshot
import com.cipher.virtual.monitoring.StabilityLevel
import com.cipher.virtual.ui.theme.NeuCard
import com.cipher.virtual.ui.theme.NeuTonalButton
import com.cipher.virtual.ui.theme.NeuWell

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

    NeuCard(
        cornerRadius = 24.dp,
        elevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("security_dashboard_resources_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
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
                NeuWell(
                    cornerRadius = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        resourceTelemetry.spaceUsages.forEach { usage ->
                            DashboardMetricRow(
                                label = stringResource(R.string.space_usage_label_format, usage.spaceName, usage.appCount),
                                value = stringResource(R.string.space_usage_value_format, usage.estimatedRamMb, usage.cpuSharePercent)
                            )
                        }
                    }
                }
            }

            NeuTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRefreshResources()
                },
                accentColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("refresh_resource_monitor_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.sample_ram_cpu_action))
            }
        }
    }
}
