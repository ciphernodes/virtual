package com.cipher.virtual.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cipher.virtual.R
import com.cipher.virtual.security.IntegrityStatusSnapshot
import com.cipher.virtual.security.IntegrityVerificationState
import com.cipher.virtual.ui.theme.NeuCard
import com.cipher.virtual.ui.theme.NeuTonalButton
import com.cipher.virtual.ui.theme.NeuWell
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlayIntegrityCard(
    integrityStatus: IntegrityStatusSnapshot,
    onRunIntegrityCheck: () -> Unit,
    onTogglePeriodicIntegrity: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val badge = when (integrityStatus.state) {
        IntegrityVerificationState.VERIFIED -> DashboardBadge(
            stringResource(R.string.badge_verified),
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        IntegrityVerificationState.TOKEN_ISSUED -> DashboardBadge(
            stringResource(R.string.badge_token_issued),
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        IntegrityVerificationState.VERIFYING -> DashboardBadge(
            stringResource(R.string.badge_verifying),
            Icons.Rounded.Refresh,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        IntegrityVerificationState.CONFIGURATION_REQUIRED -> DashboardBadge(
            stringResource(R.string.badge_setup_needed),
            Icons.Rounded.WarningAmber,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        IntegrityVerificationState.VERDICT_FAILED -> DashboardBadge(
            stringResource(R.string.badge_failed),
            Icons.Rounded.WarningAmber,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        IntegrityVerificationState.ERROR -> DashboardBadge(
            stringResource(R.string.badge_unverified),
            Icons.Rounded.WarningAmber,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        IntegrityVerificationState.IDLE -> DashboardBadge(
            stringResource(R.string.badge_idle),
            Icons.Rounded.VerifiedUser,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    NeuCard(
        cornerRadius = 24.dp,
        elevation = 6.dp,
        accentTint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .testTag("security_dashboard_integrity_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.play_integrity_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                TelemetryPill(
                    label = badge.label,
                    icon = badge.icon,
                    containerColor = badge.containerColor,
                    contentColor = badge.contentColor
                )
            }

            NeuWell(
                cornerRadius = 14.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = integrityStatus.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    integrityStatus.lastCheckedAtMillis?.let { checkedAt ->
                        val formattedTime = timeFormatter.format(Date(checkedAt))
                        val storeLabel = stringResource(
                            if (integrityStatus.playStorePresent) R.string.store_detected else R.string.store_unavailable
                        )
                        Text(
                            text = stringResource(R.string.integrity_last_check_format, formattedTime, storeLabel),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    integrityStatus.lastTokenPreview?.let { preview ->
                        Text(
                            text = stringResource(R.string.integrity_token_format, preview),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            integrityStatus.lastVerdict?.let { verdict ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    verdict.appRecognitionVerdict?.let { appVerdict ->
                        TelemetryPill(
                            label = stringResource(R.string.integrity_app_verdict_format, appVerdict),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    verdict.deviceRecognitionVerdict?.let { deviceVerdict ->
                        TelemetryPill(
                            label = deviceVerdict,
                            containerColor = if (verdict.verified) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            contentColor = if (verdict.verified) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }
            }

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
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.periodic_integrity_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.periodic_integrity_subtitle_format, integrityStatus.intervalMinutes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = integrityStatus.isPeriodicEnabled,
                    onCheckedChange = onTogglePeriodicIntegrity,
                    modifier = Modifier.testTag("toggle_periodic_integrity")
                )
            }

            NeuTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRunIntegrityCheck()
                },
                enabled = integrityStatus.state != IntegrityVerificationState.VERIFYING,
                accentColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verify_play_integrity_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.play_integrity_verify_now))
            }
        }
    }
}
