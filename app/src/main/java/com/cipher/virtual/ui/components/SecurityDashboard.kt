package com.cipher.virtual.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cipher.virtual.data.hardware.DeviceProfile
import com.cipher.virtual.data.model.VirtualSpaceEntity
import com.cipher.virtual.monitoring.ResourceTelemetrySnapshot
import com.cipher.virtual.security.IntegrityStatusSnapshot

@Composable
fun SecurityDashboard(
    integrityStatus: IntegrityStatusSnapshot,
    resourceTelemetry: ResourceTelemetrySnapshot,
    activeSpace: VirtualSpaceEntity?,
    activeDeviceProfile: DeviceProfile?,
    hostDisplaySummary: String,
    networkSummary: String,
    isTelemetryPaused: Boolean = false,
    isNativeEngineAvailable: Boolean = false,
    nativeEngineLoadWarning: String? = null,
    onRunIntegrityCheck: () -> Unit,
    onTogglePeriodicIntegrity: (Boolean) -> Unit,
    onRefreshResources: () -> Unit,
    attachedVaultUris: List<Uri> = emptyList(),
    hasStorageFolderGrant: Boolean = false,
    onVaultUriPicked: (Uri?) -> Unit = {},
    onRemoveVaultUri: (Uri) -> Unit = {},
    onStorageTreePicked: (Uri?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlayIntegrityCard(
            integrityStatus = integrityStatus,
            onRunIntegrityCheck = onRunIntegrityCheck,
            onTogglePeriodicIntegrity = onTogglePeriodicIntegrity
        )

        ResourceMonitorCard(
            resourceTelemetry = resourceTelemetry,
            isTelemetryPaused = isTelemetryPaused,
            onRefreshResources = onRefreshResources
        )

        HostProfileCard(
            activeSpace = activeSpace,
            activeDeviceProfile = activeDeviceProfile,
            hostDisplaySummary = hostDisplaySummary,
            networkSummary = networkSummary,
            isNativeEngineAvailable = isNativeEngineAvailable,
            nativeEngineLoadWarning = nativeEngineLoadWarning
        )

        VirtualVaultCard(
            attachedVaultUris = attachedVaultUris,
            hasStorageFolderGrant = hasStorageFolderGrant,
            onVaultUriPicked = onVaultUriPicked,
            onRemoveVaultUri = onRemoveVaultUri,
            onStorageTreePicked = onStorageTreePicked
        )
    }
}
