package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.hardware.DeviceProfile
import com.example.data.hardware.DeviceProfileManager
import com.example.data.model.VirtualSpaceEntity
import com.example.ui.OctoUiState
import com.example.ui.components.SecurityDashboard
import com.example.ui.theme.spaceAccentColors
import kotlin.math.roundToInt

@Composable
fun SandboxConfigScreen(
    uiState: OctoUiState,
    onUpdateSpace: (VirtualSpaceEntity) -> Unit,
    onSelectDeviceProfile: (VirtualSpaceEntity, DeviceProfile) -> Unit,
    onSaveCustomProfile: (VirtualSpaceEntity, DeviceProfile) -> Unit,
    onDeleteCustomProfile: (VirtualSpaceEntity?, String) -> Unit,
    onResetSpaceDefaults: (VirtualSpaceEntity) -> Unit,
    onToggleTheme: () -> Unit,
    onRunIntegrityCheck: () -> Unit,
    onTogglePeriodicIntegrity: (Boolean) -> Unit,
    onRefreshResources: () -> Unit,
    onVaultUriPicked: (Uri?) -> Unit = {},
    onRemoveVaultUri: (Uri) -> Unit = {},
    onStorageTreePicked: (Uri?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeSpace = uiState.activeSpace
    val spaceAccent = spaceAccentColors(activeSpace?.accentIndex ?: 0)
    val haptics = LocalHapticFeedback.current
    var showCustomProfileDialog by remember { mutableStateOf(false) }
    var editingCustomProfile by remember { mutableStateOf<DeviceProfile?>(null) }

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
                .testTag("sandbox_config_list")
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
                            text = stringResource(
                                R.string.sandbox_subtitle_format,
                                activeSpace?.name ?: stringResource(R.string.space_fallback_name)
                            ),
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
                            contentDescription = stringResource(R.string.cd_toggle_theme)
                        )
                    }
                }
            }

            if (activeSpace != null) {
                item {
                    var dpiDraft by remember(activeSpace.id, activeSpace.virtualDpi) {
                        mutableIntStateOf(activeSpace.virtualDpi.coerceIn(240, 640))
                    }
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
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.DeveloperBoard, contentDescription = null, tint = spaceAccent.accent)
                                    Text(stringResource(R.string.hardware_preset_title), style = MaterialTheme.typography.titleMedium)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        editingCustomProfile = null
                                        showCustomProfileDialog = true
                                    },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("new_custom_profile_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.custom_action))
                                }
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.testTag("device_presets_row")
                            ) {
                                items(uiState.availableDeviceProfiles, key = { it.profileId }) { profile ->
                                    val selected = activeSpace.deviceModelPreset.equals(profile.presetKey, ignoreCase = true)
                                    if (!profile.isCustom) {
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onSelectDeviceProfile(activeSpace, profile)
                                            },
                                            label = { Text(profile.displayName) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = spaceAccent.container,
                                                selectedLabelColor = spaceAccent.onContainer
                                            ),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("device_preset_${profile.presetKey}")
                                        )
                                    } else {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = if (selected) spaceAccent.container else MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.border(
                                                width = 1.dp,
                                                color = if (selected) spaceAccent.accent else MaterialTheme.colorScheme.outlineVariant,
                                                shape = MaterialTheme.shapes.small
                                            )
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(MaterialTheme.shapes.small)
                                                        .clickable {
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            onSelectDeviceProfile(activeSpace, profile)
                                                        }
                                                        .minimumInteractiveComponentSize()
                                                        .padding(start = 12.dp, end = 6.dp)
                                                        .testTag("device_preset_${profile.presetKey}")
                                                ) {
                                                    if (selected) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = null,
                                                            tint = spaceAccent.onContainer,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                    }
                                                    Text(
                                                        text = profile.displayName,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = if (selected) spaceAccent.onContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        editingCustomProfile = profile
                                                        showCustomProfileDialog = true
                                                    },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .testTag("edit_custom_profile_${profile.profileId}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Edit,
                                                        contentDescription = stringResource(R.string.cd_edit_profile_format, profile.displayName),
                                                        tint = if (selected) spaceAccent.onContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onDeleteCustomProfile(activeSpace, profile.profileId) },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .testTag("delete_custom_profile_${profile.profileId}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = stringResource(R.string.cd_delete_profile_format, profile.displayName),
                                                        tint = if (selected) spaceAccent.onContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.virtual_display_dpi_label), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = stringResource(R.string.dpi_value_format, dpiDraft),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = spaceAccent.accent
                                    )
                                }
                                Slider(
                                    value = dpiDraft.toFloat(),
                                    onValueChange = {
                                        dpiDraft = ((it / 20f).roundToInt() * 20).coerceIn(240, 640)
                                    },
                                    onValueChangeFinished = {
                                        if (dpiDraft != activeSpace.virtualDpi) {
                                            onUpdateSpace(activeSpace.copy(virtualDpi = dpiDraft))
                                        }
                                    },
                                    valueRange = 240f..640f,
                                    modifier = Modifier.testTag("sandbox_dpi_slider")
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
                                title = stringResource(R.string.isolated_storage_title),
                                subtitle = stringResource(R.string.isolated_storage_subtitle),
                                checked = activeSpace.isolatedStorage,
                                testTag = "toggle_isolated_storage",
                                onCheckedChange = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateSpace(activeSpace.copy(isolatedStorage = it))
                                }
                            )
                            SandboxToggleRow(
                                icon = Icons.Rounded.Speed,
                                title = stringResource(R.string.fps_hud_title),
                                subtitle = stringResource(R.string.fps_hud_subtitle),
                                checked = activeSpace.frameRateHud,
                                testTag = "toggle_fps_hud",
                                onCheckedChange = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateSpace(activeSpace.copy(frameRateHud = it))
                                }
                            )
                            SandboxToggleRow(
                                icon = Icons.Rounded.VerifiedUser,
                                title = stringResource(R.string.permission_bridge_title),
                                subtitle = stringResource(R.string.permission_bridge_subtitle),
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
                                Text(stringResource(R.string.reset_space_defaults_format, activeSpace.name))
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
                    activeDeviceProfile = uiState.activeDeviceProfile,
                    hostDisplaySummary = uiState.deviceBridge.hostDisplaySummary,
                    networkSummary = uiState.deviceBridge.networkSummary,
                    isTelemetryPaused = uiState.isTelemetryPausedInBackground,
                    onRunIntegrityCheck = onRunIntegrityCheck,
                    onTogglePeriodicIntegrity = onTogglePeriodicIntegrity,
                    onRefreshResources = onRefreshResources,
                    attachedVaultUris = uiState.attachedVaultUris,
                    hasStorageFolderGrant = uiState.deviceBridge.hasStorageFolderGrant,
                    onVaultUriPicked = onVaultUriPicked,
                    onRemoveVaultUri = onRemoveVaultUri,
                    onStorageTreePicked = onStorageTreePicked
                )
            }
        }
    }

    if (showCustomProfileDialog && activeSpace != null) {
        val base = editingCustomProfile ?: uiState.activeDeviceProfile ?: DeviceProfileManager.pixel8()
        CustomDeviceProfileDialog(
            baseProfile = base,
            isEditing = editingCustomProfile != null,
            onSave = { customProfile ->
                onSaveCustomProfile(activeSpace, customProfile)
                showCustomProfileDialog = false
                editingCustomProfile = null
            },
            onDismiss = {
                showCustomProfileDialog = false
                editingCustomProfile = null
            }
        )
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
