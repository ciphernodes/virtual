package com.cipher.virtual.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cipher.virtual.R
import com.cipher.virtual.data.model.InstalledAppInfo
import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.ui.OctoUiState
import com.cipher.virtual.ui.components.AnimatedPulseDot
import com.cipher.virtual.ui.components.LiveSparklineChart
import com.cipher.virtual.ui.components.TelemetryPill
import com.cipher.virtual.ui.theme.LocalNeuPalette
import com.cipher.virtual.ui.theme.NeuCard
import com.cipher.virtual.ui.theme.NeuChip
import com.cipher.virtual.ui.theme.NeuTonalButton
import com.cipher.virtual.ui.theme.NeuWell
import com.cipher.virtual.ui.theme.neuTextFieldColors
import com.cipher.virtual.ui.theme.spaceAccentColors
import java.util.Locale

private enum class SpaceSortOrder(val labelRes: Int) {
    RECENT(R.string.sort_recent),
    MOST_LAUNCHED(R.string.sort_most_used),
    NAME_AZ(R.string.sort_az)
}

@Composable
fun SpacesScreen(
    uiState: OctoUiState,
    onAddApp: (InstalledAppInfo) -> Unit,
    onDuplicateApp: (VirtualAppEntity) -> Unit,
    onRefreshInstalledApps: () -> Unit,
    onRequestIcons: (List<String>) -> Unit,
    onOpenLaunchSession: (VirtualAppEntity) -> Unit,
    onInspectApp: (VirtualAppEntity) -> Unit,
    onRemoveVirtualApp: (VirtualAppEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showImportSheet by remember { mutableStateOf(false) }
    var spaceSearchQuery by remember { mutableStateOf("") }
    var perfFilter by remember { mutableStateOf<String?>(null) }
    var sortOrder by remember { mutableStateOf(SpaceSortOrder.RECENT) }

    val palette = LocalNeuPalette.current
    val activeSpace = uiState.activeSpace
    val spaceAccent = spaceAccentColors(activeSpace?.accentIndex ?: 0)
    val spaceName = activeSpace?.name ?: stringResource(R.string.space_fallback_name)

    val displayedApps = remember(uiState.appsInSpace, spaceSearchQuery, perfFilter, sortOrder) {
        val filtered = uiState.appsInSpace.filter { app ->
            val matchesPerf = perfFilter == null || app.performanceMode == perfFilter
            val matchesQuery = spaceSearchQuery.isBlank() ||
                app.displayName.contains(spaceSearchQuery, ignoreCase = true) ||
                app.instanceLabel.contains(spaceSearchQuery, ignoreCase = true) ||
                app.packageName.contains(spaceSearchQuery, ignoreCase = true)
            matchesPerf && matchesQuery
        }
        when (sortOrder) {
            SpaceSortOrder.RECENT -> filtered.sortedByDescending { it.addedAt }
            SpaceSortOrder.MOST_LAUNCHED -> filtered.sortedByDescending { it.launchCount }
            SpaceSortOrder.NAME_AZ -> filtered.sortedBy { it.displayName.lowercase() }
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val columns = when {
            maxWidth >= 960.dp -> 3
            maxWidth >= 640.dp -> 2
            else -> 1
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1160.dp)
        ) {
            item(span = { GridItemSpan(columns) }) {
                HeroWorkspaceBanner(
                    activeSpace = activeSpace,
                    appCount = uiState.appsInSpace.size,
                    onImportClick = { showImportSheet = true }
                )
            }

            if (activeSpace?.frameRateHud == true) {
                item(span = { GridItemSpan(columns) }) {
                    val telemetry = uiState.resourceTelemetry
                    NeuCard(
                        cornerRadius = 24.dp,
                        elevation = 6.dp,
                        accentTint = spaceAccent.accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_telemetry_hud_row")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AnimatedPulseDot(color = spaceAccent.accent, size = 8.dp)
                                TelemetryPill(
                                    label = stringResource(R.string.hud_label_format, telemetry.stability.name),
                                    icon = Icons.Rounded.Speed,
                                    containerColor = spaceAccent.container,
                                    contentColor = spaceAccent.onContainer
                                )
                                TelemetryPill(
                                    label = stringResource(R.string.hud_heap_mb_format, telemetry.appHeapUsedMb),
                                    icon = Icons.Rounded.Memory,
                                    containerColor = palette.pressedSurface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                                TelemetryPill(
                                    label = stringResource(R.string.hud_cpu_percent_format, telemetry.processCpuPercent),
                                    icon = Icons.Rounded.Bolt,
                                    containerColor = palette.pressedSurface,
                                    contentColor = spaceAccent.accent
                                )
                            }
                            if (telemetry.cpuHistory.size >= 2) {
                                LiveSparklineChart(
                                    samples = telemetry.cpuHistory,
                                    lineColor = spaceAccent.accent,
                                    unitSuffix = "%",
                                    maxValueHint = 20f,
                                    chartHeight = 46.dp
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.appsInSpace.isEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    EmptySpaceCard(
                        spaceName = spaceName,
                        onImportClick = { showImportSheet = true }
                    )
                }
            } else {
                item(span = { GridItemSpan(columns) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.space_apps_title_format, spaceName),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = stringResource(
                                        R.string.space_profile_summary_format,
                                        activeSpace?.deviceModelPreset ?: stringResource(R.string.native_hardware_label),
                                        activeSpace?.virtualDpi ?: 440
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            NeuTonalButton(
                                onClick = { showImportSheet = true },
                                accentColor = spaceAccent.accent,
                                contentColor = spaceAccent.accent,
                                modifier = Modifier.testTag("import_app_inline_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.add_app))
                            }
                        }

                        if (uiState.appsInSpace.size >= 2) {
                            OutlinedTextField(
                                value = spaceSearchQuery,
                                onValueChange = { spaceSearchQuery = it },
                                placeholder = {
                                    Text(
                                        stringResource(
                                            R.string.search_apps_in_space_hint,
                                            spaceName.lowercase(Locale.getDefault())
                                        )
                                    )
                                },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                trailingIcon = if (spaceSearchQuery.isNotEmpty()) {
                                    {
                                        IconButton(
                                            onClick = { spaceSearchQuery = "" },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("space_search_clear_button")
                                        ) {
                                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear))
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                colors = neuTextFieldColors(spaceAccent.accent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_space_apps_input")
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                item {
                                    NeuChip(
                                        selected = perfFilter == null,
                                        onClick = { perfFilter = null },
                                        label = { Text(stringResource(R.string.filter_all_count, uiState.appsInSpace.size)) },
                                        accentColor = spaceAccent.accent,
                                        selectedContainerColor = spaceAccent.container,
                                        selectedContentColor = spaceAccent.onContainer,
                                        modifier = Modifier.testTag("space_perf_filter_all")
                                    )
                                }
                                listOf("Turbo", "Balanced", "Battery").forEach { mode ->
                                    item {
                                        NeuChip(
                                            selected = perfFilter == mode,
                                            onClick = { perfFilter = if (perfFilter == mode) null else mode },
                                            label = { Text(mode) },
                                            accentColor = spaceAccent.accent,
                                            selectedContainerColor = spaceAccent.container,
                                            selectedContentColor = spaceAccent.onContainer,
                                            modifier = Modifier.testTag("space_perf_filter_${mode.lowercase(Locale.US)}")
                                        )
                                    }
                                }
                                SpaceSortOrder.entries.forEach { order ->
                                    item {
                                        NeuChip(
                                            selected = sortOrder == order,
                                            onClick = { sortOrder = order },
                                            label = {
                                                Text(
                                                    stringResource(
                                                        R.string.sort_prefix_format,
                                                        stringResource(order.labelRes)
                                                    )
                                                )
                                            },
                                            accentColor = spaceAccent.accent,
                                            selectedContainerColor = spaceAccent.container,
                                            selectedContentColor = spaceAccent.onContainer,
                                            modifier = Modifier.testTag("space_sort_${order.name.lowercase(Locale.US)}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (displayedApps.isEmpty()) {
                    item(span = { GridItemSpan(columns) }) {
                        NeuWell(
                            cornerRadius = 18.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.filter_empty_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(displayedApps, key = { it.id }) { app ->
                        VirtualAppCard(
                            app = app,
                            icon = uiState.appIcons[app.packageName],
                            accentColor = spaceAccent.accent,
                            onLaunchClick = { onOpenLaunchSession(app) },
                            onInspectClick = { onInspectApp(app) },
                            onCloneAnotherClick = { onDuplicateApp(app) },
                            onRemoveClick = { onRemoveVirtualApp(app) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        )
                    }
                }
            }
        }
    }

    if (showImportSheet) {
        ImportInstalledAppsSheet(
            installedApps = uiState.installedApps,
            appsInSpace = uiState.appsInSpace,
            appIcons = uiState.appIcons,
            isScanning = uiState.isScanningApps,
            activeSpaceName = activeSpace?.name ?: stringResource(R.string.virtual_space_fallback),
            onRefresh = onRefreshInstalledApps,
            onRequestIcons = onRequestIcons,
            onImportApp = onAddApp,
            onDismiss = { showImportSheet = false }
        )
    }
}
