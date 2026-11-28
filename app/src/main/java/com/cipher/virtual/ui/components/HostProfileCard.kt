package com.cipher.virtual.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cipher.virtual.R
import com.cipher.virtual.data.hardware.DeviceProfile
import com.cipher.virtual.data.model.VirtualSpaceEntity
import com.cipher.virtual.ui.theme.NeuCard
import com.cipher.virtual.ui.theme.NeuWell

@Composable
fun HostProfileCard(
    activeSpace: VirtualSpaceEntity?,
    activeDeviceProfile: DeviceProfile?,
    hostDisplaySummary: String,
    networkSummary: String,
    isNativeEngineAvailable: Boolean = false,
    nativeEngineLoadWarning: String? = null,
    modifier: Modifier = Modifier
) {
    NeuCard(
        cornerRadius = 24.dp,
        elevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("security_dashboard_host_profile_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(R.string.host_workspace_profile_title), style = MaterialTheme.typography.titleMedium)
            }
            NeuWell(
                cornerRadius = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardMetricRow(
                        label = stringResource(R.string.host_hardware_label),
                        value = "${Build.MANUFACTURER} ${Build.MODEL}"
                    )
                    DashboardMetricRow(
                        label = stringResource(R.string.android_os_label),
                        value = stringResource(R.string.android_os_value_format, Build.VERSION.RELEASE, Build.VERSION.SDK_INT)
                    )
                    DashboardMetricRow(
                        label = stringResource(R.string.host_panel_label),
                        value = hostDisplaySummary,
                        icon = Icons.Rounded.Storage
                    )
                    DashboardMetricRow(
                        label = stringResource(R.string.network_link_label),
                        value = networkSummary,
                        icon = Icons.Rounded.Wifi
                    )
                    DashboardMetricRow(
                        label = stringResource(R.string.native_engine_label),
                        value = if (isNativeEngineAvailable) {
                            stringResource(R.string.native_engine_loaded)
                        } else {
                            stringResource(
                                R.string.native_engine_unavailable_format,
                                nativeEngineLoadWarning ?: "not built"
                            )
                        }
                    )
                    DashboardMetricRow(
                        label = stringResource(R.string.active_space_preset_label),
                        value = activeSpace?.let {
                            stringResource(R.string.space_preset_value_format, it.name, it.deviceModelPreset, it.virtualDpi)
                        } ?: stringResource(R.string.no_active_space)
                    )
                    activeDeviceProfile?.let { profile ->
                        DashboardMetricRow(
                            label = stringResource(R.string.profile_model_label),
                            value = "${profile.manufacturer} ${profile.model}"
                        )
                        DashboardMetricRow(
                            label = stringResource(R.string.device_board_label),
                            value = "${profile.brand}/${profile.device} • ${profile.board} (${profile.hardware})"
                        )
                        DashboardMetricRow(
                            label = stringResource(R.string.profile_os_id_label),
                            value = stringResource(
                                R.string.profile_os_id_value_format,
                                profile.androidVersion,
                                profile.sdkInt,
                                profile.profileId.take(8)
                            )
                        )
                    }
                }
            }
        }
    }
}
