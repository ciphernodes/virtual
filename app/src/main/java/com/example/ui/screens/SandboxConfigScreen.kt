package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.VirtualSpaceEntity
import com.example.ui.OctoUiState
import com.example.ui.components.SecurityDashboard
import com.example.ui.theme.spaceAccentColors
import kotlin.math.roundToInt

private val DEVICE_PRESETS = listOf(
    "ROG Phone 8 Pro",
    "Pixel 9 Pro",
    "Galaxy S24 Ultra",
    "RedMagic 9S Pro",
    "Native Hardware"
)

@Composable
fun SandboxConfigScreen(
    uiState: OctoUiState,
    onUpdateSpace: (VirtualSpaceEntity) -> Unit,
    onResetSpaceDefaults: (VirtualSpaceEntity) -> Unit,
    onToggleTheme: () -> Unit,
    onRunIntegrityCheck: () -> Unit,
    onTogglePeriodicIntegrity: (Boolean) -> Unit,
    onRefreshResources: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSpace = uiState.activeSpace
    val spaceAccent = spaceAccentColors(activeSpace?.accentIndex ?: 0)
    val haptics = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sandbox_settings_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Configure ${activeSpace?.name ?: "Space"} environment & runtime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleTheme()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.darkThemeEnabled) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Toggle theme mode"
                        )
                    }
                }
            }

            if (activeSpace != null) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                spaceAccent.accent.copy(alpha = 0.35f),
                                MaterialTheme.shapes.large
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.DeveloperBoard, contentDescription = null, tint = spaceAccent.accent)
                                Text("Display Profile Preset", style = MaterialTheme.typography.titleMedium)
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(DEVICE_PRESETS) { preset ->
                                    FilterChip(
                                        selected = activeSpace.deviceModelPreset == preset,
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onUpdateSpace(activeSpace.copy(deviceModelPreset = preset))
                                        },
                                        label = { Text(preset) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = spaceAccent.container,
                                            selectedLabelColor = spaceAccent.onContainer
                                        ),
                                        modifier = Modifier.testTag("device_preset_$preset")
                                    )
                                }
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Virtual Display DPI", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${activeSpace.virtualDpi} dpi",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = spaceAccent.accent
                                    )
                                }
                                Slider(
                                    value = activeSpace.virtualDpi.toFloat(),
                                    onValueChange = {
                                        val snapped = (it / 20f).roundToInt() * 20
                                        if (snapped != activeSpace.virtualDpi) {
                                            onUpdateSpace(activeSpace.copy(virtualDpi = snapped))
                                        }
                                    },
                                    valueRange = 240f..640f
                                )
                            }
                        }
                    }
                }

                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SandboxToggleRow(
                                icon = Icons.Rounded.Security,
                                title = "Isolated Storage Profile",
                                subtitle = "Separate sandbox metadata & per-instance preferences",
                                checked = activeSpace.isolatedStorage,
                                testTag = "toggle_isolated_storage",
                                onCheckedChange = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateSpace(activeSpace.copy(isolatedStorage = it))
                                }
                            )
                            SandboxToggleRow(
                                icon = Icons.Rounded.Speed,
                                title = "Frame-Rate Telemetry HUD",
                                subtitle = "Display live CPU sparkline & stability telemetry on Spaces",
                                checked = activeSpace.frameRateHud,
                                testTag = "toggle_fps_hud",
                                onCheckedChange = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateSpace(activeSpace.copy(frameRateHud = it))
                                }
                            )
                            SandboxToggleRow(
                                icon = Icons.Rounded.VerifiedUser,
                                title = "Device Permission Bridge",
                                subtitle = "Expose granted Location, Contacts & Media access to this space",
                                checked = activeSpace.permissionBridgeEnabled,
                                testTag = "toggle_permission_bridge",
                                onCheckedChange = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateSpace(activeSpace.copy(permissionBridgeEnabled = it))
                                }
                            )
                            FilledTonalButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onResetSpaceDefaults(activeSpace)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("reset_space_defaults_button")
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reset ${activeSpace.name} Defaults")
                            }
                        }
                    }
                }
            }

            item {
                SecurityDashboard(
                    integrityStatus = uiState.integrityStatus,
                    resourceTelemetry = uiState.resourceTelemetry,
                    activeSpace = activeSpace,
                    hostDisplaySummary = uiState.deviceBridge.hostDisplaySummary,
                    networkSummary = uiState.deviceBridge.networkSummary,
                    onRunIntegrityCheck = onRunIntegrityCheck,
                    onTogglePeriodicIntegrity = onTogglePeriodicIntegrity,
                    onRefreshResources = onRefreshResources
                )
            }
        }
    }
}

@Composable
private fun SandboxToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
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
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
