package com.cipher.virtual.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cipher.virtual.R
import com.cipher.virtual.data.model.InstalledAppInfo
import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.ui.components.AppIconBadge
import com.cipher.virtual.ui.components.TelemetryPill
import com.cipher.virtual.ui.theme.LocalNeuPalette
import com.cipher.virtual.ui.theme.NeuCard
import com.cipher.virtual.ui.theme.NeuChip
import com.cipher.virtual.ui.theme.NeuIconButton
import com.cipher.virtual.ui.theme.NeuTonalButton
import com.cipher.virtual.ui.theme.neuTextFieldColors
import java.util.Locale

private enum class ImportFilter {
    USER_APPS,
    GAMES,
    ALL
}

private enum class ImportSortOrder(val labelRes: Int) {
    DEFAULT(R.string.sort_recommended),
    NAME(R.string.sort_az),
    SIZE(R.string.sort_size),
    TARGET_SDK(R.string.sort_sdk)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportInstalledAppsSheet(
    installedApps: List<InstalledAppInfo>,
    appsInSpace: List<VirtualAppEntity>,
    appIcons: Map<String, ImageBitmap>,
    isScanning: Boolean,
    activeSpaceName: String,
    onRefresh: () -> Unit,
    onRequestIcons: (List<String>) -> Unit,
    onImportApp: (InstalledAppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val palette = LocalNeuPalette.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ImportFilter.USER_APPS) }
    var sortOrder by remember { mutableStateOf(ImportSortOrder.DEFAULT) }

    val instanceCountsByPackage = remember(appsInSpace) {
        appsInSpace.groupingBy { it.packageName }.eachCount()
    }

    val filteredApps = remember(installedApps, query, filter, sortOrder) {
        val filtered = installedApps.filter { app ->
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
        when (sortOrder) {
            ImportSortOrder.DEFAULT -> filtered
            ImportSortOrder.NAME -> filtered.sortedBy { it.label.lowercase() }
            ImportSortOrder.SIZE -> filtered.sortedByDescending { it.apkSizeMb }
            ImportSortOrder.TARGET_SDK -> filtered.sortedByDescending { it.targetSdk }
        }
    }

    LaunchedEffect(filteredApps) {
        val visiblePackages = filteredApps.take(40).map { it.packageName }
        if (visiblePackages.isNotEmpty()) {
            onRequestIcons(visiblePackages)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.base
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
                        text = stringResource(R.string.import_into_space_format, activeSpaceName),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(
                            R.string.import_discovered_count_format,
                            filteredApps.size,
                            installedApps.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                NeuIconButton(
                    onClick = onRefresh,
                    size = 48.dp,
                    modifier = Modifier.testTag("refresh_installed_apps_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh_installed_apps)
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
                        IconButton(
                            onClick = { query = "" },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("import_search_clear_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear_search))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = neuTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_installed_apps_input")
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item {
                    NeuChip(
                        selected = filter == ImportFilter.USER_APPS,
                        onClick = { filter = ImportFilter.USER_APPS },
                        label = { Text(stringResource(R.string.filter_user_apps)) },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("filter_user_apps_chip")
                    )
                }
                item {
                    NeuChip(
                        selected = filter == ImportFilter.GAMES,
                        onClick = { filter = ImportFilter.GAMES },
                        label = { Text(stringResource(R.string.filter_games)) },
                        leadingIcon = { Icon(Icons.Rounded.SportsEsports, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("filter_games_only_chip")
                    )
                }
                item {
                    NeuChip(
                        selected = filter == ImportFilter.ALL,
                        onClick = { filter = ImportFilter.ALL },
                        label = { Text(stringResource(R.string.filter_all)) },
                        leadingIcon = { Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("filter_all_apps_chip")
                    )
                }
                ImportSortOrder.entries.forEach { order ->
                    item {
                        NeuChip(
                            selected = sortOrder == order,
                            onClick = { sortOrder = order },
                            label = { Text(stringResource(order.labelRes)) },
                            modifier = Modifier.testTag("import_sort_${order.name.lowercase(Locale.US)}")
                        )
                    }
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
                        text = stringResource(R.string.no_matching_installed_apps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp, start = 4.dp, end = 4.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { appInfo ->
                        LaunchedEffect(appInfo.packageName) {
                            if (appInfo.icon == null && appIcons[appInfo.packageName] == null) {
                                onRequestIcons(listOf(appInfo.packageName))
                            }
                        }
                        val existingCount = instanceCountsByPackage[appInfo.packageName] ?: 0
                        val sizePart = if (appInfo.apkSizeMb >= 0.1f) {
                            stringResource(R.string.apk_size_mb_suffix_format, appInfo.apkSizeMb)
                        } else ""
                        val sdkPart = if (appInfo.targetSdk > 0) {
                            stringResource(R.string.api_level_suffix_format, appInfo.targetSdk)
                        } else ""

                        NeuCard(
                            cornerRadius = 18.dp,
                            elevation = 4.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AppIconBadge(
                                    appName = appInfo.label,
                                    icon = appIcons[appInfo.packageName] ?: appInfo.icon,
                                    size = 44.dp
                                )
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
                                                label = stringResource(R.string.badge_game),
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        if (existingCount > 0) {
                                            TelemetryPill(
                                                label = stringResource(R.string.instance_multiplier_format, existingCount),
                                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource(
                                            R.string.installed_app_meta_format,
                                            appInfo.packageName,
                                            appInfo.versionName,
                                            sizePart,
                                            sdkPart
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                NeuTonalButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onImportApp(appInfo)
                                    },
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("add_installed_${appInfo.packageName}")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (existingCount > 0) {
                                            stringResource(R.string.clone_more_badge)
                                        } else {
                                            stringResource(R.string.clone_action)
                                        }
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
