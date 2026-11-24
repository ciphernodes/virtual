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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.hardware.DeviceProfile
import com.example.data.hardware.DeviceProfileManager
import com.example.data.model.VirtualSpaceEntity
import com.example.ui.OctoUiState
import com.example.ui.components.SecurityDashboard
import com.example.ui.theme.spaceAccentColors
import kotlin.math.roundToInt

@Composable
fun SandboxConfigScreen(
    uiState: OctoUiState,
    onUpdateSpace: (VirtualSpaceEntity) -> Unit,
    onSelectDeviceProfile: (VirtualSpaceEntity, DeviceProfile) -> Unit,
    onSaveCustomProfile: (VirtualSpaceEntity, DeviceProfile) -> Unit,
    onDeleteCustomProfile: (VirtualSpaceEntity?, String) -> Unit,
    onResetSpaceDefaults: (VirtualSpaceEntity) -> Unit,
    onToggleTheme: () -> Unit,
    onRunIntegrityCheck: () -> Unit,
    onTogglePeriodicIntegrity: (Boolean) -> Unit,
    onRefreshResources: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSpace = uiState.activeSpace
    val spaceAccent = spaceAccentColors(activeSpace?.accentIndex ?: 0)
    val haptics = LocalHapticFeedback.current
    var showCustomProfileDialog by remember { mutableStateOf(false) }
    var editingCustomProfile by remember { mutableStateOf<DeviceProfile?>(null) }

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
                .testTag("sandbox_config_list")
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sandbox_settings_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Configure ${activeSpace?.name ?: "Space"} environment & runtime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleTheme()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.darkThemeEnabled) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Toggle theme mode"
                        )
                    }
                }
            }

            if (activeSpace != null) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                spaceAccent.accent.copy(alpha = 0.35f),
                                MaterialTheme.shapes.large
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.DeveloperBoard, contentDescription = null, tint = spaceAccent.accent)
                                    Text("Display & Hardware Preset", style = MaterialTheme.typography.titleMedium)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        editingCustomProfile = null
                                        showCustomProfileDialog = true
                                    },
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("new_custom_profile_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Custom")
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.availableDeviceProfiles, key = { it.profileId }) { profile ->
                                    val selected = activeSpace.deviceModelPreset.equals(profile.presetKey, ignoreCase = true)
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelectDeviceProfile(activeSpace, profile)
                                        },
                                        label = { Text(profile.displayName) },
                                        leadingIcon = if (profile.isCustom) {
                                            {
                                                IconButton(
                                                    onClick = {
                                                        editingCustomProfile = profile
                                                        showCustomProfileDialog = true
                                                    },
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .testTag("edit_custom_profile_${profile.profileId}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Edit,
                                                        contentDescription = "Edit ${profile.displayName}",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        } else null,
                                        trailingIcon = if (profile.isCustom) {
                                            {
                                                IconButton(
                                                    onClick = { onDeleteCustomProfile(activeSpace, profile.profileId) },
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .testTag("delete_custom_profile_${profile.profileId}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = "Delete ${profile.displayName}",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = spaceAccent.container,
                                            selectedLabelColor = spaceAccent.onContainer
                                        ),
                                        modifier = Modifier.testTag("device_preset_${profile.presetKey}")
                                    )
                                }
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Virtual Display DPI", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${activeSpace.virtualDpi} dpi",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = spaceAccent.accent
                                    )
                                }
                                Slider(
                                    value = activeSpace.virtualDpi.toFloat(),
                                    onValueChange = {
                                        val snapped = (it / 20f).roundToInt() * 20
                                        if (snapped != activeSpace.virtualDpi) {
                                            onUpdateSpace(activeSpace.copy(virtualDpi = snapped))
                                        }
                                    },
                                    valueRange = 240f..640f
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SandboxToggleRow(
                                icon = Icons.Rounded.Security,
                                title = "Isolated Storage Profile",
                                subtitle = "Separate sandbox metadata & per-instance workspace records",
                                checked = activeSpace.isolatedStorage,
                                testTag = "toggle_isolated_storage",
                                onCheckedChange = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateSpace(activeSpace.copy(isolatedStorage = it))
                                }
                            )
                            SandboxToggleRow(
                                icon = Icons.Rounded.Speed,
                                title = "Frame-Rate Telemetry HUD",
                                subtitle = "Display live CPU sparkline & stability telemetry on Spaces",
                                checked = activeSpace.frameRateHud,
                                testTag = "toggle_fps_hud",
                                onCheckedChange = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateSpace(activeSpace.copy(frameRateHud = it))
                                }
                            )
                            SandboxToggleRow(
                                icon = Icons.Rounded.VerifiedUser,
                                title = "Device Permission Bridge",
                                subtitle = "Expose granted Location, Contacts & Media access to this space",
                                checked = activeSpace.permissionBridgeEnabled,
                                testTag = "toggle_permission_bridge",
                                onCheckedChange = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateSpace(activeSpace.copy(permissionBridgeEnabled = it))
                                }
                            )
                            FilledTonalButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onResetSpaceDefaults(activeSpace)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("reset_space_defaults_button")
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reset ${activeSpace.name} Defaults")
                            }
                        }
                    }
                }
            }

            item {
                SecurityDashboard(
                    integrityStatus = uiState.integrityStatus,
                    resourceTelemetry = uiState.resourceTelemetry,
                    activeSpace = activeSpace,
                    activeDeviceProfile = uiState.activeDeviceProfile,
                    hostDisplaySummary = uiState.deviceBridge.hostDisplaySummary,
                    networkSummary = uiState.deviceBridge.networkSummary,
                    isTelemetryPaused = uiState.isTelemetryPausedInBackground,
                    onRunIntegrityCheck = onRunIntegrityCheck,
                    onTogglePeriodicIntegrity = onTogglePeriodicIntegrity,
                    onRefreshResources = onRefreshResources
                )
            }
        }
    }

    if (showCustomProfileDialog && activeSpace != null) {
        val base = editingCustomProfile ?: uiState.activeDeviceProfile ?: DeviceProfileManager.pixel8()
        CustomDeviceProfileDialog(
            baseProfile = base,
            isEditing = editingCustomProfile != null,
            onSave = { customProfile ->
                onSaveCustomProfile(activeSpace, customProfile)
                showCustomProfileDialog = false
                editingCustomProfile = null
            },
            onDismiss = {
                showCustomProfileDialog = false
                editingCustomProfile = null
            }
        )
    }
}

@Composable
private fun CustomDeviceProfileDialog(
    baseProfile: DeviceProfile,
    isEditing: Boolean,
    onSave: (DeviceProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var displayName by remember(baseProfile.profileId, isEditing) {
        mutableStateOf(if (isEditing) baseProfile.displayName else "Custom Lab")
    }
    var manufacturer by remember(baseProfile.profileId, isEditing) { mutableStateOf(baseProfile.manufacturer) }
    var model by remember(baseProfile.profileId, isEditing) { mutableStateOf(baseProfile.model) }
    var brand by remember(baseProfile.profileId, isEditing) { mutableStateOf(baseProfile.brand) }
    var device by remember(baseProfile.profileId, isEditing) { mutableStateOf(baseProfile.device) }
    var product by remember(baseProfile.profileId, isEditing) { mutableStateOf(baseProfile.product) }
    var board by remember(baseProfile.profileId, isEditing) { mutableStateOf(baseProfile.board) }
    var hardware by remember(baseProfile.profileId, isEditing) { mutableStateOf(baseProfile.hardware) }
    var androidVersion by remember(baseProfile.profileId, isEditing) { mutableStateOf(baseProfile.androidVersion) }
    var sdkInt by remember(baseProfile.profileId, isEditing) { mutableIntStateOf(baseProfile.sdkInt.coerceIn(26, 36)) }
    var densityDpi by remember(baseProfile.profileId, isEditing) { mutableIntStateOf(baseProfile.densityDpi.coerceIn(160, 640)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isEditing) R.string.custom_profile_edit_title
                    else R.string.custom_profile_create_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("custom_profile_name_input")
                )
                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = { Text("Manufacturer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Brand") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = device,
                        onValueChange = { device = it },
                        label = { Text("Device") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = product,
                    onValueChange = { product = it },
                    label = { Text("Product") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = board,
                        onValueChange = { board = it },
                        label = { Text("Board") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hardware,
                        onValueChange = { hardware = it },
                        label = { Text("Hardware") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = androidVersion,
                    onValueChange = { androidVersion = it },
                    label = { Text("Android Version Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Target SDK Level", style = MaterialTheme.typography.labelLarge)
                        Text("API $sdkInt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = sdkInt.toFloat(),
                        onValueChange = { sdkInt = it.roundToInt().coerceIn(26, 36) },
                        valueRange = 26f..36f,
                        steps = 9
                    )
                }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Preset Density DPI", style = MaterialTheme.typography.labelLarge)
                        Text("$densityDpi dpi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = densityDpi.toFloat(),
                        onValueChange = { densityDpi = ((it / 20f).roundToInt() * 20).coerceIn(160, 640) },
                        valueRange = 160f..640f
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                enabled = displayName.isNotBlank() && model.isNotBlank(),
                onClick = {
                    val builder = DeviceProfileManager.CustomProfileBuilder(baseProfile)
                        .displayName(displayName)
                        .manufacturer(manufacturer)
                        .model(model)
                        .brand(brand)
                        .device(device)
                        .product(product.ifBlank { device })
                        .board(board)
                        .hardware(hardware)
                        .androidVersion(androidVersion)
                        .sdkInt(sdkInt)
                        .densityDpi(densityDpi)
                    if (isEditing) {
                        builder.profileId(baseProfile.profileId)
                    }
                    onSave(builder.build())
                },
                modifier = Modifier.height(48.dp).testTag("save_custom_profile_button")
            ) {
                Text(if (isEditing) "Update Preset" else "Save Preset")
            }
        }
    )
}

@Composable
private fun SandboxToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
