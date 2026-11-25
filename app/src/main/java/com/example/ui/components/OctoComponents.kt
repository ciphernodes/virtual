package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.hardware.DeviceProfile
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import com.example.ui.theme.spaceAccentColors
import kotlinx.coroutines.delay

@Composable
fun AppIconBadge(
    appName: String,
    icon: ImageBitmap?,
    size: Dp = 48.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = stringResource(R.string.cd_app_icon),
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(14.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.28f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = appName.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TelemetryPill(
    label: String,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VirtualLaunchSessionDialog(
    app: VirtualAppEntity,
    space: VirtualSpaceEntity?,
    activeDeviceProfile: DeviceProfile?,
    appIcon: ImageBitmap?,
    hostDisplaySummary: String,
    hasWorkProfile: Boolean,
    onChangePerformanceMode: (String) -> Unit,
    onRenameInstance: (String) -> Unit,
    onCloneInstance: () -> Unit,
    onInspectDetails: () -> Unit,
    onLaunchNow: (preferWorkProfile: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val perfModes = listOf("Balanced", "Turbo", "Battery")
    val haptics = LocalHapticFeedback.current
    val spaceAccent = spaceAccentColors(space?.accentIndex ?: 0)
    var labelDraft by remember(app.id) { mutableStateOf(app.instanceLabel) }
    var preferWorkProfile by remember(app.id, hasWorkProfile) { mutableStateOf(false) }

    fun flushLabelDraft() {
        val trimmed = labelDraft.trim()
        if (trimmed.isNotBlank() && trimmed != app.instanceLabel) {
            onRenameInstance(trimmed)
        }
    }

    LaunchedEffect(app.id, labelDraft) {
        val trimmed = labelDraft.trim()
        if (trimmed.isNotBlank() && trimmed != app.instanceLabel) {
            delay(350L)
            onRenameInstance(trimmed)
        }
    }

    AlertDialog(
        onDismissRequest = {
            flushLabelDraft()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconBadge(
                    appName = app.displayName,
                    icon = appIcon,
                    size = 48.dp,
                    accentColor = spaceAccent.accent
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.detail_subtitle_format,
                            space?.name ?: stringResource(R.string.virtual_space_fallback),
                            app.instanceLabel
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = spaceAccent.accent
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.medium
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SessionDetailRow(
                            icon = Icons.Rounded.Security,
                            label = stringResource(R.string.session_space_profile_label),
                            value = space?.deviceModelPreset ?: stringResource(R.string.native_hardware_label)
                        )
                        activeDeviceProfile?.let { profile ->
                            SessionDetailRow(
                                icon = Icons.Rounded.DeveloperBoard,
                                label = stringResource(R.string.session_device_spec_label),
                                value = stringResource(
                                    R.string.session_device_spec_format,
                                    profile.manufacturer,
                                    profile.model,
                                    profile.sdkInt
                                )
                            )
                            SessionDetailRow(
                                icon = Icons.Rounded.Info,
                                label = stringResource(R.string.session_profile_id_label),
                                value = profile.profileId.take(8)
                            )
                        }
                        SessionDetailRow(
                            icon = Icons.Rounded.Layers,
                            label = stringResource(R.string.session_virtual_dpi_label),
                            value = stringResource(
                                if (space?.isolatedStorage == true) R.string.session_dpi_saved_format
                                else R.string.session_dpi_shared_format,
                                space?.virtualDpi ?: 440
                            )
                        )
                        SessionDetailRow(
                            icon = Icons.Rounded.Layers,
                            label = stringResource(R.string.session_host_panel_label),
                            value = hostDisplaySummary
                        )
                        SessionDetailRow(
                            icon = Icons.Rounded.Work,
                            label = stringResource(R.string.session_launch_target_label),
                            value = if (hasWorkProfile) {
                                stringResource(R.string.session_launch_mode_work_profile)
                            } else {
                                stringResource(R.string.session_launch_mode_host)
                            }
                        )
                    }
                }

                if (hasWorkProfile) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.launch_in_work_profile_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.launch_in_work_profile_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferWorkProfile,
                            onCheckedChange = { preferWorkProfile = it },
                            modifier = Modifier.testTag("session_work_profile_switch")
                        )
                    }
                }

                OutlinedTextField(
                    value = labelDraft,
                    onValueChange = { updated -> labelDraft = updated.take(24) },
                    label = { Text(stringResource(R.string.instance_label_field)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_instance_label_input")
                )

                Text(
                    text = stringResource(R.string.engine_performance_profile_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    perfModes.forEach { mode ->
                        val selected = app.performanceMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                flushLabelDraft()
                                onChangePerformanceMode(mode)
                            },
                            label = { Text(mode, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = spaceAccent.container,
                                selectedLabelColor = spaceAccent.onContainer
                            ),
                            modifier = Modifier.testTag("perf_mode_$mode")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            flushLabelDraft()
                            onCloneInstance()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("session_clone_instance_button")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.clone_action))
                    }
                    FilledTonalButton(
                        onClick = {
                            flushLabelDraft()
                            onInspectDetails()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("session_inspect_button")
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.inspect_instance))
                    }
                }

                Text(
                    text = stringResource(R.string.session_launch_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    flushLabelDraft()
                    onDismiss()
                },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("session_cancel_button")
            ) {
                Text(stringResource(R.string.close_action))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    flushLabelDraft()
                    onLaunchNow(preferWorkProfile)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = spaceAccent.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("session_launch_now_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.launch_now_action))
            }
        }
    )
}

@Composable
private fun SessionDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
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
