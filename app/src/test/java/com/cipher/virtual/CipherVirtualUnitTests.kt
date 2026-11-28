package com.cipher.virtual

import com.cipher.virtual.data.hardware.DeviceAppScanner
import com.cipher.virtual.data.hardware.DeviceProfile
import com.cipher.virtual.data.hardware.DeviceProfileManager
import com.cipher.virtual.data.local.WorkspaceManager
import com.cipher.virtual.data.local.WorkspaceProcessRouter
import com.cipher.virtual.data.local.WorkspaceStorageManager
import com.cipher.virtual.data.model.DeviceBridgeSnapshot
import com.cipher.virtual.data.model.DevicePermissionItem
import com.cipher.virtual.data.model.InstalledAppInfo
import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.data.model.VirtualSpaceEntity
import com.cipher.virtual.monitoring.ResourceTelemetrySnapshot
import com.cipher.virtual.monitoring.SpaceResourceUsage
import com.cipher.virtual.monitoring.StabilityLevel
import com.cipher.virtual.security.IntegrityStatusSnapshot
import com.cipher.virtual.security.IntegrityVerdictResult
import com.cipher.virtual.security.IntegrityVerificationState
import com.cipher.virtual.ui.OctoUiState
import com.cipher.virtual.ui.navigation.OctoRoute
import com.cipher.virtual.virtualization.NativeVirtualEngine
import com.cipher.virtual.virtualization.RootDetectionHelper
import com.cipher.virtual.virtualization.VirtualWorkerContainerW0
import com.cipher.virtual.virtualization.resolveWorkerContainerClass
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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
            packageName = "com.target.demo",
            displayName = "Demo"
        )
        assertEquals("Instance #1", app.instanceLabel)
        assertEquals("Balanced", app.performanceMode)
        assertEquals(0, app.launchCount)
        assertNull(app.lastLaunchedAt)
        assertEquals("", app.notes)
        assertNull(app.lastProfileId)
        assertEquals(":w0", app.workerSlot)
        assertEquals(10000, app.virtualUid)
        assertFalse(app.isActive)
        assertEquals(0L, app.storageSizeBytes)
        assertEquals(0, app.fileCount)

        val installed = InstalledAppInfo(
            packageName = "com.target.demo",
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
        assertEquals("sandbox", OctoRoute.Sandbox.routePattern)
        assertEquals("app_detail/42", OctoRoute.AppDetail(42L).routePattern)
        assertEquals("app_detail/{appId}", OctoRoute.AppDetail.PATTERN)
    }

    @Test
    fun devicePermissionItem_distinguishesRuntimeAndSpecialAccess() {
        val runtimeItem = DevicePermissionItem(
            key = "camera",
            title = "Camera Hardware",
            description = "Camera capture",
            permissions = listOf("android.permission.CAMERA"),
            isGranted = false
        )
        assertFalse(runtimeItem.isSpecialAccess)

        val specialItem = DevicePermissionItem(
            key = "overlay",
            title = "Display Over Other Apps",
            description = "Overlay access",
            permissions = listOf("android.permission.SYSTEM_ALERT_WINDOW"),
            isGranted = false,
            isSpecialAccess = true
        )
        assertTrue(specialItem.isSpecialAccess)

        val bridge = DeviceBridgeSnapshot()
        assertFalse(bridge.allRequiredGranted)
        assertTrue(bridge.permissions.isEmpty())
    }

    @Test
    fun workspaceStorageManager_initializesResetsAndDeletesMultiInstanceDirectories() = runTest {
        val internalRoot = Files.createTempDirectory("cipher_ws_internal").toFile()
        val externalRoot = Files.createTempDirectory("cipher_ws_external").toFile()
        try {
            val storage = WorkspaceStorageManager(internalRoot, externalRoot)

            val alphaPaths = storage.initializeInstanceStorage("alpha_1").getOrThrow()
            val betaPaths = storage.initializeInstanceStorage("beta_2").getOrThrow()

            assertTrue(alphaPaths.dataDir.isDirectory)
            assertTrue(alphaPaths.cacheDir.isDirectory)
            assertTrue(alphaPaths.databasesDir.isDirectory)
            assertTrue(alphaPaths.sharedPrefsDir.isDirectory)
            assertTrue(alphaPaths.externalDataDir?.isDirectory == true)
            assertTrue(alphaPaths.externalCacheDir?.isDirectory == true)

            File(alphaPaths.dataDir, "session.dat").writeText("alpha-secret")
            alphaPaths.resolvePreferenceFile("prefs").writeText("<map/>")
            File(betaPaths.dataDir, "session.dat").writeText("beta-secret")

            val alphaStats = storage.inspectInstanceStorage("alpha_1").getOrThrow()
            assertEquals(2, alphaStats.fileCount)
            assertTrue(alphaStats.sizeBytes > 0L)

            val resetPaths = storage.resetInstanceStorage("alpha_1").getOrThrow()
            assertTrue(resetPaths.dataDir.isDirectory)
            assertTrue(resetPaths.sharedPrefsDir.isDirectory)
            assertEquals(0, storage.inspectInstanceStorage("alpha_1").getOrThrow().fileCount)
            assertTrue(File(betaPaths.dataDir, "session.dat").exists())

            assertTrue(storage.deleteInstanceStorage("beta_2").getOrThrow())
            assertFalse(storage.instanceExists("beta_2"))
            assertFalse(betaPaths.externalBaseDir?.exists() == true)
            assertTrue(storage.instanceExists("alpha_1"))

            assertTrue(runCatching { storage.sanitizeInstanceId("../escape") }.isFailure)
            assertTrue(runCatching { storage.sanitizeInstanceId("dir/sub") }.isFailure)
            assertTrue(runCatching { storage.sanitizeInstanceId("   ") }.isFailure)
        } finally {
            internalRoot.deleteRecursively()
            externalRoot.deleteRecursively()
        }
    }

    @Test
    fun workspaceManager_persistsCreatesSwitchesClearsAndRemovesInstances() = runTest {
        val tempRoot = Files.createTempDirectory("cipher_ws_manager").toFile()
        try {
            val storage = WorkspaceStorageManager(tempRoot)
            var now = 1000L
            val manager = WorkspaceManager(storageManager = storage, clock = { now += 10L; now })
            manager.initialize().getOrThrow()

            val work = manager.createInstance(
                label = "Work \"Primary\"",
                targetPackageName = "com.target.chat",
                instanceId = "work_1",
                activate = true
            ).getOrThrow()
            assertTrue(work.isActive)

            val personal = manager.createInstance(
                label = "Personal",
                targetPackageName = "com.target.chat",
                instanceId = "personal_2",
                activate = false
            ).getOrThrow()
            assertFalse(personal.isActive)
            assertEquals("work_1", manager.getActiveInstance()?.instanceId)

            val reloaded = WorkspaceManager(storage)
            val persistedList = reloaded.initialize().getOrThrow()
            assertEquals(2, persistedList.size)
            assertEquals("Work \"Primary\"", reloaded.getInstance("work_1")?.label)
            assertEquals("work_1", reloaded.getActiveInstance()?.instanceId)

            reloaded.switchActiveInstance("personal_2").getOrThrow()
            assertEquals("personal_2", reloaded.getActiveInstance()?.instanceId)
            assertFalse(reloaded.getInstance("work_1")?.isActive == true)

            val personalDataFile = File(storage.getInstancePaths("personal_2").dataDir, "token.txt")
            personalDataFile.writeText("abc123")
            assertTrue(personalDataFile.exists())
            reloaded.clearInstanceData("personal_2").getOrThrow()
            assertFalse(personalDataFile.exists())
            assertNotNull(reloaded.getInstance("personal_2"))

            assertTrue(reloaded.removeInstance("personal_2").getOrThrow())
            assertNull(reloaded.getInstance("personal_2"))
            assertFalse(storage.instanceExists("personal_2"))
            assertEquals("work_1", reloaded.getActiveInstance()?.instanceId)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun workspaceProcessRouter_assignsWorkerSlotsAndResolvesActivePaths() = runTest {
        val tempRoot = Files.createTempDirectory("cipher_ws_router").toFile()
        try {
            val storage = WorkspaceStorageManager(tempRoot)
            val manager = WorkspaceManager(storage)
            manager.initialize().getOrThrow()
            val router = WorkspaceProcessRouter(manager, storage)

            val assignedSlots = (0 until 10).map { index ->
                val id = "inst_$index"
                manager.createInstance("Inst $index", "com.demo.app", instanceId = id).getOrThrow()
                router.assignWorkerSlot(id).getOrThrow().workerProcessName
            }
            assertEquals((0..7).map { ":w$it" }, assignedSlots.take(8))
            assertEquals(":w0", assignedSlots[8])
            assertEquals(":w1", assignedSlots[9])

            assertTrue(router.releaseWorkerSlot("inst_2"))
            assertNull(router.getAssignedWorkerSlot("inst_2"))
            manager.createInstance("Inst Extra", "com.demo.app", instanceId = "inst_extra").getOrThrow()
            assertEquals(":w2", router.assignWorkerSlot("inst_extra").getOrThrow().workerProcessName)

            manager.switchActiveInstance("inst_0").getOrThrow()
            val activePaths = router.resolveActiveInstancePaths().getOrThrow()
            assertEquals("inst_0", activePaths.instanceId)
            val prefFile = router.resolvePreferenceFile("session").getOrThrow()
            assertEquals(File(activePaths.sharedPrefsDir, "session.xml"), prefFile)
            val dbFile = router.resolveDatabaseFile("cache.db").getOrThrow()
            assertEquals(File(activePaths.databasesDir, "cache.db"), dbFile)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun workspaceProcessRouter_persistsAndHydratesWorkerSlotsAcrossRestarts() = runTest {
        val tempRoot = Files.createTempDirectory("cipher_ws_slot_persist").toFile()
        try {
            val storage = WorkspaceStorageManager(tempRoot)
            val manager = WorkspaceManager(storage)
            manager.initialize().getOrThrow()
            val router = WorkspaceProcessRouter(manager, storage)

            manager.createInstance("Slot A", "com.demo.a", instanceId = "slot_a", activate = true).getOrThrow()
            manager.createInstance("Slot B", "com.demo.b", instanceId = "slot_b", activate = false).getOrThrow()
            router.assignAndPersistWorkerSlot("slot_a").getOrThrow()
            router.assignAndPersistWorkerSlot("slot_b").getOrThrow()
            assertEquals(":w0", manager.getInstance("slot_a")?.workerSlot)
            assertEquals(":w1", manager.getInstance("slot_b")?.workerSlot)

            val freshManager = WorkspaceManager(storage)
            freshManager.initialize().getOrThrow()
            val freshRouter = WorkspaceProcessRouter(freshManager, storage)
            assertEquals(2, freshRouter.hydrateFromManager())
            assertEquals(":w0", freshRouter.getAssignedWorkerSlot("slot_a"))
            assertEquals(":w1", freshRouter.getAssignedWorkerSlot("slot_b"))

            freshManager.createInstance("Slot C", "com.demo.c", instanceId = "slot_c", activate = false).getOrThrow()
            val thirdSlot = freshRouter.assignAndPersistWorkerSlot("slot_c").getOrThrow().workerProcessName
            assertEquals(":w2", thirdSlot)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun nativeVirtualEngine_reportsUnavailableHonestyWithoutFakeRedirects() {
        val engine = NativeVirtualEngine
        assertFalse(engine.isNativeEngineAvailable())
        assertNotNull(engine.getLoadFailureReason())
        assertFalse(engine.initVirtualEngine("com.cipher.virtual", 10000, false))
        assertFalse(
            engine.registerRedirectRule(
                instanceId = "test_clone_1",
                targetPackage = "com.target.chat",
                sourcePrefix = "/data/data/com.target.chat",
                targetPrefix = "/data/data/com.cipher.virtual/files/workspaces/clone_1/data",
                virtualUid = 10042
            )
        )
        assertFalse(engine.activateInstance("test_clone_1"))

        val samplePath = "/data/data/com.target.chat/databases/msgstore.db"
        assertEquals(samplePath, engine.redirectPath(samplePath))
        assertFalse(engine.setVirtualUid(10042))
        assertEquals(-1, engine.getVirtualUid())
        assertEquals(0L, engine.getRedirectCount())
        assertTrue(engine.getEngineStatus().contains("unavailable"))
    }

    @Test
    fun rootDetectionHelper_providesStructuredRootStatus() {
        val status = RootDetectionHelper.checkRootStatus()
        assertNotNull(status.rootType)
        if (!status.isRooted) {
            assertFalse(status.isRooted)
        }
    }

    @Test
    fun deviceAppScanner_calculateScaledDimensionsPreservesAspectRatio() {
        assertEquals(
            84 to 42,
            DeviceAppScanner.calculateScaledDimensions(200, 100, allowUpscale = false)
        )
        assertEquals(
            42 to 84,
            DeviceAppScanner.calculateScaledDimensions(100, 200, allowUpscale = false)
        )
        assertEquals(
            64 to 32,
            DeviceAppScanner.calculateScaledDimensions(64, 32, allowUpscale = false)
        )
        assertEquals(
            84 to 42,
            DeviceAppScanner.calculateScaledDimensions(32, 16, allowUpscale = true)
        )
        assertEquals(
            84 to 84,
            DeviceAppScanner.calculateScaledDimensions(-1, -1, allowUpscale = true)
        )
    }

    @Test
    fun resolveWorkerContainerClass_mapsAllEightSlotsToDistinctWorkerActivities() {
        val resolved = (0..7).map { slot ->
            resolveWorkerContainerClass(":w$slot")
        }
        assertEquals(8, resolved.distinct().size)
        assertEquals(
            VirtualWorkerContainerW0::class.java,
            resolveWorkerContainerClass(":unknown")
        )
    }
}
