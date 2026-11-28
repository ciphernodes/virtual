package com.cipher.virtual.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cipher.virtual.R
import com.cipher.virtual.data.hardware.DeviceProfile
import com.cipher.virtual.data.local.InstanceWorkspaceStats
import com.cipher.virtual.data.model.InstalledAppInfo
import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.data.model.VirtualSpaceEntity
import com.cipher.virtual.ui.components.AppIconBadge
import com.cipher.virtual.ui.components.TelemetryPill
import com.cipher.virtual.ui.theme.NeuCard
import com.cipher.virtual.ui.theme.NeuTonalButton
import com.cipher.virtual.ui.theme.NeuWell
import com.cipher.virtual.ui.theme.SpaceAccentColors
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun InstanceHeaderCard(
    app: VirtualAppEntity,
    appIcon: ImageBitmap?,
    installedMeta: InstalledAppInfo?,
    spaceAccent: SpaceAccentColors,
    dateFormatter: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    NeuCard(
        cornerRadius = 24.dp,
        elevation = 6.dp,
        accentTint = spaceAccent.accent,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconBadge(
                    appName = app.displayName,
                    icon = appIcon,
                    size = 58.dp,
                    accentColor = spaceAccent.accent
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TelemetryPill(
                    label = stringResource(R.string.launches_count_format, app.launchCount),
                    containerColor = spaceAccent.container,
                    contentColor = spaceAccent.onContainer
                )
            }

            NeuWell(
                cornerRadius = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    installedMeta?.let { meta ->
                        DetailInfoRow(
                            stringResource(R.string.detail_version_label),
                            stringResource(R.string.detail_version_value_format, meta.versionName)
                        )
                        DetailInfoRow(
                            stringResource(R.string.detail_target_sdk_label),
                            stringResource(R.string.detail_target_sdk_value_format, meta.targetSdk)
                        )
                        if (meta.apkSizeMb >= 0.1f) {
                            DetailInfoRow(
                                stringResource(R.string.detail_apk_size_label),
                                stringResource(R.string.detail_apk_size_value_format, meta.apkSizeMb)
                            )
                        }
                    }
                    DetailInfoRow(stringResource(R.string.detail_added_label), dateFormatter.format(Date(app.addedAt)))
                    DetailInfoRow(
                        stringResource(R.string.detail_last_launched_label),
                        app.lastLaunchedAt?.let { dateFormatter.format(Date(it)) }
                            ?: stringResource(R.string.detail_never)
                    )
                    DetailInfoRow(
                        stringResource(R.string.detail_last_profile_id_label),
                        app.lastProfileId?.take(8) ?: stringResource(R.string.detail_none_recorded)
                    )
                }
            }
        }
    }
}

@Composable
internal fun EnvironmentProfileCard(
    space: VirtualSpaceEntity?,
    activeProfile: DeviceProfile?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    NeuCard(
        cornerRadius = 24.dp,
        elevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.DeveloperBoard, contentDescription = null, tint = accentColor)
                Text(stringResource(R.string.assigned_env_profile_title), style = MaterialTheme.typography.titleMedium)
            }
            NeuWell(
                cornerRadius = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailInfoRow(
                        stringResource(R.string.detail_workspace_label),
                        "${space?.name ?: stringResource(R.string.space_fallback_name)} (${space?.tag ?: "MAIN"})"
                    )
                    DetailInfoRow(stringResource(R.string.detail_preset_label), space?.deviceModelPreset ?: "ROG Phone 8 Pro")
                    DetailInfoRow(
                        stringResource(R.string.detail_virtual_dpi_label),
                        stringResource(R.string.dpi_value_format, space?.virtualDpi ?: 440)
                    )
                    activeProfile?.let { profile ->
                        DetailInfoRow(stringResource(R.string.detail_model_label), "${profile.manufacturer} ${profile.model}")
                        DetailInfoRow(stringResource(R.string.detail_product_device_label), "${profile.product} / ${profile.device}")
                        DetailInfoRow(stringResource(R.string.detail_board_hardware_label), "${profile.board} / ${profile.hardware}")
                        DetailInfoRow(
                            stringResource(R.string.detail_android_os_label),
                            stringResource(R.string.android_os_value_format, profile.androidVersion, profile.sdkInt)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun InstanceStorageCard(
    inspectedWorkerSlot: String?,
    workspaceStats: InstanceWorkspaceStats?,
    onResetWorkspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeuCard(
        cornerRadius = 24.dp,
        elevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.isolated_workspace_storage_title), style = MaterialTheme.typography.titleMedium)
            }
            NeuWell(
                cornerRadius = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailInfoRow(
                        stringResource(R.string.worker_slot_label),
                        inspectedWorkerSlot ?: stringResource(R.string.worker_slot_pending)
                    )
                    DetailInfoRow(
                        stringResource(R.string.files_recorded_label),
                        stringResource(
                            R.string.files_recorded_format,
                            workspaceStats?.fileCount ?: 0,
                            workspaceStats?.sizeBytes ?: 0L
                        )
                    )
                    Text(
                        text = workspaceStats?.directoryPath ?: stringResource(R.string.workspace_not_initialized),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            NeuTonalButton(
                onClick = onResetWorkspace,
                accentColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_instance_workspace_button")
            ) {
                Icon(Icons.Rounded.FolderDelete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.reset_workspace_data))
            }
        }
    }
}

@Composable
internal fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
