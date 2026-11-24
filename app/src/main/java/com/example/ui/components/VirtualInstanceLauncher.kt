package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ciphernodes.ciphervirtual.monitoring.ResourceTelemetrySnapshot
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import com.example.ui.theme.spaceAccentColors

@Composable
fun VirtualInstanceLauncher(
    spaces: List<VirtualSpaceEntity>,
    activeSpaceId: Long?,
    allVirtualApps: List<VirtualAppEntity>,
    resourceTelemetry: ResourceTelemetrySnapshot,
    onSelectSpace: (Long) -> Unit,
    onCreateSpaceClick: () -> Unit,
    onEditSpaceClick: (VirtualSpaceEntity) -> Unit,
    onDeleteSpace: (Long) -> Unit,
    onOpenSandboxConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .testTag("virtual_instance_launcher")
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
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Virtual Instance Launcher",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Switch or customize isolated runtime sessions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TelemetryPill(
                    label = "${spaces.size} envs",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            spaces.forEach { space ->
                val isSelected = space.id == activeSpaceId
                val appCount = allVirtualApps.count { it.spaceId == space.id }
                val usage = resourceTelemetry.spaceUsages.firstOrNull { it.spaceId == space.id }
                val spaceAccent = spaceAccentColors(space.accentIndex)
                val cardBorderColor by animateColorAsState(
                    targetValue = if (isSelected) spaceAccent.accent else MaterialTheme.colorScheme.outlineVariant,
                    animationSpec = spring(),
                    label = "space_border"
                )
                val cardFillColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        spaceAccent.container.copy(alpha = 0.42f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    animationSpec = spring(),
                    label = "space_fill"
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = cardFillColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = cardBorderColor,
                            shape = MaterialTheme.shapes.medium
                        )
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectSpace(space.id)
                        }
                        .testTag("launcher_env_card_${space.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    AnimatedPulseDot(color = spaceAccent.accent, size = 8.dp)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(spaceAccent.accent.copy(alpha = 0.7f))
                                    )
                                }
                                Text(
                                    text = space.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                TelemetryPill(
                                    label = space.tag,
                                    icon = if (isSelected) Icons.Rounded.CheckCircle else null,
                                    containerColor = if (isSelected) {
                                        spaceAccent.container
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    contentColor = if (isSelected) {
                                        spaceAccent.onContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (usage != null && usage.estimatedRamMb > 0L) {
                                    TelemetryPill(
                                        label = "${usage.estimatedRamMb} MB",
                                        icon = Icons.Rounded.Memory,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = "$appCount apps • ${space.deviceModelPreset} • ${space.virtualDpi} dpi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onEditSpaceClick(space) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("edit_space_button_${space.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit ${space.name}",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (spaces.size > 1) {
                            IconButton(
                                onClick = { onDeleteSpace(space.id) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("delete_space_button_${space.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Delete ${space.name}",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectSpace(space.id)
                                }
                            },
                            modifier = Modifier.testTag("launcher_env_toggle_${space.id}")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onCreateSpaceClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("launcher_new_env_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Environment")
                }
                FilledTonalButton(
                    onClick = onOpenSandboxConfig,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("launcher_sandbox_config_button")
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Configure")
                }
            }
        }
    }
}
