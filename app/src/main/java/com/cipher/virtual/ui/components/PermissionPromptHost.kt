package com.cipher.virtual.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.cipher.virtual.R
import com.cipher.virtual.data.model.DeviceBridgeSnapshot
import com.cipher.virtual.security.PermissionManager

@Composable
fun PermissionPromptHost(
    bridge: DeviceBridgeSnapshot,
    missingRuntimePermissions: () -> Array<String>,
    onPermissionsResult: (Map<String, Boolean>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val permissionManager = remember(context) { PermissionManager(context.applicationContext) }

    var rationaleBeforeLaunch by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var showSettingsFallbackDialog by remember { mutableStateOf(false) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            onPermissionsResult(results)
            val denied = results.filterValues { !it }.keys
            if (denied.isNotEmpty()) {
                val userActivelyDeclined = activity == null || denied.any { perm ->
                    rationaleBeforeLaunch[perm] == true ||
                        ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
                }
                if (userActivelyDeclined) {
                    permissionManager.handlePermissionDenialAndExit(activity, context)
                } else {
                    showSettingsFallbackDialog = true
                }
            } else if (results.isEmpty() && missingRuntimePermissions().isNotEmpty()) {
                permissionManager.handlePermissionDenialAndExit(activity, context)
            }
        }
    )

    val missingRuntime = remember(bridge) { missingRuntimePermissions() }
    val missingKeyString = remember(missingRuntime) { missingRuntime.joinToString(",") }

    LaunchedEffect(bridge.initialized, missingKeyString, showSettingsFallbackDialog) {
        if (bridge.initialized && missingRuntime.isNotEmpty() && !showSettingsFallbackDialog) {
            rationaleBeforeLaunch = missingRuntime.associateWith { perm ->
                activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, perm) } ?: false
            }
            runtimeLauncher.launch(missingRuntime)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("permission_prompt_host")
    ) {
        if (showSettingsFallbackDialog) {
            AlertDialog(
                onDismissRequest = {
                    permissionManager.handlePermissionDenialAndExit(activity, context)
                },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Permissions Required") },
                text = {
                    Text("Cipher Virtual requires all permissions to run. Enable the missing permissions in App Settings to continue.")
                },
                confirmButton = {
                    Button(
                        onClick = { permissionManager.openAppSettings(context) },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("prompt_open_settings_button")
                    ) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            permissionManager.handlePermissionDenialAndExit(activity, context)
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("prompt_decline_settings_button")
                    ) {
                        Text("Decline")
                    }
                },
                modifier = Modifier.testTag("settings_permission_prompt_dialog")
            )
        } else if (missingRuntime.isEmpty()) {
            val nextSpecial = bridge.permissions.firstOrNull { !it.isGranted && it.isSpecialAccess }
            if (nextSpecial != null && nextSpecial.key == "battery") {
                AlertDialog(
                    onDismissRequest = {
                        permissionManager.handlePermissionDenialAndExit(activity, context)
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.battery_rationale_title)) },
                    text = { Text(stringResource(R.string.battery_rationale_body)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                permissionManager.openBatteryOptimizationPrompt(context)
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("prompt_allow_battery_button")
                        ) {
                            Text(stringResource(R.string.battery_rationale_cta))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                permissionManager.handlePermissionDenialAndExit(activity, context)
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("prompt_decline_battery_button")
                        ) {
                            Text("Decline")
                        }
                    },
                    modifier = Modifier.testTag("battery_rationale_dialog")
                )
            } else if (nextSpecial != null) {
                AlertDialog(
                    onDismissRequest = {
                        permissionManager.handlePermissionDenialAndExit(activity, context)
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text("Allow ${nextSpecial.title}") },
                    text = {
                        Text("${nextSpecial.description}. Enable this permission to continue using Cipher Virtual.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                permissionManager.openSpecialAccessSettings(nextSpecial.key, context)
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("prompt_allow_special_button")
                        ) {
                            Text("Allow")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                permissionManager.handlePermissionDenialAndExit(activity, context)
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("prompt_decline_button")
                        ) {
                            Text("Decline")
                        }
                    },
                    modifier = Modifier.testTag("special_permission_prompt_dialog")
                )
            }
        }
    }
}
