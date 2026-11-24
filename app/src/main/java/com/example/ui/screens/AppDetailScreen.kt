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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.hardware.DeviceProfile
import com.example.data.local.InstanceWorkspaceStats
import com.example.data.model.InstalledAppInfo
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import com.example.ui.OctoUiState
import com.example.ui.components.AppIconBadge
import com.example.ui.components.TelemetryPill
import com.example.ui.theme.spaceAccentColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppDetailScreen(
    appId: Long,
    uiState: OctoUiState,
    hasWorkProfileTarget: (String) -> Boolean,
    onInspectWorkspace: (Long, Long) -> Unit,
    onUpdateApp: (VirtualAppEntity) -> Unit,
    onCloneApp: (VirtualAppEntity) -> Unit,
    onRemoveApp: (VirtualAppEntity) -> Unit,
    onResetWorkspace: (VirtualAppEntity) -> Unit,
    onLaunchApp: (VirtualAppEntity, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = uiState.allVirtualApps.firstOrNull { it.id == appId }
    if (app == null) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text("Instance not found", style = MaterialTheme.typography.titleLarge)
                FilledTonalButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
                    Text("Back to Spaces")
                }
            }
        }
        return
    }

    val space: VirtualSpaceEntity? = uiState.spaces.firstOrNull { it.id == app.spaceId } ?: uiState.activeSpace
    val spaceAccent = spaceAccentColors(space?.accentIndex ?: 0)
    val installedMeta: InstalledAppInfo? = uiState.installedApps.firstOrNull { it.packageName == app.packageName }
    val activeProfile: DeviceProfile? = uiState.activeDeviceProfile
    val workspaceStats: InstanceWorkspaceStats? = uiState.inspectedWorkspaceStats
    val workProfileSupported = remember(app.packageName) { hasWorkProfileTarget(app.packageName) }
    var preferWorkProfile by remember(app.id) { mutableStateOf(false) }
    var labelDraft by remember(app.id, app.instanceLabel) { mutableStateOf(app.instanceLabel) }
    var notesDraft by remember(app.id, app.notes) { mutableStateOf(app.notes) }
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(app.spaceId, app.id) {
        onInspectWorkspace(app.spaceId, app.id)
    }

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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("app_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_detail_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "${space?.name ?: "Space"} • ${app.instanceLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = spaceAccent.accent
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, spaceAccent.accent.copy(alpha = 0.35f), MaterialTheme.shapes.large)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIconBadge(
                                appName = app.displayName,
                                icon = uiState.appIcons[app.packageName] ?: installedMeta?.icon,
                                size = 58.dp,
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
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TelemetryPill(
                                label = "${app.launchCount} launches",
                                containerColor = spaceAccent.container,
                                contentColor = spaceAccent.onContainer
                            )
                        }

                        installedMeta?.let { meta ->
                            DetailInfoRow("Version", "v${meta.versionName}")
                            DetailInfoRow("Target SDK", "API ${meta.targetSdk}")
                            if (meta.apkSizeMb >= 0.1f) {
                                DetailInfoRow(
                                    "APK Size",
                                    String.format(Locale.US, "%.1f MB", meta.apkSizeMb)
                                )
                            }
                        }
                        DetailInfoRow("Added", dateFormatter.format(Date(app.addedAt)))
                        DetailInfoRow(
                            "Last Launched",
                            app.lastLaunchedAt?.let { dateFormatter.format(Date(it)) } ?: "Never"
                        )
                        DetailInfoRow(
                            "Last Profile ID",
                            app.lastProfileId?.take(8) ?: "None recorded"
                        )
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.DeveloperBoard, contentDescription = null, tint = spaceAccent.accent)
                            Text("Assigned Environment & Device Profile", style = MaterialTheme.typography.titleMedium)
                        }
                        DetailInfoRow("Workspace", "${space?.name ?: "Default"} (${space?.tag ?: "SPACE"})")
                        DetailInfoRow("Preset", space?.deviceModelPreset ?: "ROG Phone 8 Pro")
                        DetailInfoRow("Virtual DPI", "${space?.virtualDpi ?: 440} dpi")
                        activeProfile?.let { profile ->
                            DetailInfoRow("Model", "${profile.manufacturer} ${profile.model}")
                            DetailInfoRow("Product / Device", "${profile.product} / ${profile.device}")
                            DetailInfoRow("Board / Hardware", "${profile.board} / ${profile.hardware}")
                            DetailInfoRow("Android OS", "Android ${profile.androidVersion} (API ${profile.sdkInt})")
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Instance Configuration", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = labelDraft,
                            onValueChange = { updated ->
                                val clean = updated.take(24)
                                labelDraft = clean
                                if (clean.isNotBlank()) {
                                    onUpdateApp(app.copy(instanceLabel = clean.trim()))
                                }
                            },
                            label = { Text("Instance Label") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("detail_instance_label_input")
                        )
                        OutlinedTextField(
                            value = notesDraft,
                            onValueChange = { updated ->
                                notesDraft = updated.take(180)
                                onUpdateApp(app.copy(notes = notesDraft))
                            },
                            label = { Text("Instance Notes (Account / Test Tag)") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth().testTag("detail_instance_notes_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Balanced", "Turbo", "Battery").forEach { mode ->
                                FilterChip(
                                    selected = app.performanceMode == mode,
                                    onClick = { onUpdateApp(app.copy(performanceMode = mode)) },
                                    label = { Text(mode) },
                                    leadingIcon = if (app.performanceMode == mode) {
                                        { Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = spaceAccent.container,
                                        selectedLabelColor = spaceAccent.onContainer
                                    )
                                )
                            }
                        }
                        if (workProfileSupported) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Prefer Work / Managed Profile", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = preferWorkProfile,
                                    onCheckedChange = { preferWorkProfile = it }
                                )
                            }
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Isolated Workspace Storage", style = MaterialTheme.typography.titleMedium)
                        }
                        DetailInfoRow(
                            "Files Recorded",
                            "${workspaceStats?.fileCount ?: 0} file(s) • ${workspaceStats?.sizeBytes ?: 0L} bytes"
                        )
                        Text(
                            text = workspaceStats?.directoryPath ?: "Not initialized yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        FilledTonalButton(
                            onClick = { onResetWorkspace(app) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reset_instance_workspace_button")
                        ) {
                            Icon(Icons.Rounded.FolderDelete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.reset_workspace_data))
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onCloneApp(app) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clone")
                    }
                    FilledTonalButton(
                        onClick = {
                            onRemoveApp(app)
                            onBack()
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove")
                    }
                    Button(
                        onClick = { onLaunchApp(app, preferWorkProfile) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = spaceAccent.accent,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.launch_in_space))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
