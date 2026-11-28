package com.cipher.virtual.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cipher.virtual.R
import com.cipher.virtual.data.hardware.DeviceProfile
import com.cipher.virtual.data.local.InstanceWorkspaceStats
import com.cipher.virtual.data.model.InstalledAppInfo
import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.data.model.VirtualSpaceEntity
import com.cipher.virtual.ui.OctoUiState
import com.cipher.virtual.ui.theme.NeuButton
import com.cipher.virtual.ui.theme.NeuCard
import com.cipher.virtual.ui.theme.NeuChip
import com.cipher.virtual.ui.theme.NeuIconButton
import com.cipher.virtual.ui.theme.NeuTonalButton
import com.cipher.virtual.ui.theme.NeuWell
import com.cipher.virtual.ui.theme.neuTextFieldColors
import com.cipher.virtual.ui.theme.spaceAccentColors
import java.text.SimpleDateFormat
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
            NeuCard(
                cornerRadius = 24.dp,
                elevation = 6.dp,
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(stringResource(R.string.instance_not_found), style = MaterialTheme.typography.titleLarge)
                    NeuTonalButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("app_detail_not_found_back_button")
                    ) {
                        Text(stringResource(R.string.back_to_spaces))
                    }
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
    val focusManager = LocalFocusManager.current
    var preferWorkProfile by remember(app.id) { mutableStateOf(false) }
    var labelDraft by remember(app.id, app.instanceLabel) { mutableStateOf(app.instanceLabel) }
    var notesDraft by remember(app.id, app.notes) { mutableStateOf(app.notes) }
    var wasLabelFocused by remember(app.id) { mutableStateOf(false) }
    var wasNotesFocused by remember(app.id) { mutableStateOf(false) }
    var isRemoving by remember(app.id) { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    fun flushDrafts(): VirtualAppEntity {
        val nextLabel = labelDraft.trim().takeIf { it.isNotBlank() } ?: app.instanceLabel
        val nextNotes = notesDraft
        return if (!isRemoving && (nextLabel != app.instanceLabel || nextNotes != app.notes)) {
            app.copy(instanceLabel = nextLabel, notes = nextNotes).also(onUpdateApp)
        } else {
            app
        }
    }

    val latestFlush by rememberUpdatedState { flushDrafts() }
    DisposableEffect(app.id) {
        onDispose {
            latestFlush()
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NeuIconButton(
                        onClick = {
                            flushDrafts()
                            onBack()
                        },
                        size = 48.dp,
                        modifier = Modifier.testTag("app_detail_back_button")
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
                InstanceHeaderCard(
                    app = app,
                    appIcon = uiState.appIcons[app.packageName] ?: installedMeta?.icon,
                    installedMeta = installedMeta,
                    spaceAccent = spaceAccent,
                    dateFormatter = dateFormatter
                )
            }

            item {
                EnvironmentProfileCard(
                    space = space,
                    activeProfile = activeProfile,
                    accentColor = spaceAccent.accent
                )
            }

            item {
                NeuCard(
                    cornerRadius = 24.dp,
                    elevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(stringResource(R.string.instance_config_title), style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = labelDraft,
                            onValueChange = { updated -> labelDraft = updated.take(24) },
                            label = { Text(stringResource(R.string.instance_label_field)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    flushDrafts()
                                }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            colors = neuTextFieldColors(spaceAccent.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (wasLabelFocused && !focusState.isFocused) {
                                        flushDrafts()
                                    }
                                    wasLabelFocused = focusState.isFocused
                                }
                                .testTag("detail_instance_label_input")
                        )
                        OutlinedTextField(
                            value = notesDraft,
                            onValueChange = { updated -> notesDraft = updated.take(180) },
                            label = { Text(stringResource(R.string.instance_notes_field)) },
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp),
                            colors = neuTextFieldColors(spaceAccent.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (wasNotesFocused && !focusState.isFocused) {
                                        flushDrafts()
                                    }
                                    wasNotesFocused = focusState.isFocused
                                }
                                .testTag("detail_instance_notes_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Balanced", "Turbo", "Battery").forEach { mode ->
                                val selected = app.performanceMode == mode
                                NeuChip(
                                    selected = selected,
                                    onClick = {
                                        val synced = flushDrafts()
                                        onUpdateApp(synced.copy(performanceMode = mode))
                                    },
                                    label = { Text(mode) },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    accentColor = spaceAccent.accent,
                                    selectedContainerColor = spaceAccent.container,
                                    selectedContentColor = spaceAccent.onContainer,
                                    modifier = Modifier.testTag("detail_perf_mode_${mode.lowercase(Locale.US)}")
                                )
                            }
                        }
                        if (workProfileSupported) {
                            NeuWell(
                                cornerRadius = 14.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
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
            }

            item {
                InstanceStorageCard(
                    inspectedWorkerSlot = uiState.inspectedWorkerSlot,
                    workspaceStats = workspaceStats,
                    onResetWorkspace = { onResetWorkspace(flushDrafts()) }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NeuTonalButton(
                        onClick = { onCloneApp(flushDrafts()) },
                        accentColor = spaceAccent.accent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_clone_button")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.clone_action))
                    }
                    NeuTonalButton(
                        onClick = {
                            isRemoving = true
                            onRemoveApp(app)
                            onBack()
                        },
                        contentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("detail_remove_button")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.remove_action))
                    }
                    NeuButton(
                        onClick = { onLaunchApp(flushDrafts(), preferWorkProfile) },
                        containerColor = spaceAccent.accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .weight(1f)
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
