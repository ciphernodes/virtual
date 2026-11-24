package com.example

import com.ciphernodes.ciphervirtual.monitoring.ResourceTelemetrySnapshot
import com.ciphernodes.ciphervirtual.monitoring.SpaceResourceUsage
import com.ciphernodes.ciphervirtual.monitoring.StabilityLevel
import com.ciphernodes.ciphervirtual.security.IntegrityStatusSnapshot
import com.ciphernodes.ciphervirtual.security.IntegrityVerdictResult
import com.ciphernodes.ciphervirtual.security.IntegrityVerificationState
import com.example.data.hardware.DeviceProfile
import com.example.data.hardware.DeviceProfileManager
import com.example.data.local.WorkspaceStorageManager
import com.example.data.model.DeviceBridgeSnapshot
import com.example.data.model.InstalledAppInfo
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import com.example.ui.OctoUiState
import com.example.ui.navigation.OctoRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

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
        assertEquals("", app.notes)
        assertNull(app.lastProfileId)

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

    @Test
    fun deviceProfile_presetsAndBuilderAndJsonRoundTripWork() {
        val pixel8 = DeviceProfileManager.pixel8()
        assertEquals("Pixel 8", pixel8.model)
        assertEquals("Google", pixel8.manufacturer)
        assertEquals(34, pixel8.sdkInt)
        assertFalse(pixel8.isCustom)

        val galaxyS24 = DeviceProfileManager.galaxyS24()
        assertEquals("SM-S921B", galaxyS24.model)
        assertEquals("samsung", galaxyS24.manufacturer)

        val custom = DeviceProfileManager.CustomProfileBuilder(pixel8)
            .profileId("custom-rig-01")
            .displayName("Lab Rig")
            .model("Lab-X1")
            .manufacturer("CipherLabs")
            .brand("cipher")
            .device("rig01")
            .product("rig01_global")
            .board("kalama")
            .hardware("qcom")
            .androidVersion("15")
            .sdkInt(35)
            .densityDpi(480)
            .build()

        assertTrue(custom.isCustom)
        assertEquals("custom-rig-01", custom.profileId)
        assertEquals("Lab Rig", custom.displayName)
        assertEquals("Lab-X1", custom.model)
        assertEquals("CipherLabs", custom.manufacturer)
        assertEquals(35, custom.sdkInt)

        val roundTripped = DeviceProfile.fromJsonString(custom.toJsonString())
        assertEquals(custom, roundTripped)

        val presets = DeviceProfileManager.availablePresets()
        assertTrue(presets.any { it.presetKey == "Pixel 8" })
        assertTrue(presets.any { it.presetKey == "Galaxy S24" })
        assertEquals("Pixel 8", DeviceProfileManager.presetForName("Pixel 8").presetKey)
    }

    @Test
    fun workspaceStorageManager_writesInspectsAndResetsSessionMetadata() = runTest {
        val tempRoot = Files.createTempDirectory("cipher_workspace_test").toFile()
        try {
            val manager = WorkspaceStorageManager(tempRoot)
            val space = VirtualSpaceEntity(id = 7L, name = "Testing Lab", tag = "LAB", virtualDpi = 480)
            val app = VirtualAppEntity(id = 19L, spaceId = 7L, packageName = "com.demo.app", displayName = "Demo")
            val profile = DeviceProfileManager.pixel9Pro()

            val configFile = manager.recordSessionMetadata(space, app, profile)
            assertTrue(configFile.exists())
            val content = configFile.readText()
            assertTrue(content.contains("\"spaceName\":\"Testing Lab\""))
            assertTrue(content.contains("\"profileModel\":\"Pixel 9 Pro\""))

            val stats = manager.inspectInstanceWorkspace(7L, 19L)
            assertEquals(1, stats.fileCount)
            assertTrue(stats.sizeBytes > 0L)
            assertNotNull(stats.lastSessionRecordedAt)

            assertTrue(manager.resetInstanceWorkspace(7L, 19L))
            val emptyStats = manager.inspectInstanceWorkspace(7L, 19L)
            assertEquals(0, emptyStats.fileCount)
            assertEquals(0L, emptyStats.sizeBytes)

            manager.recordSessionMetadata(space, app, profile)
            assertTrue(manager.deleteSpaceWorkspaces(7L))
            assertFalse(manager.spaceDirectory(7L).exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun octoRoutes_resolveTypedDestinationPatterns() {
        assertEquals("spaces", OctoRoute.Spaces.routePattern)
        assertEquals("permissions", OctoRoute.Permissions.routePattern)
        assertEquals("sandbox", OctoRoute.Sandbox.routePattern)
        assertEquals("app_detail/42", OctoRoute.AppDetail(42L).routePattern)
        assertEquals("app_detail/{appId}", OctoRoute.AppDetail.PATTERN)
    }
}
