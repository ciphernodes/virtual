package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VerifiedUser
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
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.InstalledAppInfo
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import com.example.ui.OctoUiState
import com.example.ui.components.AnimatedPulseDot
import com.example.ui.components.AppIconBadge
import com.example.ui.components.LiveSparklineChart
import com.example.ui.components.TelemetryPill
import com.example.ui.components.VirtualInstanceLauncher
import com.example.ui.theme.spaceAccentColors
import java.util.Locale

private enum class SpaceSortOrder(val label: String) {
    RECENT("Recent"),
    MOST_LAUNCHED("Most Used"),
    NAME_AZ("A–Z")
}

@Composable
fun SpacesScreen(
    uiState: OctoUiState,
    onSelectSpace: (Long) -> Unit,
    onCreateSpace: (String, String, Int) -> Unit,
    onEditSpace: (VirtualSpaceEntity, String, String, Int) -> Unit,
    onDeleteSpace: (Long) -> Unit,
    onAddApp: (InstalledAppInfo) -> Unit,
    onDuplicateApp: (VirtualAppEntity) -> Unit,
    onRefreshInstalledApps: () -> Unit,
    onRequestIcons: (List<String>) -> Unit,
    onOpenLaunchSession: (VirtualAppEntity) -> Unit,
    onInspectApp: (VirtualAppEntity) -> Unit,
    onOpenPermissionsTab: () -> Unit,
    onOpenSandboxConfig: () -> Unit,
    onRemoveVirtualApp: (VirtualAppEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showImportSheet by remember { mutableStateOf(false) }
    var showCreateSpaceDialog by remember { mutableStateOf(false) }
    var editingSpace by remember { mutableStateOf<VirtualSpaceEntity?>(null) }
    var spaceSearchQuery by remember { mutableStateOf("") }
    var perfFilter by remember { mutableStateOf<String?>(null) }
    var sortOrder by remember { mutableStateOf(SpaceSortOrder.RECENT) }

    val activeSpace = uiState.activeSpace
    val spaceAccent = spaceAccentColors(activeSpace?.accentIndex ?: 0)

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1160.dp)
        ) {
            item(span = { GridItemSpan(columns) }) {
                HeroWorkspaceBanner(
                    activeSpace = activeSpace,
                    appCount = uiState.appsInSpace.size,
                    grantedPermissions = uiState.deviceBridge.grantedCount,
                    totalPermissions = uiState.deviceBridge.totalCount,
                    onImportClick = { showImportSheet = true },
                    onPermissionsClick = onOpenPermissionsTab
                )
            }

            item(span = { GridItemSpan(columns) }) {
                VirtualInstanceLauncher(
                    spaces = uiState.spaces,
                    activeSpaceId = activeSpace?.id,
                    allVirtualApps = uiState.allVirtualApps,
                    resourceTelemetry = uiState.resourceTelemetry,
                    onSelectSpace = onSelectSpace,
                    onCreateSpaceClick = { showCreateSpaceDialog = true },
                    onEditSpaceClick = { editingSpace = it },
                    onDeleteSpace = onDeleteSpace,
                    onOpenSandboxConfig = onOpenSandboxConfig
                )
            }

            if (activeSpace?.frameRateHud == true) {
                item(span = { GridItemSpan(columns) }) {
                    val telemetry = uiState.resourceTelemetry
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                spaceAccent.accent.copy(alpha = 0.28f),
                                MaterialTheme.shapes.large
                            )
                            .testTag("live_telemetry_hud_row")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AnimatedPulseDot(color = spaceAccent.accent, size = 8.dp)
                                TelemetryPill(
                                    label = "HUD • ${telemetry.stability.name}",
                                    icon = Icons.Rounded.Speed,
                                    containerColor = spaceAccent.container,
                                    contentColor = spaceAccent.onContainer
                                )
                                TelemetryPill(
                                    label = "${telemetry.appHeapUsedMb} MB Heap",
                                    icon = Icons.Rounded.Memory,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                                TelemetryPill(
                                    label = String.format(Locale.US, "%.1f%% CPU", telemetry.processCpuPercent),
                                    icon = Icons.Rounded.Bolt,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                        spaceName = activeSpace?.name ?: "Space",
                        onImportClick = { showImportSheet = true }
                    )
                }
            } else {
                item(span = { GridItemSpan(columns) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${activeSpace?.name ?: "Space"} Apps",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "Profile: ${activeSpace?.deviceModelPreset ?: "Native"} • ${activeSpace?.virtualDpi ?: 440} dpi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = { showImportSheet = true },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("import_app_inline_button")
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
                                placeholder = { Text("Search apps in ${activeSpace?.name ?: "space"}…") },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                trailingIcon = if (spaceSearchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { spaceSearchQuery = "" }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_space_apps_input")
                            )

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = perfFilter == null,
                                        onClick = { perfFilter = null },
                                        label = { Text("All (${uiState.appsInSpace.size})") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = spaceAccent.container,
                                            selectedLabelColor = spaceAccent.onContainer
                                        )
                                    )
                                }
                                listOf("Turbo", "Balanced", "Battery").forEach { mode ->
                                    item {
                                        FilterChip(
                                            selected = perfFilter == mode,
                                            onClick = { perfFilter = if (perfFilter == mode) null else mode },
                                            label = { Text(mode) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = spaceAccent.container,
                                                selectedLabelColor = spaceAccent.onContainer
                                            )
                                        )
                                    }
                                }
                                SpaceSortOrder.entries.forEach { order ->
                                    item {
                                        FilterChip(
                                            selected = sortOrder == order,
                                            onClick = { sortOrder = order },
                                            label = { Text("Sort: ${order.label}") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (displayedApps.isEmpty()) {
                    item(span = { GridItemSpan(columns) }) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                        ) {
                            Text(
                                text = "No apps in this space match your current filter.",
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
            activeSpaceName = activeSpace?.name ?: "Virtual Space",
            onRefresh = onRefreshInstalledApps,
            onRequestIcons = onRequestIcons,
            onImportApp = onAddApp,
            onDismiss = { showImportSheet = false }
        )
    }

    if (showCreateSpaceDialog || editingSpace != null) {
        CreateEditSpaceDialog(
            editingSpace = editingSpace,
            onConfirm = { name, tag, accent ->
                val target = editingSpace
                if (target != null) {
                    onEditSpace(target, name, tag, accent)
                } else {
                    onCreateSpace(name, tag, accent)
                }
                showCreateSpaceDialog = false
                editingSpace = null
            },
            onDismiss = {
                showCreateSpaceDialog = false
                editingSpace = null
            }
        )
    }
}

@Composable
private fun HeroWorkspaceBanner(
    activeSpace: VirtualSpaceEntity?,
    appCount: Int,
    grantedPermissions: Int,
    totalPermissions: Int,
    onImportClick: () -> Unit,
    onPermissionsClick: () -> Unit
) {
    val spaceAccent = spaceAccentColors(activeSpace?.accentIndex ?: 0)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = spaceAccent.accent.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.large
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner),
                contentDescription = stringResource(R.string.cd_hero_banner),
                contentScale = ContentScale.Crop,
                alpha = 0.42f,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        TelemetryPill(
                            label = stringResource(R.string.engine_ready),
                            icon = Icons.Rounded.Layers,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.hero_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.hero_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onImportClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = spaceAccent.accent,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("hero_import_app_button")
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

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        TelemetryPill(
                            label = "$appCount ${stringResource(R.string.virtual_apps_count)}",
                            icon = Icons.Rounded.Apps,
                            containerColor = spaceAccent.container,
                            contentColor = spaceAccent.onContainer
                        )
                    }
                    item {
                        TelemetryPill(
                            label = "$grantedPermissions/$totalPermissions ${stringResource(R.string.permissions_granted_label)}",
                            icon = Icons.Rounded.VerifiedUser,
                            containerColor = if (grantedPermissions == totalPermissions && totalPermissions > 0) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (grantedPermissions == totalPermissions && totalPermissions > 0) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.clickable(onClick = onPermissionsClick)
                        )
                    }
                    activeSpace?.let {
                        item {
                            TelemetryPill(
                                label = it.tag,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualAppCard(
    app: VirtualAppEntity,
    icon: ImageBitmap?,
    accentColor: androidx.compose.ui.graphics.Color,
    onLaunchClick: () -> Unit,
    onInspectClick: () -> Unit,
    onCloneAnotherClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.medium
            )
            .clickable(onClick = onLaunchClick)
            .testTag("virtual_app_card_${app.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconBadge(
                    appName = app.displayName,
                    icon = icon,
                    size = 52.dp,
                    accentColor = accentColor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = app.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        TelemetryPill(
                            label = app.instanceLabel,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onInspectClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("inspect_app_button_${app.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Inspect ${app.displayName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCloneAnotherClick()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("clone_app_button_${app.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Clone another ${app.displayName} instance",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("remove_app_button_${app.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove ${app.displayName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryPill(
                    label = app.performanceMode,
                    icon = Icons.Rounded.Bolt,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = accentColor
                )
                TelemetryPill(
                    label = "${app.launchCount} launches",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onLaunchClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("launch_app_button_${app.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.launch_in_space))
                }
            }
        }
    }
}

@Composable
private fun EmptySpaceCard(
    spaceName: String,
    onImportClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_space),
                contentDescription = stringResource(R.string.cd_empty_space),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
            Text(
                text = "$spaceName: ${stringResource(R.string.empty_space_title)}",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.empty_space_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onImportClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("empty_import_apps_button")
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.import_apps_cta))
            }
        }
    }
}
