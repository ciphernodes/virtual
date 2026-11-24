package com.example

import com.ciphernodes.ciphervirtual.monitoring.ResourceTelemetrySnapshot
import com.ciphernodes.ciphervirtual.monitoring.SpaceResourceUsage
import com.ciphernodes.ciphervirtual.monitoring.StabilityLevel
import com.ciphernodes.ciphervirtual.security.IntegrityStatusSnapshot
import com.ciphernodes.ciphervirtual.security.IntegrityVerdictResult
import com.ciphernodes.ciphervirtual.security.IntegrityVerificationState
import com.example.data.model.DeviceBridgeSnapshot
import com.example.data.model.InstalledAppInfo
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import com.example.ui.OctoUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CipherVirtualUnitTests {

    @Test
    fun resourceTelemetrySnapshot_computesUsedRamStorageAndHistorySafely() {
        val normal = ResourceTelemetrySnapshot(
            systemAvailableRamMb = 2048L,
            systemTotalRamMb = 8192L,
            storageAvailableGb = 32f,
            storageTotalGb = 128f,
            cpuHistory = listOf(4.5f, 12.0f, 8.2f),
            batteryPercent = 84,
            isCharging = true,
            thermalLabel = "Nominal"
        )
        assertEquals(6144L, normal.systemUsedRamMb)
        assertEquals(0.75f, normal.systemRamUsageRatio, 0.0001f)
        assertEquals(0.75f, normal.storageUsedRatio, 0.0001f)
        assertEquals(3, normal.cpuHistory.size)
        assertTrue(normal.isCharging)
        assertEquals("Nominal", normal.thermalLabel)

        val zeroTotal = ResourceTelemetrySnapshot(
            systemAvailableRamMb = 512L,
            systemTotalRamMb = 0L,
            storageAvailableGb = 10f,
            storageTotalGb = 0f
        )
        assertEquals(0L, zeroTotal.systemUsedRamMb)
        assertEquals(0f, zeroTotal.systemRamUsageRatio, 0.0001f)
        assertEquals(0f, zeroTotal.storageUsedRatio, 0.0001f)

        val overAvailable = ResourceTelemetrySnapshot(
            systemAvailableRamMb = 9000L,
            systemTotalRamMb = 8192L,
            storageAvailableGb = 200f,
            storageTotalGb = 128f
        )
        assertEquals(0L, overAvailable.systemUsedRamMb)
        assertEquals(0f, overAvailable.systemRamUsageRatio, 0.0001f)
        assertEquals(0f, overAvailable.storageUsedRatio, 0.0001f)
    }

    @Test
    fun resourceTelemetrySnapshot_holdsSpaceUsagesAndStability() {
        val usage = SpaceResourceUsage(
            spaceId = 42L,
            spaceName = "Gaming Arena",
            tag = "120Hz",
            appCount = 3,
            estimatedRamMb = 128L,
            cpuSharePercent = 18.5f
        )
        val snapshot = ResourceTelemetrySnapshot(
            appHeapUsedMb = 96L,
            appHeapMaxMb = 512L,
            nativeHeapMb = 32L,
            systemAvailableRamMb = 1024L,
            systemTotalRamMb = 8192L,
            systemLowMemory = false,
            processCpuPercent = 24.0f,
            cpuCores = 8,
            stability = StabilityLevel.MODERATE,
            spaceUsages = listOf(usage)
        )

        assertEquals(StabilityLevel.MODERATE, snapshot.stability)
        assertEquals(1, snapshot.spaceUsages.size)
        assertEquals(42L, snapshot.spaceUsages.first().spaceId)
        assertEquals(128L, snapshot.spaceUsages.first().estimatedRamMb)
    }

    @Test
    fun octoUiState_activeSpaceSelectsOrFallsBackCorrectly() {
        val alpha = VirtualSpaceEntity(id = 1L, name = "Work Space", tag = "Dual")
        val beta = VirtualSpaceEntity(id = 2L, name = "Gaming Arena", tag = "120Hz", accentIndex = 2)

        val selectedState = OctoUiState(
            spaces = listOf(alpha, beta),
            selectedSpaceId = 2L
        )
        assertEquals(beta, selectedState.activeSpace)

        val missingSelectionState = OctoUiState(
            spaces = listOf(alpha, beta),
            selectedSpaceId = 999L
        )
        assertEquals(alpha, missingSelectionState.activeSpace)

        val emptyState = OctoUiState(spaces = emptyList(), selectedSpaceId = 1L)
        assertNull(emptyState.activeSpace)
    }

    @Test
    fun virtualEntitiesAndBridgeModels_haveExpectedSafeDefaults() {
        val space = VirtualSpaceEntity(name = "Test Sandbox", tag = "Isolation")
        assertTrue(space.isolatedStorage)
        assertTrue(space.frameRateHud)
        assertTrue(space.permissionBridgeEnabled)
        assertEquals(440, space.virtualDpi)

        val app = VirtualAppEntity(
            spaceId = 1L,
            packageName = "com.example.demo",
            displayName = "Demo"
        )
        assertEquals("Instance #1", app.instanceLabel)
        assertEquals("Balanced", app.performanceMode)
        assertEquals(0, app.launchCount)
        assertNull(app.lastLaunchedAt)

        val installed = InstalledAppInfo(
            packageName = "com.example.demo",
            label = "Demo",
            versionName = "2.1",
            isGame = false,
            icon = null
        )
        assertFalse(installed.isSystemApp)
        assertEquals(0, installed.targetSdk)
        assertEquals(0f, installed.apkSizeMb, 0.0001f)

        val bridge = DeviceBridgeSnapshot()
        assertFalse(bridge.initialized)
        assertEquals("Standard display", bridge.hostDisplaySummary)
    }

    @Test
    fun integrityStatusSnapshot_defaultsAndVerdictInvariants() {
        val initial = IntegrityStatusSnapshot()
        assertEquals(IntegrityVerificationState.IDLE, initial.state)
        assertTrue(initial.isPeriodicEnabled)
        assertEquals(15L, initial.intervalMinutes)
        assertFalse(initial.playStorePresent)
        assertNull(initial.lastVerdict)

        val verdict = IntegrityVerdictResult(
            verified = true,
            appRecognitionVerdict = "PLAY_RECOGNIZED",
            deviceRecognitionVerdict = "MEETS_DEVICE_INTEGRITY",
            detail = "app=PLAY_RECOGNIZED, device=MEETS_DEVICE_INTEGRITY"
        )
        val verifiedSnapshot = initial.copy(
            state = IntegrityVerificationState.VERIFIED,
            lastVerdict = verdict,
            playStorePresent = true
        )
        assertEquals(IntegrityVerificationState.VERIFIED, verifiedSnapshot.state)
        assertTrue(verifiedSnapshot.lastVerdict?.verified == true)
    }
}
