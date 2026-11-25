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
import kotlinx.coroutines.delay
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
                Text(stringResource(R.string.instance_not_found), style = MaterialTheme.typography.titleLarge)
                FilledTonalButton(
                    onClick = onBack,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("app_detail_not_found_back_button")
                ) {
                    Text(stringResource(R.string.back_to_spaces))
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
    var labelDraft by remember(app.id) { mutableStateOf(app.instanceLabel) }
    var notesDraft by remember(app.id) { mutableStateOf(app.notes) }
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    fun flushDrafts(): VirtualAppEntity {
        val nextLabel = labelDraft.trim().takeIf { it.isNotBlank() } ?: app.instanceLabel
        val nextNotes = notesDraft
        return if (nextLabel != app.instanceLabel || nextNotes != app.notes) {
            app.copy(instanceLabel = nextLabel, notes = nextNotes).also(onUpdateApp)
        } else {
            app
        }
    }

    LaunchedEffect(app.id, labelDraft, notesDraft) {
        val nextLabel = labelDraft.trim().takeIf { it.isNotBlank() } ?: app.instanceLabel
        val nextNotes = notesDraft
        if (nextLabel != app.instanceLabel || nextNotes != app.notes) {
            delay(350L)
            onUpdateApp(app.copy(instanceLabel = nextLabel, notes = nextNotes))
        }
    }

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
                        onClick = {
                            flushDrafts()
                            onBack()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("app_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_detail_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = stringResource(
                                R.string.detail_subtitle_format,
                                space?.name ?: stringResource(R.string.space_fallback_name),
                                app.instanceLabel
                            ),
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
                                label = stringResource(R.string.launches_count_format, app.launchCount),
                                containerColor = spaceAccent.container,
                                contentColor = spaceAccent.onContainer
                            )
                        }

                        installedMeta?.let { meta ->
                            DetailInfoRow(
                                stringResource(R.string.detail_version_label),
                                stringResource(R.string.detail_version_value_format, meta.versionName)
                            )
                            DetailInfoRow(
                                stringResource(R.string.detail_target_sdk_label),
                                stringResource(R.string.detail_target_sdk_value_format, meta.targetSdk)
                            )
                            if (meta.apkSizeMb >= 0.1f) {
                                DetailInfoRow(
                                    stringResource(R.string.detail_apk_size_label),
                                    stringResource(R.string.detail_apk_size_value_format, meta.apkSizeMb)
                                )
                            }
                        }
                        DetailInfoRow(stringResource(R.string.detail_added_label), dateFormatter.format(Date(app.addedAt)))
                        DetailInfoRow(
                            stringResource(R.string.detail_last_launched_label),
                            app.lastLaunchedAt?.let { dateFormatter.format(Date(it)) }
                                ?: stringResource(R.string.detail_never)
                        )
                        DetailInfoRow(
                            stringResource(R.string.detail_last_profile_id_label),
                            app.lastProfileId?.take(8) ?: stringResource(R.string.detail_none_recorded)
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
                            Text(stringResource(R.string.assigned_env_profile_title), style = MaterialTheme.typography.titleMedium)
                        }
                        DetailInfoRow(
                            stringResource(R.string.detail_workspace_label),
                            "${space?.name ?: stringResource(R.string.space_fallback_name)} (${space?.tag ?: "MAIN"})"
                        )
                        DetailInfoRow(stringResource(R.string.detail_preset_label), space?.deviceModelPreset ?: "ROG Phone 8 Pro")
                        DetailInfoRow(
                            stringResource(R.string.detail_virtual_dpi_label),
                            stringResource(R.string.dpi_value_format, space?.virtualDpi ?: 440)
                        )
                        activeProfile?.let { profile ->
                            DetailInfoRow(stringResource(R.string.detail_model_label), "${profile.manufacturer} ${profile.model}")
                            DetailInfoRow(stringResource(R.string.detail_product_device_label), "${profile.product} / ${profile.device}")
                            DetailInfoRow(stringResource(R.string.detail_board_hardware_label), "${profile.board} / ${profile.hardware}")
                            DetailInfoRow(
                                stringResource(R.string.detail_android_os_label),
                                stringResource(R.string.android_os_value_format, profile.androidVersion, profile.sdkInt)
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(stringResource(R.string.instance_config_title), style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = labelDraft,
                            onValueChange = { updated -> labelDraft = updated.take(24) },
                            label = { Text(stringResource(R.string.instance_label_field)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("detail_instance_label_input")
                        )
                        OutlinedTextField(
                            value = notesDraft,
                            onValueChange = { updated -> notesDraft = updated.take(180) },
                            label = { Text(stringResource(R.string.instance_notes_field)) },
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("detail_instance_notes_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Balanced", "Turbo", "Battery").forEach { mode ->
                                FilterChip(
                                    selected = app.performanceMode == mode,
                                    onClick = {
                                        val synced = flushDrafts()
                                        onUpdateApp(synced.copy(performanceMode = mode))
                                    },
                                    label = { Text(mode) },
                                    leadingIcon = if (app.performanceMode == mode) {
                                        { Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = spaceAccent.container,
                                        selectedLabelColor = spaceAccent.onContainer
                                    ),
                                    modifier = Modifier.testTag("detail_perf_mode_${mode.lowercase(Locale.US)}")
                                )
                            }
                        }
                        if (workProfileSupported) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.prefer_work_profile_label), style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = preferWorkProfile,
                                    onCheckedChange = { preferWorkProfile = it },
                                    modifier = Modifier.testTag("detail_work_profile_switch")
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
                            Text(stringResource(R.string.isolated_workspace_storage_title), style = MaterialTheme.typography.titleMedium)
                        }
                        DetailInfoRow(
                            stringResource(R.string.worker_slot_label),
                            uiState.inspectedWorkerSlot ?: stringResource(R.string.worker_slot_pending)
                        )
                        DetailInfoRow(
                            stringResource(R.string.files_recorded_label),
                            stringResource(
                                R.string.files_recorded_format,
                                workspaceStats?.fileCount ?: 0,
                                workspaceStats?.sizeBytes ?: 0L
                            )
                        )
                        Text(
                            text = workspaceStats?.directoryPath ?: stringResource(R.string.workspace_not_initialized),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        FilledTonalButton(
                            onClick = { onResetWorkspace(flushDrafts()) },
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
                        onClick = { onCloneApp(flushDrafts()) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("detail_clone_button")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.clone_action))
                    }
                    FilledTonalButton(
                        onClick = {
                            onRemoveApp(app)
                            onBack()
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("detail_remove_button")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.remove_action))
                    }
                    Button(
                        onClick = { onLaunchApp(flushDrafts(), preferWorkProfile) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = spaceAccent.accent,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("detail_launch_button")
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
