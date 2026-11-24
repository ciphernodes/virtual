package com.example.ui.navigation

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
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.OctoViewModel
import com.example.ui.components.VirtualLaunchSessionDialog
import com.example.ui.screens.PermissionGateScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.SandboxConfigScreen
import com.example.ui.screens.SpacesScreen

const val ROUTE_SPACES = "spaces"
const val ROUTE_PERMISSIONS = "permissions"
const val ROUTE_SANDBOX = "sandbox"

private data class OctoNavItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val NAV_ITEMS = listOf(
    OctoNavItem(ROUTE_SPACES, R.string.nav_spaces, Icons.Rounded.Layers, Icons.Outlined.Layers),
    OctoNavItem(ROUTE_PERMISSIONS, R.string.nav_permissions, Icons.Rounded.VerifiedUser, Icons.Outlined.VerifiedUser),
    OctoNavItem(ROUTE_SANDBOX, R.string.nav_sandbox, Icons.Rounded.Tune, Icons.Outlined.Tune)
)

@Composable
fun OctoApp(viewModel: OctoViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

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
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    if (!uiState.deviceBridge.allRequiredGranted) {
        PermissionGateScreen(
            uiState = uiState,
            missingPermissions = viewModel::getMissingRuntimePermissions,
            onPermissionsResult = viewModel::onPermissionsResult,
            onStorageTreePicked = viewModel::onStorageTreePicked
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

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NAV_ITEMS.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag("nav_${item.route}")
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
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .statusBarsPadding()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        NAV_ITEMS.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag("nav_rail_${item.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_SPACES,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_SPACES) {
                        SpacesScreen(
                            uiState = uiState,
                            onSelectSpace = viewModel::selectSpace,
                            onCreateSpace = viewModel::createSpace,
                            onEditSpace = viewModel::editSpace,
                            onDeleteSpace = viewModel::deleteSpace,
                            onAddApp = viewModel::addAppToActiveSpace,
                            onDuplicateApp = viewModel::duplicateVirtualApp,
                            onRefreshInstalledApps = viewModel::refreshInstalledApps,
                            onOpenLaunchSession = viewModel::openLaunchSession,
                            onOpenPermissionsTab = { navigateToTab(ROUTE_PERMISSIONS) },
                            onOpenSandboxConfig = { navigateToTab(ROUTE_SANDBOX) },
                            onRemoveVirtualApp = viewModel::removeVirtualApp
                        )
                    }
                    composable(ROUTE_PERMISSIONS) {
                        PermissionsScreen(
                            uiState = uiState,
                            missingPermissions = viewModel::getMissingRuntimePermissions,
                            onPermissionsResult = viewModel::onPermissionsResult,
                            onRefreshBridge = viewModel::refreshDeviceBridge,
                            onVaultUriPicked = viewModel::onVaultUriPicked,
                            onRemoveVaultUri = viewModel::removeVaultUri,
                            onStorageTreePicked = viewModel::onStorageTreePicked
                        )
                    }
                    composable(ROUTE_SANDBOX) {
                        SandboxConfigScreen(
                            uiState = uiState,
                            onUpdateSpace = viewModel::updateSpace,
                            onResetSpaceDefaults = viewModel::resetSpaceDefaults,
                            onToggleTheme = viewModel::toggleTheme,
                            onRunIntegrityCheck = viewModel::runIntegrityCheckNow,
                            onTogglePeriodicIntegrity = viewModel::setPeriodicIntegrityEnabled,
                            onRefreshResources = viewModel::refreshResourceTelemetry
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
            appIcon = uiState.appIcons[launchApp.packageName],
            hostDisplaySummary = uiState.deviceBridge.hostDisplaySummary,
            grantedPermissionsCount = uiState.deviceBridge.grantedCount,
            totalPermissionsCount = uiState.deviceBridge.totalCount,
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
            onOpenPermissions = {
                viewModel.dismissLaunchSession()
                navigateToTab(ROUTE_PERMISSIONS)
            },
            onLaunchNow = {
                viewModel.launchAppFromSession(launchApp)
            },
            onDismiss = viewModel::dismissLaunchSession
        )
    }
}
