package com.cipher.virtual.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cipher.virtual.R
import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.data.model.VirtualSpaceEntity
import com.cipher.virtual.ui.components.AppIconBadge
import com.cipher.virtual.ui.components.TelemetryPill
import com.cipher.virtual.ui.theme.LocalNeuPalette
import com.cipher.virtual.ui.theme.NeuButton
import com.cipher.virtual.ui.theme.NeuCard
import com.cipher.virtual.ui.theme.NeuIconButton
import com.cipher.virtual.ui.theme.NeuWell
import com.cipher.virtual.ui.theme.spaceAccentColors

@Composable
fun HeroWorkspaceBanner(
    activeSpace: VirtualSpaceEntity?,
    appCount: Int,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spaceAccent = spaceAccentColors(activeSpace?.accentIndex ?: 0)
    val palette = LocalNeuPalette.current

    NeuCard(
        cornerRadius = 26.dp,
        elevation = 7.dp,
        accentTint = spaceAccent.accent,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner),
                contentDescription = stringResource(R.string.cd_hero_banner),
                contentScale = ContentScale.Crop,
                alpha = if (palette.isDark) 0.34f else 0.22f,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                palette.base.copy(alpha = 0.28f),
                                palette.raisedSurface.copy(alpha = 0.94f)
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
                    NeuButton(
                        onClick = onImportClick,
                        containerColor = spaceAccent.accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("hero_import_app_button")
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
fun VirtualAppCard(
    app: VirtualAppEntity,
    icon: ImageBitmap?,
    accentColor: Color,
    onLaunchClick: () -> Unit,
    onInspectClick: () -> Unit,
    onCloneAnotherClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val palette = LocalNeuPalette.current

    NeuCard(
        cornerRadius = 22.dp,
        elevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = ripple(),
                interactionSource = null,
                onClick = onLaunchClick
            )
            .testTag("virtual_app_card_${app.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppIconBadge(
                    appName = app.displayName,
                    icon = icon,
                    size = 54.dp,
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
                NeuIconButton(
                    onClick = onInspectClick,
                    size = 48.dp,
                    modifier = Modifier.testTag("inspect_app_button_${app.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = stringResource(R.string.cd_inspect_app_format, app.displayName),
                        modifier = Modifier.size(18.dp)
                    )
                }
                NeuIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCloneAnotherClick()
                    },
                    size = 48.dp,
                    modifier = Modifier.testTag("clone_app_button_${app.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.cd_clone_app_format, app.displayName),
                        modifier = Modifier.size(18.dp)
                    )
                }
                NeuIconButton(
                    onClick = onRemoveClick,
                    size = 48.dp,
                    modifier = Modifier.testTag("remove_app_button_${app.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_remove_app_format, app.displayName),
                        modifier = Modifier.size(18.dp)
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
                    containerColor = palette.pressedSurface,
                    contentColor = accentColor
                )
                TelemetryPill(
                    label = stringResource(R.string.launches_count_format, app.launchCount),
                    containerColor = palette.pressedSurface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                NeuButton(
                    onClick = onLaunchClick,
                    containerColor = accentColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("launch_app_button_${app.id}")
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
fun EmptySpaceCard(
    spaceName: String,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeuCard(
        cornerRadius = 26.dp,
        elevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NeuWell(
                cornerRadius = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_space),
                    contentDescription = stringResource(R.string.cd_empty_space),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
            }
            Text(
                text = "$spaceName: ${stringResource(R.string.empty_space_title)}",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.empty_space_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NeuButton(
                onClick = onImportClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_import_apps_button")
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.import_apps_cta))
            }
        }
    }
}
