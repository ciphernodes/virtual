package com.cipher.virtual.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cipher.virtual.R
import com.cipher.virtual.data.hardware.DeviceProfile
import com.cipher.virtual.data.hardware.DeviceProfileManager
import com.cipher.virtual.ui.theme.LocalNeuPalette
import com.cipher.virtual.ui.theme.NeuButton
import com.cipher.virtual.ui.theme.NeuWell
import com.cipher.virtual.ui.theme.neuTextFieldColors
import kotlin.math.roundToInt

@Composable
fun CustomDeviceProfileDialog(
    baseProfile: DeviceProfile,
    isEditing: Boolean,
    onSave: (DeviceProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNeuPalette.current
    val defaultPresetName = stringResource(R.string.default_custom_profile_name)
    var displayName by remember(baseProfile.profileId, isEditing) {
        mutableStateOf(if (isEditing) baseProfile.displayName else defaultPresetName)
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

    val fieldShape = RoundedCornerShape(14.dp)
    val fieldColors = neuTextFieldColors()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.base,
        shape = MaterialTheme.shapes.large,
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
                    label = { Text(stringResource(R.string.preset_name_label)) },
                    singleLine = true,
                    shape = fieldShape,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_profile_name_input")
                )
                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = { Text(stringResource(R.string.preset_manufacturer_label)) },
                    singleLine = true,
                    shape = fieldShape,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.preset_model_label)) },
                    singleLine = true,
                    shape = fieldShape,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text(stringResource(R.string.preset_brand_label)) },
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = device,
                        onValueChange = { device = it },
                        label = { Text(stringResource(R.string.preset_device_label)) },
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = product,
                    onValueChange = { product = it },
                    label = { Text(stringResource(R.string.preset_product_label)) },
                    singleLine = true,
                    shape = fieldShape,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = board,
                        onValueChange = { board = it },
                        label = { Text(stringResource(R.string.preset_board_label)) },
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hardware,
                        onValueChange = { hardware = it },
                        label = { Text(stringResource(R.string.preset_hardware_label)) },
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = androidVersion,
                    onValueChange = { androidVersion = it },
                    label = { Text(stringResource(R.string.preset_android_version_label)) },
                    singleLine = true,
                    shape = fieldShape,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                NeuWell(
                    cornerRadius = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.preset_target_sdk_level), style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = stringResource(R.string.preset_api_value_format, sdkInt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = sdkInt.toFloat(),
                            onValueChange = { sdkInt = it.roundToInt().coerceIn(26, 36) },
                            valueRange = 26f..36f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = palette.base
                            ),
                            modifier = Modifier.testTag("custom_profile_sdk_slider")
                        )
                    }
                }
                NeuWell(
                    cornerRadius = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.preset_density_dpi_label), style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = stringResource(R.string.dpi_value_format, densityDpi),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = densityDpi.toFloat(),
                            onValueChange = { densityDpi = ((it / 20f).roundToInt() * 20).coerceIn(160, 640) },
                            valueRange = 160f..640f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = palette.base
                            ),
                            modifier = Modifier.testTag("custom_profile_dpi_slider")
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("custom_profile_cancel_button")
            ) {
                Text(stringResource(R.string.cancel_action))
            }
        },
        confirmButton = {
            NeuButton(
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
                modifier = Modifier.testTag("save_custom_profile_button")
            ) {
                Text(
                    stringResource(
                        if (isEditing) R.string.update_preset_action
                        else R.string.save_preset_action
                    )
                )
            }
        }
    )
}
