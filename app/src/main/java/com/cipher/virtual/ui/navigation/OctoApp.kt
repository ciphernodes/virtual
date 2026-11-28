package com.cipher.virtual.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cipher.virtual.R
import com.cipher.virtual.ui.OctoViewModel
import com.cipher.virtual.ui.components.PermissionPromptHost
import com.cipher.virtual.ui.components.VirtualLaunchSessionDialog
import com.cipher.virtual.ui.screens.AppDetailScreen
import com.cipher.virtual.ui.screens.SandboxConfigScreen
import com.cipher.virtual.ui.screens.SpacesScreen
import com.cipher.virtual.ui.theme.LocalNeuPalette
import com.cipher.virtual.ui.theme.neuExtruded

sealed interface OctoRoute {
    val routePattern: String

    data object Spaces : OctoRoute {
        override val routePattern: String = "spaces"
    }

    data object Sandbox : OctoRoute {
        override val routePattern: String = "sandbox"
    }

    data class AppDetail(val appId: Long) : OctoRoute {
        override val routePattern: String = routeFor(appId)

        companion object {
            const val ARG_APP_ID = "appId"
            const val PATTERN = "app_detail/{$ARG_APP_ID}"
            fun routeFor(appId: Long): String = "app_detail/$appId"
        }
    }
}

private data class OctoNavItem(
    val route: OctoRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val NAV_ITEMS = listOf(
    OctoNavItem(OctoRoute.Spaces, R.string.nav_spaces, Icons.Rounded.Layers, Icons.Outlined.Layers),
    OctoNavItem(OctoRoute.Sandbox, R.string.nav_sandbox, Icons.Rounded.Tune, Icons.Outlined.Tune)
)

@Composable
fun OctoApp(viewModel: OctoViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val neuPalette = LocalNeuPalette.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDeviceBridge()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!uiState.deviceBridge.initialized) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(neuPalette.base),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Prompt for missing permissions directly on launch/resume without displaying a permissions screen
    if (!uiState.deviceBridge.allRequiredGranted) {
        PermissionPromptHost(
            bridge = uiState.deviceBridge,
            missingRuntimePermissions = viewModel::getMissingRuntimePermissions,
            onPermissionsResult = viewModel::onPermissionsResult
        )
        return
    }

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearStatusMessage()
        }
    }

    fun navigateToTab(route: OctoRoute) {
        navController.navigate(route.routePattern) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = neuPalette.pressedSurface,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val railItemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = neuPalette.pressedSurface,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = neuPalette.base,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = neuPalette.raisedSurface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars,
                        modifier = Modifier.drawBehind {
                            drawLine(
                                color = neuPalette.bevelHighlight.copy(alpha = if (neuPalette.isDark) 0.45f else 0.85f),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    ) {
                        NAV_ITEMS.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == item.route.routePattern
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                colors = navItemColors,
                                icon = {
                                    Box(
                                        modifier = if (selected) {
                                            Modifier
                                                .neuExtruded(
                                                    cornerRadius = 14.dp,
                                                    elevation = 3.dp,
                                                    containerColor = neuPalette.raisedSurface,
                                                    accentTint = MaterialTheme.colorScheme.primary
                                                )
                                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                        } else {
                                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag("nav_${item.route.routePattern}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = neuPalette.raisedSurface,
                        modifier = Modifier
                            .statusBarsPadding()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .drawBehind {
                                drawLine(
                                    color = neuPalette.bevelShadow.copy(alpha = 0.45f),
                                    start = Offset(size.width, 0f),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                    ) {
                        NAV_ITEMS.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == item.route.routePattern
                            } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                colors = railItemColors,
                                icon = {
                                    Box(
                                        modifier = if (selected) {
                                            Modifier
                                                .neuExtruded(
                                                    cornerRadius = 14.dp,
                                                    elevation = 3.dp,
                                                    containerColor = neuPalette.raisedSurface,
                                                    accentTint = MaterialTheme.colorScheme.primary
                                                )
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        } else {
                                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag("nav_rail_${item.route.routePattern}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = OctoRoute.Spaces.routePattern,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(OctoRoute.Spaces.routePattern) {
                        SpacesScreen(
                            uiState = uiState,
                            onAddApp = viewModel::addAppToActiveSpace,
                            onDuplicateApp = viewModel::duplicateVirtualApp,
                            onRefreshInstalledApps = viewModel::refreshInstalledApps,
                            onRequestIcons = viewModel::requestIconsForPackages,
                            onOpenLaunchSession = viewModel::openLaunchSession,
                            onInspectApp = { app ->
                                navController.navigate(OctoRoute.AppDetail.routeFor(app.id))
                            },
                            onRemoveVirtualApp = viewModel::removeVirtualApp
                        )
                    }
                    composable(OctoRoute.Sandbox.routePattern) {
                        SandboxConfigScreen(
                            uiState = uiState,
                            onUpdateSpace = viewModel::updateSpace,
                            onSelectDeviceProfile = viewModel::selectDeviceProfile,
                            onSaveCustomProfile = viewModel::saveCustomDeviceProfile,
                            onDeleteCustomProfile = viewModel::deleteCustomDeviceProfile,
                            onResetSpaceDefaults = viewModel::resetSpaceDefaults,
                            onToggleTheme = viewModel::toggleTheme,
                            onRunIntegrityCheck = viewModel::runIntegrityCheckNow,
                            onTogglePeriodicIntegrity = viewModel::setPeriodicIntegrityEnabled,
                            onRefreshResources = viewModel::refreshResourceTelemetry,
                            onVaultUriPicked = viewModel::onVaultUriPicked,
                            onRemoveVaultUri = viewModel::removeVaultUri,
                            onStorageTreePicked = viewModel::onStorageTreePicked
                        )
                    }
                    composable(
                        route = OctoRoute.AppDetail.PATTERN,
                        arguments = listOf(navArgument(OctoRoute.AppDetail.ARG_APP_ID) { type = NavType.LongType })
                    ) { entry ->
                        val appId = entry.arguments?.getLong(OctoRoute.AppDetail.ARG_APP_ID) ?: -1L
                        AppDetailScreen(
                            appId = appId,
                            uiState = uiState,
                            hasWorkProfileTarget = viewModel::hasWorkProfileTarget,
                            onInspectWorkspace = viewModel::inspectInstanceWorkspace,
                            onUpdateApp = viewModel::updateVirtualApp,
                            onCloneApp = viewModel::duplicateVirtualApp,
                            onRemoveApp = viewModel::removeVirtualApp,
                            onResetWorkspace = viewModel::resetInstanceWorkspace,
                            onLaunchApp = viewModel::launchAppFromSession,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    uiState.launchSessionApp?.let { launchApp ->
        val space = uiState.spaces.firstOrNull { it.id == launchApp.spaceId } ?: uiState.activeSpace
        VirtualLaunchSessionDialog(
            app = launchApp,
            space = space,
            activeDeviceProfile = uiState.activeDeviceProfile,
            appIcon = uiState.appIcons[launchApp.packageName],
            hostDisplaySummary = uiState.deviceBridge.hostDisplaySummary,
            hasWorkProfile = viewModel.hasWorkProfileTarget(launchApp.packageName),
            onChangePerformanceMode = { mode ->
                viewModel.updateVirtualApp(launchApp.copy(performanceMode = mode))
            },
            onRenameInstance = { newLabel ->
                viewModel.updateVirtualApp(launchApp.copy(instanceLabel = newLabel))
            },
            onCloneInstance = {
                viewModel.duplicateVirtualApp(launchApp)
                viewModel.dismissLaunchSession()
            },
            onInspectDetails = {
                viewModel.dismissLaunchSession()
                navController.navigate(OctoRoute.AppDetail.routeFor(launchApp.id))
            },
            onLaunchNow = { preferWork ->
                viewModel.launchAppFromSession(launchApp, preferWorkProfile = preferWork)
            },
            onDismiss = viewModel::dismissLaunchSession
        )
    }
}
