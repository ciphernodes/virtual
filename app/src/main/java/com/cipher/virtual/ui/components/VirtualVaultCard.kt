package com.cipher.virtual.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cipher.virtual.R
import com.cipher.virtual.ui.theme.LocalNeuPalette
import com.cipher.virtual.ui.theme.NeuCard
import com.cipher.virtual.ui.theme.NeuIconButton
import com.cipher.virtual.ui.theme.NeuTonalButton
import com.cipher.virtual.ui.theme.NeuWell

@Composable
fun VirtualVaultCard(
    attachedVaultUris: List<Uri>,
    hasStorageFolderGrant: Boolean,
    onVaultUriPicked: (Uri?) -> Unit,
    onRemoveVaultUri: (Uri) -> Unit,
    onStorageTreePicked: (Uri?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val palette = LocalNeuPalette.current
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        onVaultUriPicked(uri)
    }
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        onStorageTreePicked(uri)
    }

    NeuCard(
        cornerRadius = 24.dp,
        elevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("virtual_vault_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.virtual_vault_title), style = MaterialTheme.typography.titleMedium)
                }
                TelemetryPill(
                    label = stringResource(
                        if (hasStorageFolderGrant) R.string.vault_storage_granted_badge
                        else R.string.vault_storage_not_granted_badge
                    ),
                    containerColor = if (hasStorageFolderGrant) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        palette.pressedSurface
                    },
                    contentColor = if (hasStorageFolderGrant) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.testTag(
                        if (hasStorageFolderGrant) "vault_storage_granted_badge"
                        else "vault_storage_not_granted_badge"
                    )
                )
            }

            Text(
                text = stringResource(R.string.virtual_vault_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeuTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    accentColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vault_import_media_button")
                ) {
                    Icon(Icons.Rounded.PermMedia, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.vault_import_media_action))
                }
                NeuTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        folderPickerLauncher.launch(null)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vault_select_folder_button")
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.vault_grant_folder_action))
                }
            }

            Text(
                text = stringResource(R.string.vault_attachments_header_format, attachedVaultUris.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (attachedVaultUris.isEmpty()) {
                NeuWell(
                    cornerRadius = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.vault_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    attachedVaultUris.forEachIndexed { index, uri ->
                        NeuWell(
                            cornerRadius = 14.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: uri.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                NeuIconButton(
                                    onClick = { onRemoveVaultUri(uri) },
                                    size = 48.dp,
                                    modifier = Modifier.testTag("remove_vault_uri_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_remove_vault_uri),
                                        modifier = Modifier.size(18.dp)
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
