package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.InstalledAppInfo
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import com.example.ui.components.AppIconBadge
import com.example.ui.components.TelemetryPill
import com.example.ui.theme.spaceAccentColors
import java.util.Locale

private enum class ImportFilter {
    USER_APPS,
    GAMES,
    ALL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportInstalledAppsSheet(
    installedApps: List<InstalledAppInfo>,
    appsInSpace: List<VirtualAppEntity>,
    isScanning: Boolean,
    activeSpaceName: String,
    onRefresh: () -> Unit,
    onImportApp: (InstalledAppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ImportFilter.USER_APPS) }

    val instanceCountsByPackage = remember(appsInSpace) {
        appsInSpace.groupingBy { it.packageName }.eachCount()
    }

    val filteredApps = remember(installedApps, query, filter) {
        installedApps.filter { app ->
            val matchesFilter = when (filter) {
                ImportFilter.USER_APPS -> !app.isSystemApp
                ImportFilter.GAMES -> app.isGame
                ImportFilter.ALL -> true
            }
            val matchesQuery = query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Import into $activeSpaceName",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${filteredApps.size} shown of ${installedApps.size} discovered",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_installed_apps_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh installed apps"
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_apps_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                } else null,
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_installed_apps_input")
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = filter == ImportFilter.USER_APPS,
                        onClick = { filter = ImportFilter.USER_APPS },
                        label = { Text("User Apps") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("filter_user_apps_chip")
                    )
                }
                item {
                    FilterChip(
                        selected = filter == ImportFilter.GAMES,
                        onClick = { filter = ImportFilter.GAMES },
                        label = { Text(stringResource(R.string.filter_games)) },
                        leadingIcon = { Icon(Icons.Rounded.SportsEsports, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("filter_games_only_chip")
                    )
                }
                item {
                    FilterChip(
                        selected = filter == ImportFilter.ALL,
                        onClick = { filter = ImportFilter.ALL },
                        label = { Text(stringResource(R.string.filter_all)) },
                        leadingIcon = { Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("filter_all_apps_chip")
                    )
                }
            }

            if (isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching installed apps found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { appInfo ->
                        val existingCount = instanceCountsByPackage[appInfo.packageName] ?: 0
                        val sizePart = if (appInfo.apkSizeMb >= 0.1f) {
                            String.format(Locale.US, " • %.1f MB", appInfo.apkSizeMb)
                        } else ""
                        val sdkPart = if (appInfo.targetSdk > 0) " • API ${appInfo.targetSdk}" else ""

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AppIconBadge(appName = appInfo.label, icon = appInfo.icon, size = 44.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = appInfo.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (appInfo.isGame) {
                                            TelemetryPill(
                                                label = "GAME",
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        if (existingCount > 0) {
                                            TelemetryPill(
                                                label = "${existingCount}x",
                                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${appInfo.packageName} • v${appInfo.versionName}$sizePart$sdkPart",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onImportApp(appInfo)
                                    },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("add_installed_${appInfo.packageName}")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (existingCount > 0) "+1" else "Clone")
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
fun CreateEditSpaceDialog(
    editingSpace: VirtualSpaceEntity?,
    onConfirm: (name: String, tag: String, accentIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(editingSpace?.id) { mutableStateOf(editingSpace?.name ?: "") }
    var tag by remember(editingSpace?.id) { mutableStateOf(editingSpace?.tag ?: "CLONE") }
    var accentIndex by remember(editingSpace?.id) { mutableIntStateOf(editingSpace?.accentIndex ?: 0) }
    val paletteOptions = (0..4).map { idx -> spaceAccentColors(idx) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingSpace == null) "Create Virtual Space" else "Edit Virtual Space") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Space Name") },
                    placeholder = { Text("e.g., Dual Social, Work Apps") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_space_name_input")
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it.take(6) },
                    label = { Text("Tag Badge (max 6 chars)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_space_tag_input")
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Space Accent Theme", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        paletteOptions.forEachIndexed { idx, palette ->
                            val selected = accentIndex == idx
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { accentIndex = idx }
                                    .semantics { contentDescription = "Select ${palette.label} accent" }
                                    .testTag("space_accent_swatch_$idx")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(palette.accent)
                                        .border(
                                            width = if (selected) 2.5.dp else 0.dp,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, tag, accentIndex) },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("confirm_create_space_button")
            ) {
                Text(if (editingSpace == null) "Create" else "Save")
            }
        }
    )
}
