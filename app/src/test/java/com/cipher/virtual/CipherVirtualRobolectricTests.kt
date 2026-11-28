package com.cipher.virtual

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.cipher.virtual.data.hardware.DeviceAppScanner
import com.cipher.virtual.data.hardware.DeviceBridgeManager
import com.cipher.virtual.data.hardware.DeviceProfileManager
import com.cipher.virtual.data.local.OctoDatabase
import com.cipher.virtual.data.local.WorkspaceInstance
import com.cipher.virtual.data.local.WorkspaceManager
import com.cipher.virtual.data.local.WorkspaceProcessRouter
import com.cipher.virtual.data.local.WorkspaceStorageManager
import com.cipher.virtual.data.model.DeviceBridgeSnapshot
import com.cipher.virtual.data.model.DevicePermissionItem
import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.data.model.VirtualSpaceEntity
import com.cipher.virtual.data.repository.OctoRepository
import com.cipher.virtual.security.PermissionManager
import com.cipher.virtual.service.BootCompletedReceiver
import com.cipher.virtual.service.CipherHostService
import com.cipher.virtual.ui.OctoUiState
import com.cipher.virtual.ui.components.PermissionPromptHost
import com.cipher.virtual.ui.screens.AppDetailScreen
import com.cipher.virtual.ui.screens.SandboxConfigScreen
import com.cipher.virtual.ui.theme.OctoSpaceTheme
import com.cipher.virtual.virtualization.VirtualAppContainerContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CipherVirtualRobolectricTests {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OctoDatabase::class.java
    )

    @Test
    fun octoDao_crudAndCascadeDeleteWorkInMemory() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OctoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.octoDao()
            val spaceId = dao.insertSpace(VirtualSpaceEntity(name = "QA Space", tag = "QA"))
            val appId = dao.insertApp(
                VirtualAppEntity(
                    spaceId = spaceId,
                    packageName = "com.target.qa",
                    displayName = "QA App",
                    notes = "Secondary test account"
                )
            )
            dao.recordAppLaunch(appId, 123456789L, "profile-uuid-1")

            val apps = dao.observeAppsForSpace(spaceId).first()
            assertEquals(1, apps.size)
            assertEquals(1, apps.first().launchCount)
            assertEquals("profile-uuid-1", apps.first().lastProfileId)

            dao.deleteSpace(spaceId)
            assertTrue(dao.observeAppsForSpace(spaceId).first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun octoRepository_ensureDefaultSpaces_combinesSpacesBackfillsInstancesAndSyncsRemovals() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OctoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.octoDao()
            val storage = WorkspaceStorageManager(context)
            val workspaceManager = WorkspaceManager(storage, db.workspaceInstanceDao())
            val workspaceRouter = WorkspaceProcessRouter(workspaceManager, storage)
            val repo = OctoRepository(
                dao = dao,
                scanner = DeviceAppScanner(context),
                bridgeManager = DeviceBridgeManager(context),
                workspaceStorage = storage,
                workspaceManager = workspaceManager,
                workspaceRouter = workspaceRouter
            )
            val firstId = dao.insertSpace(VirtualSpaceEntity(name = "Primary", tag = "MAIN"))
            val secondId = dao.insertSpace(VirtualSpaceEntity(name = "Secondary", tag = "DUAL"))
            val app1Id = dao.insertApp(VirtualAppEntity(spaceId = firstId, packageName = "com.target.one", displayName = "One"))
            val app2Id = dao.insertApp(VirtualAppEntity(spaceId = secondId, packageName = "com.target.two", displayName = "Two"))

            repo.ensureDefaultSpaces()

            val spaces = dao.getAllSpacesSnapshot()
            assertEquals(1, spaces.size)
            val mergedApps = dao.observeAppsForSpace(firstId).first()
            assertEquals(2, mergedApps.size)

            val instance1Id = OctoRepository.multiInstanceId(firstId, app1Id)
            val instance2Id = OctoRepository.multiInstanceId(firstId, app2Id)
            assertEquals(2, workspaceManager.listInstances().size)
            assertTrue(storage.instanceExists(instance1Id))
            assertTrue(storage.instanceExists(instance2Id))
            assertEquals(":w0", workspaceManager.getInstance(instance1Id)?.workerSlot)
            assertEquals(":w1", workspaceManager.getInstance(instance2Id)?.workerSlot)

            val secondApp = mergedApps.first { it.id == app2Id }
            repo.removeVirtualApp(secondApp)
            assertEquals(1, workspaceManager.listInstances().size)
            assertNull(workspaceRouter.getAssignedWorkerSlot(instance2Id))
            assertFalse(storage.instanceExists(instance2Id))
        } finally {
            db.close()
        }
    }

    @Test
    fun octoDatabase_migrations3To4To5_preserveExistingRows() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration_test_${System.currentTimeMillis()}.db")
        if (dbFile.exists()) dbFile.delete()
        try {
            val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `virtual_spaces` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `tag` TEXT NOT NULL,
                    `accentIndex` INTEGER NOT NULL,
                    `deviceModelPreset` TEXT NOT NULL,
                    `isolatedStorage` INTEGER NOT NULL,
                    `frameRateHud` INTEGER NOT NULL,
                    `permissionBridgeEnabled` INTEGER NOT NULL,
                    `virtualDpi` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `virtual_apps` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `spaceId` INTEGER NOT NULL,
                    `packageName` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `instanceLabel` TEXT NOT NULL,
                    `performanceMode` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `lastProfileId` TEXT,
                    `launchCount` INTEGER NOT NULL,
                    `lastLaunchedAt` INTEGER,
                    `addedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`spaceId`) REFERENCES `virtual_spaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_virtual_apps_spaceId` ON `virtual_apps` (`spaceId`)")
            rawDb.execSQL(
                """
                INSERT INTO `virtual_spaces` (`id`, `name`, `tag`, `accentIndex`, `deviceModelPreset`, `isolatedStorage`, `frameRateHud`, `permissionBridgeEnabled`, `virtualDpi`, `createdAt`)
                VALUES (1, 'Cipher Workspace', 'MAIN', 0, 'ROG Phone 8 Pro', 1, 1, 1, 440, 1000)
                """.trimIndent()
            )
            rawDb.execSQL(
                """
                INSERT INTO `virtual_apps` (`id`, `spaceId`, `packageName`, `displayName`, `instanceLabel`, `performanceMode`, `notes`, `lastProfileId`, `launchCount`, `lastLaunchedAt`, `addedAt`)
                VALUES (10, 1, 'com.target.migrated', 'Migrated App', 'Instance #1', 'Turbo', 'Saved note', NULL, 4, 2000, 1500)
                """.trimIndent()
            )
            rawDb.version = 3
            rawDb.close()

            val migratedDb = Room.databaseBuilder(context, OctoDatabase::class.java, dbFile.absolutePath)
                .addMigrations(
                    OctoDatabase.MIGRATION_1_2,
                    OctoDatabase.MIGRATION_2_3,
                    OctoDatabase.MIGRATION_3_4,
                    OctoDatabase.MIGRATION_4_5,
                    OctoDatabase.MIGRATION_5_6,
                    OctoDatabase.MIGRATION_6_7
                )
                .allowMainThreadQueries()
                .build()
            try {
                val spaces = migratedDb.octoDao().getAllSpacesSnapshot()
                val apps = migratedDb.octoDao().getAllAppsSnapshot()
                assertEquals(1, spaces.size)
                assertEquals(1, apps.size)
                assertEquals("com.target.migrated", apps.first().packageName)
                assertEquals("Saved note", apps.first().notes)

                val instanceDao = migratedDb.workspaceInstanceDao()
                instanceDao.upsert(
                    WorkspaceInstance(
                        instanceId = "migrated_inst",
                        label = "Migrated Instance",
                        targetPackageName = "com.target.migrated",
                        createdAt = 3000L,
                        isActive = true,
                        workerSlot = ":w3"
                    )
                )
                val persisted = instanceDao.getById("migrated_inst")
                assertEquals(":w3", persisted?.workerSlot)
            } finally {
                migratedDb.close()
            }
        } finally {
            dbFile.delete()
        }
    }

    @Test
    fun deviceProfileManager_persistsCustomProfilesAcrossReads() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DeviceProfileManager(context)
        val custom = DeviceProfileManager.CustomProfileBuilder(DeviceProfileManager.pixel8())
            .displayName("CI Rig")
            .model("CI-9000")
            .sdkInt(35)
            .build()

        manager.saveCustomProfile(custom, spaceId = 1L)
        val active = manager.getActiveProfile(spaceId = 1L)
        assertEquals("CI Rig", active.displayName)
        assertEquals("CI-9000", active.model)
        assertEquals(35, active.sdkInt)
    }

    @Test
    fun sandboxConfigScreen_displaysCustomProfileAccessibleButtonsIntegrityAndVirtualVault() {
        val space = VirtualSpaceEntity(id = 1L, name = "Primary", tag = "MAIN")
        val custom = DeviceProfileManager.CustomProfileBuilder(DeviceProfileManager.pixel8())
            .profileId("custom_qa_1")
            .displayName("QA Rig")
            .build()
        val state = OctoUiState(
            spaces = listOf(space),
            selectedSpaceId = 1L,
            activeDeviceProfile = DeviceProfileManager.rogPhone8Pro(),
            availableDeviceProfiles = DeviceProfileManager.availablePresets() + custom,
            attachedVaultUris = listOf(Uri.parse("content://media/external/images/media/42")),
            deviceBridge = DeviceBridgeSnapshot(initialized = true, hasStorageFolderGrant = true)
        )
        composeRule.setContent {
            OctoSpaceTheme(darkTheme = true) {
                SandboxConfigScreen(
                    uiState = state,
                    onUpdateSpace = {},
                    onSelectDeviceProfile = { _, _ -> },
                    onSaveCustomProfile = { _, _ -> },
                    onDeleteCustomProfile = { _, _ -> },
                    onResetSpaceDefaults = {},
                    onToggleTheme = {},
                    onRunIntegrityCheck = {},
                    onTogglePeriodicIntegrity = {},
                    onRefreshResources = {}
                )
            }
        }

        composeRule.onNodeWithTag("new_custom_profile_button").assertIsDisplayed()
        composeRule.onNodeWithTag("device_presets_row")
            .performScrollToNode(hasTestTag("edit_custom_profile_custom_qa_1"))
        composeRule.onNodeWithTag("edit_custom_profile_custom_qa_1")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("delete_custom_profile_custom_qa_1")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithTag("sandbox_config_list")
            .performScrollToNode(hasTestTag("security_dashboard_integrity_card"))
        composeRule.onNodeWithTag("security_dashboard_integrity_card").assertIsDisplayed()

        composeRule.onNodeWithTag("sandbox_config_list")
            .performScrollToNode(hasTestTag("virtual_vault_card"))
        composeRule.onNodeWithTag("virtual_vault_card").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_storage_granted_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_import_media_button").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_select_folder_button").assertIsDisplayed()
        composeRule.onNodeWithTag("remove_vault_uri_0").assertIsDisplayed()
    }

    @Test
    fun permissionManager_requiresBothRuntimeAndSpecialAccessPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = PermissionManager(context)

        val requiredItems = manager.getRequiredPermissionItems()
        assertTrue(requiredItems.any { it.key == "camera" && !it.isSpecialAccess })
        assertTrue(requiredItems.any { it.key == "phone" && !it.isSpecialAccess })
        assertTrue(requiredItems.any { it.key == "sms" && !it.isSpecialAccess })
        assertTrue(requiredItems.any { it.key == "all_files_storage" && it.isSpecialAccess })
        assertTrue(requiredItems.any { it.key == "overlay" && it.isSpecialAccess })
        assertTrue(requiredItems.any { it.key == "usage_stats" && it.isSpecialAccess })

        val missingRuntime = manager.getMissingRuntimePermissions().toList()
        assertTrue(missingRuntime.contains(Manifest.permission.CAMERA))
        assertTrue(missingRuntime.contains(Manifest.permission.READ_PHONE_STATE))
        assertTrue(missingRuntime.contains(Manifest.permission.READ_SMS))
        assertTrue(!missingRuntime.contains(Manifest.permission.MANAGE_EXTERNAL_STORAGE))
        assertTrue(!missingRuntime.contains(Manifest.permission.SYSTEM_ALERT_WINDOW))
        assertEquals("all_files_storage", manager.getNextMissingSpecialAccessKey())
    }

    @Test
    fun permissionPromptHost_promptsMissingSpecialPermissionWithoutDisplayingPermissionList() {
        val snapshot = DeviceBridgeSnapshot(
            initialized = true,
            permissions = listOf(
                DevicePermissionItem(
                    key = "camera",
                    title = "Camera Hardware",
                    description = "QR scanning & photo capture",
                    permissions = listOf(Manifest.permission.CAMERA),
                    isGranted = true,
                    isSpecialAccess = false
                ),
                DevicePermissionItem(
                    key = "overlay",
                    title = "Display Over Other Apps",
                    description = "Floating overlay access",
                    permissions = listOf(Manifest.permission.SYSTEM_ALERT_WINDOW),
                    isGranted = false,
                    isSpecialAccess = true
                )
            ),
            grantedCount = 1,
            totalCount = 2,
            allRequiredGranted = false
        )

        composeRule.setContent {
            OctoSpaceTheme(darkTheme = true) {
                PermissionPromptHost(
                    bridge = snapshot,
                    missingRuntimePermissions = { emptyArray() },
                    onPermissionsResult = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("permission_prompt_host").assertIsDisplayed()
        composeRule.onNodeWithTag("permissions_dashboard").assertDoesNotExist()
        composeRule.onNodeWithTag("special_permission_prompt_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("prompt_allow_special_button").assertIsDisplayed()
        composeRule.onNodeWithTag("prompt_decline_button").assertIsDisplayed()
    }

    @Test
    fun workspaceProcessRouter_isolatesXmlSharedPreferencesAndResetsCleanly() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OctoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val storage = WorkspaceStorageManager(context)
            val dao = db.workspaceInstanceDao()
            val manager = WorkspaceManager(storageManager = storage, dao = dao)
            manager.initialize().getOrThrow()
            val router = WorkspaceProcessRouter(manager, storage)

            manager.createInstance("Account A", "com.target.app", instanceId = "inst_a", activate = true).getOrThrow()
            manager.createInstance("Account B", "com.target.app", instanceId = "inst_b", activate = false).getOrThrow()

            assertEquals(2, dao.getAllSnapshot().size)
            assertEquals("inst_a", dao.getActive()?.instanceId)

            val ctxA = router.createInstanceContext(context, "inst_a").getOrThrow()
            val ctxB = router.createInstanceContext(context, "inst_b").getOrThrow()

            assertEquals(":w0", ctxA.workerSlot)
            assertEquals(":w1", ctxB.workerSlot)
            assertNotEquals(ctxA.filesDir.absolutePath, ctxB.filesDir.absolutePath)
            assertNotEquals(ctxA.cacheDir.absolutePath, ctxB.cacheDir.absolutePath)
            assertNotEquals(ctxA.dataDir.absolutePath, ctxB.dataDir.absolutePath)
            assertNotEquals(ctxA.getDatabasePath("app.db").absolutePath, ctxB.getDatabasePath("app.db").absolutePath)
            assertNotEquals(ctxA.getSharedPreferencesFile("auth").absolutePath, ctxB.getSharedPreferencesFile("auth").absolutePath)

            ctxA.openFileOutput("token.txt", Context.MODE_PRIVATE).use {
                it.write("secret-a".toByteArray())
            }
            assertTrue(ctxA.fileList().contains("token.txt"))
            assertFalse(ctxB.fileList().contains("token.txt"))
            assertEquals("secret-a", ctxA.openFileInput("token.txt").bufferedReader().readText())

            val prefsA = ctxA.getSharedPreferences("auth", Context.MODE_PRIVATE)
            val prefsB = ctxB.getSharedPreferences("auth", Context.MODE_PRIVATE)
            assertTrue(
                prefsA.edit()
                    .putString("user", "alice")
                    .putInt("tier", 3)
                    .putBoolean("verified", true)
                    .putStringSet("roles", mutableSetOf("admin", "tester"))
                    .commit()
            )
            assertTrue(prefsB.edit().putString("user", "bob").commit())

            val fileA = ctxA.getSharedPreferencesFile("auth")
            assertTrue(fileA.exists())
            assertTrue(fileA.readText().contains("alice"))
            assertFalse(fileA.readText().contains("bob"))

            // Reload from disk via fresh ContextWrapper
            val reloadedCtxA = router.createInstanceContext(context, "inst_a").getOrThrow()
            val reloadedPrefsA = reloadedCtxA.getSharedPreferences("auth", Context.MODE_PRIVATE)
            assertEquals("alice", reloadedPrefsA.getString("user", null))
            assertEquals(3, reloadedPrefsA.getInt("tier", 0))
            assertTrue(reloadedPrefsA.getBoolean("verified", false))
            assertEquals(setOf("admin", "tester"), reloadedPrefsA.getStringSet("roles", emptySet()))
            assertEquals("bob", prefsB.getString("user", null))
            assertNull(context.getSharedPreferences("auth", Context.MODE_PRIVATE).getString("user", null))

            assertTrue(reloadedCtxA.deleteSharedPreferences("auth"))
            assertFalse(fileA.exists())

            manager.switchActiveInstance("inst_b").getOrThrow()
            assertEquals("inst_b", dao.getActive()?.instanceId)
            val activeCtx = router.resolveActiveContext(context).getOrThrow()
            assertEquals("inst_b", activeCtx.instancePaths.instanceId)

            manager.clearInstanceData("inst_b").getOrThrow()
            assertFalse(ctxB.getSharedPreferencesFile("auth").exists())

            manager.removeInstance("inst_b").getOrThrow()
            assertEquals(1, dao.getAllSnapshot().size)
            assertEquals("inst_a", dao.getActive()?.instanceId)
        } finally {
            db.close()
        }
    }

    @Test
    fun octoDao_unifiedVirtualAppSlotActiveAndMetricsWorkInMemory() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OctoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.octoDao()
            val spaceId = dao.insertSpace(VirtualSpaceEntity(name = "Cipher Workspace", tag = "MAIN"))
            val app1Id = dao.insertApp(
                VirtualAppEntity(
                    spaceId = spaceId,
                    packageName = "com.target.chat",
                    displayName = "Chat",
                    instanceLabel = "Work",
                    workerSlot = ":w0",
                    isActive = true
                )
            )
            val app2Id = dao.insertApp(
                VirtualAppEntity(
                    spaceId = spaceId,
                    packageName = "com.target.chat",
                    displayName = "Chat",
                    instanceLabel = "Personal",
                    workerSlot = ":w1",
                    isActive = false
                )
            )

            dao.switchActiveAppAtomically(app2Id)
            dao.updateAppWorkerRouting(app2Id, ":w2", 10420)
            dao.updateAppStorageMetrics(app2Id, 4096L, 3)
            dao.recordAppLaunch(app2Id, 999999L, "profile_1")

            val apps = dao.getAllAppsSnapshot()
            val updated1 = apps.first { it.id == app1Id }
            val updated2 = apps.first { it.id == app2Id }
            assertFalse(updated1.isActive)
            assertTrue(updated2.isActive)
            assertEquals(":w2", updated2.workerSlot)
            assertEquals(10420, updated2.virtualUid)
            assertEquals(4096L, updated2.storageSizeBytes)
            assertEquals(3, updated2.fileCount)
            assertEquals(1, updated2.launchCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun virtualAppContainerContent_rendersNeumorphicCardsAndAccessible48dpButtons() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDataDir = File(context.cacheDir, "container_ui_test/data").apply { mkdirs() }
        try {
            composeRule.setContent {
                OctoSpaceTheme(darkTheme = true) {
                    VirtualAppContainerContent(
                        instanceId = "space_1_app_7",
                        packageName = "com.target.app",
                        appName = "TargetApp",
                        label = "Instance #1",
                        workerSlot = ":w2",
                        virtualUid = 10420,
                        isRooted = false,
                        envType = "NON_ROOT_USERSPACE_HOOK",
                        dataPath = tempDataDir.absolutePath,
                        onBack = {},
                        onLaunchHostIntent = {}
                    )
                }
            }

            composeRule.onNodeWithTag("virtual_container_back_button")
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithTag("virtual_container_engine_card").assertIsDisplayed()
            composeRule.onNodeWithTag("virtual_container_storage_card").assertIsDisplayed()
            composeRule.onNodeWithTag("virtual_container_simulate_io_button")
                .assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithTag("virtual_container_list_files_button")
                .assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithTag("launch_virtual_app_button")
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        } finally {
            tempDataDir.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun migrationHelper_migratesV1ThroughV4AndPersistsWorkspaceInstanceMetadata() = runTest {
        val dbName = "migration_helper_v1_v4_${System.currentTimeMillis()}.db"
        migrationHelper.createDatabase(dbName, 1).use { dbV1 ->
            dbV1.insert(
                "virtual_spaces",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("id", 1L)
                    put("name", "Legacy Workspace")
                    put("tag", "MAIN")
                    put("createdAt", 1000L)
                }
            )
            dbV1.insert(
                "virtual_apps",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("id", 10L)
                    put("spaceId", 1L)
                    put("packageName", "com.target.legacy")
                    put("displayName", "Legacy App")
                    put("instanceLabel", "Account #1")
                    put("performanceMode", "Balanced")
                    put("launchCount", 3)
                    put("lastLaunchedAt", 1500L)
                    put("addedAt", 1200L)
                }
            )
        }

        val dbV4 = migrationHelper.runMigrationsAndValidate(
            dbName,
            4,
            true,
            OctoDatabase.MIGRATION_1_2,
            OctoDatabase.MIGRATION_2_3,
            OctoDatabase.MIGRATION_3_4
        )
        dbV4.use { migrated ->
            migrated.query("SELECT name, deviceModelPreset, virtualDpi FROM virtual_spaces WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Legacy Workspace", cursor.getString(0))
                assertEquals("ROG Phone 8 Pro", cursor.getString(1))
                assertEquals(440, cursor.getInt(2))
            }
            migrated.query("SELECT packageName, instanceLabel, notes, lastProfileId FROM virtual_apps WHERE id = 10").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("com.target.legacy", cursor.getString(0))
                assertEquals("Account #1", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertTrue(cursor.isNull(3))
            }

            migrated.insert(
                "workspace_instances",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("instanceId", "ws_legacy_10")
                    put("label", "Account #1")
                    put("targetPackageName", "com.target.legacy")
                    put("createdAt", 2000L)
                    put("isActive", 1)
                }
            )
            migrated.query("SELECT instanceId, label, targetPackageName, isActive FROM workspace_instances WHERE instanceId = 'ws_legacy_10'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ws_legacy_10", cursor.getString(0))
                assertEquals("Account #1", cursor.getString(1))
                assertEquals("com.target.legacy", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
            }
        }

        val dbV6 = migrationHelper.runMigrationsAndValidate(
            dbName,
            6,
            true,
            OctoDatabase.MIGRATION_4_5,
            OctoDatabase.MIGRATION_5_6
        )
        dbV6.use { v6 ->
            v6.insert(
                "cloned_app_instances",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("instanceId", "space_1_app_10")
                    put("packageName", "com.target.legacy")
                    put("appName", "Legacy App")
                    put("instanceLabel", "Account #1")
                    put("dataStoragePath", "/tmp/data")
                    put("cacheStoragePath", "/tmp/cache")
                    put("databasesPath", "/tmp/databases")
                    put("sharedPrefsPath", "/tmp/prefs")
                    put("workerSlot", ":w4")
                    put("virtualUid", 10555)
                    put("virtualPid", 0)
                    put("isRootedVirtualization", 0)
                    put("virtualEnvType", "NON_ROOT_USERSPACE_HOOK")
                    put("isActive", 1)
                    put("storageSizeBytes", 2048L)
                    put("fileCount", 5)
                    put("launchCount", 3)
                    put("createdAt", 1200L)
                }
            )
            v6.insert(
                "cloned_app_instances",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("instanceId", "orphan_clone_99")
                    put("packageName", "com.target.orphan")
                    put("appName", "Orphan App")
                    put("instanceLabel", "Clone #99")
                    put("dataStoragePath", "/tmp/orphan/data")
                    put("cacheStoragePath", "/tmp/orphan/cache")
                    put("databasesPath", "/tmp/orphan/databases")
                    put("sharedPrefsPath", "/tmp/orphan/prefs")
                    put("workerSlot", ":w5")
                    put("virtualUid", 10666)
                    put("virtualPid", 0)
                    put("isRootedVirtualization", 0)
                    put("virtualEnvType", "NON_ROOT_USERSPACE_HOOK")
                    put("isActive", 0)
                    put("storageSizeBytes", 512L)
                    put("fileCount", 1)
                    put("launchCount", 2)
                    put("createdAt", 1300L)
                }
            )
        }

        val dbV7 = migrationHelper.runMigrationsAndValidate(
            dbName,
            7,
            true,
            OctoDatabase.MIGRATION_6_7
        )
        dbV7.close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val roomDb = Room.databaseBuilder(context, OctoDatabase::class.java, context.getDatabasePath(dbName).absolutePath)
            .addMigrations(
                OctoDatabase.MIGRATION_1_2,
                OctoDatabase.MIGRATION_2_3,
                OctoDatabase.MIGRATION_3_4,
                OctoDatabase.MIGRATION_4_5,
                OctoDatabase.MIGRATION_5_6,
                OctoDatabase.MIGRATION_6_7
            )
            .allowMainThreadQueries()
            .build()
        try {
            val persisted = roomDb.workspaceInstanceDao().getById("ws_legacy_10")
            assertNotNull(persisted)
            assertEquals("Account #1", persisted?.label)
            assertEquals("com.target.legacy", persisted?.targetPackageName)
            assertTrue(persisted?.isActive == true)
            assertNull(persisted?.workerSlot)

            val allApps = roomDb.octoDao().getAllAppsSnapshot()
            val mergedLegacy = allApps.firstOrNull { it.id == 10L }
            assertNotNull(mergedLegacy)
            assertEquals(":w4", mergedLegacy?.workerSlot)
            assertEquals(10555, mergedLegacy?.virtualUid)
            assertTrue(mergedLegacy?.isActive == true)
            assertEquals(2048L, mergedLegacy?.storageSizeBytes)
            assertEquals(5, mergedLegacy?.fileCount)

            val migratedOrphan = allApps.firstOrNull { it.packageName == "com.target.orphan" }
            assertNotNull(migratedOrphan)
            assertEquals(":w5", migratedOrphan?.workerSlot)
            assertEquals(10666, migratedOrphan?.virtualUid)

            roomDb.workspaceInstanceDao().upsert(
                WorkspaceInstance(
                    instanceId = "ws_legacy_10",
                    label = "Account #1",
                    targetPackageName = "com.target.legacy",
                    createdAt = 2000L,
                    isActive = true,
                    workerSlot = ":w2"
                )
            )
            assertEquals(":w2", roomDb.workspaceInstanceDao().getById("ws_legacy_10")?.workerSlot)
        } finally {
            roomDb.close()
        }
    }

    @Test
    fun appDetailScreen_commitsTextEditsOnlyOnFocusLoss() {
        val space = VirtualSpaceEntity(id = 1L, name = "Cipher Workspace", tag = "MAIN")
        val initialApp = VirtualAppEntity(
            id = 7L,
            spaceId = 1L,
            packageName = "com.target.demo",
            displayName = "Demo",
            instanceLabel = "Account 1",
            notes = "Initial note"
        )
        val committedUpdates = mutableListOf<VirtualAppEntity>()

        composeRule.setContent {
            OctoSpaceTheme(darkTheme = true) {
                AppDetailScreen(
                    appId = 7L,
                    uiState = OctoUiState(
                        spaces = listOf(space),
                        selectedSpaceId = 1L,
                        allVirtualApps = listOf(initialApp)
                    ),
                    hasWorkProfileTarget = { false },
                    onInspectWorkspace = { _, _ -> },
                    onUpdateApp = { updated -> committedUpdates += updated },
                    onCloneApp = {},
                    onRemoveApp = {},
                    onResetWorkspace = {},
                    onLaunchApp = { _, _ -> },
                    onBack = {}
                )
            }
        }

        composeRule.waitForIdle()
        assertTrue(committedUpdates.isEmpty())

        composeRule.onNodeWithTag("detail_instance_label_input").performClick()
        composeRule.onNodeWithTag("detail_instance_label_input").performTextReplacement("Work Profile")
        composeRule.waitForIdle()
        assertTrue("Expected no Room write while label field retains focus", committedUpdates.isEmpty())

        // Moving focus to notes triggers focus loss on the label input.
        composeRule.onNodeWithTag("detail_instance_notes_input").performClick()
        composeRule.waitForIdle()
        assertEquals(1, committedUpdates.size)
        assertEquals("Work Profile", committedUpdates.last().instanceLabel)

        composeRule.onNodeWithTag("detail_instance_notes_input").performTextReplacement("QA notes saved on blur")
        composeRule.waitForIdle()
        assertEquals(1, committedUpdates.size)

        // Pressing IME Done on label after focusing and editing it clears focus and commits pending edits.
        composeRule.onNodeWithTag("detail_instance_label_input").performClick()
        composeRule.waitForIdle()
        assertEquals(2, committedUpdates.size)
        assertEquals("QA notes saved on blur", committedUpdates.last().notes)

        composeRule.onNodeWithTag("detail_instance_label_input").performTextReplacement("Work Profile IME")
        composeRule.waitForIdle()
        assertEquals(2, committedUpdates.size)
        composeRule.onNodeWithTag("detail_instance_label_input").performImeAction()
        composeRule.waitForIdle()
        assertEquals(3, committedUpdates.size)
        assertEquals("Work Profile IME", committedUpdates.last().instanceLabel)
    }

    @Test
    fun sandboxConfigScreen_sliderUpdatesLocallyAndCommitsOnlyOnValueChangeFinished() {
        val space = VirtualSpaceEntity(id = 1L, name = "Cipher Workspace", tag = "MAIN", virtualDpi = 440)
        val committedSpaces = mutableListOf<VirtualSpaceEntity>()

        composeRule.setContent {
            OctoSpaceTheme(darkTheme = true) {
                SandboxConfigScreen(
                    uiState = OctoUiState(
                        spaces = listOf(space),
                        selectedSpaceId = 1L,
                        activeDeviceProfile = DeviceProfileManager.rogPhone8Pro(),
                        availableDeviceProfiles = DeviceProfileManager.availablePresets()
                    ),
                    onUpdateSpace = { committedSpaces += it },
                    onSelectDeviceProfile = { _, _ -> },
                    onSaveCustomProfile = { _, _ -> },
                    onDeleteCustomProfile = { _, _ -> },
                    onResetSpaceDefaults = {},
                    onToggleTheme = {},
                    onRunIntegrityCheck = {},
                    onTogglePeriodicIntegrity = {},
                    onRefreshResources = {}
                )
            }
        }

        composeRule.onNodeWithTag("sandbox_dpi_slider").performTouchInput {
            swipeRight()
        }
        composeRule.waitForIdle()

        assertEquals(1, committedSpaces.size)
        assertTrue(committedSpaces.single().virtualDpi > 440)
    }

    @Test
    fun deviceAppScanner_preservesAspectRatioForNonSquareDrawablesAndDecodesBoundedParallel() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scanner = DeviceAppScanner(context)

        val wideBitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val wideDrawable = BitmapDrawable(context.resources, wideBitmap)
        val wideResult = scanner.drawableToImageBitmap(wideDrawable)
        assertEquals(84, wideResult.width)
        assertEquals(42, wideResult.height)

        val tallBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val tallDrawable = BitmapDrawable(context.resources, tallBitmap)
        val tallResult = scanner.drawableToImageBitmap(tallDrawable)
        assertEquals(42, tallResult.width)
        assertEquals(84, tallResult.height)

        val smallBitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        val smallDrawable = BitmapDrawable(context.resources, smallBitmap)
        val smallResult = scanner.drawableToImageBitmap(smallDrawable)
        assertEquals(64, smallResult.width)
        assertEquals(32, smallResult.height)

        val vectorLikeDrawable = ShapeDrawable(RectShape()).apply {
            intrinsicWidth = 32
            intrinsicHeight = 16
        }
        val vectorResult = scanner.drawableToImageBitmap(vectorLikeDrawable)
        assertEquals(84, vectorResult.width)
        assertEquals(42, vectorResult.height)

        val icons = scanner.loadAppIcons(listOf(context.packageName, context.packageName, "com.nonexistent.pkg"))
        assertFalse(icons.containsKey("com.nonexistent.pkg"))
    }

    @Test
    fun cipherHostService_promotesToForegroundImmediatelyInOnCreate() {
        val controller = Robolectric.buildService(CipherHostService::class.java).create()
        val service = controller.get()
        assertTrue(service.isForegroundActive)

        val shadowService = Shadows.shadowOf(service)
        assertNotNull(shadowService.lastForegroundNotification)
        assertEquals(CipherHostService.NOTIFICATION_ID, shadowService.lastForegroundNotificationId)
        assertFalse(shadowService.isForegroundStopped)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceInfo = service.packageManager.getServiceInfo(
                android.content.ComponentName(service, CipherHostService::class.java),
                0
            )
            assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                serviceInfo.foregroundServiceType
            )
        }

        val stopIntent = Intent(service, CipherHostService::class.java).apply {
            action = CipherHostService.ACTION_STOP
        }
        Robolectric.buildService(CipherHostService::class.java, stopIntent)
            .create()
            .startCommand(0, 1)
            .let { stoppedController ->
                assertFalse(stoppedController.get().isForegroundActive)
                assertTrue(Shadows.shadowOf(stoppedController.get()).isForegroundStopped)
                stoppedController.destroy()
            }
        controller.destroy()
    }

    @Test
    fun bootCompletedReceiver_dispatchesCipherHostServiceOnBootAndUpgrade() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)
        val receiver = BootCompletedReceiver()

        receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        val bootService = shadowApp.nextStartedService
        assertNotNull(bootService)
        assertEquals(CipherHostService::class.java.name, bootService.component?.className)
        assertEquals(Intent.ACTION_BOOT_COMPLETED, bootService.getStringExtra(CipherHostService.EXTRA_TRIGGER_REASON))

        receiver.onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        val upgradeService = shadowApp.nextStartedService
        assertNotNull(upgradeService)
        assertEquals(CipherHostService::class.java.name, upgradeService.component?.className)
        assertEquals(Intent.ACTION_MY_PACKAGE_REPLACED, upgradeService.getStringExtra(CipherHostService.EXTRA_TRIGGER_REASON))
    }

    @Test
    fun permissionPromptHost_displaysDedicatedBatteryOptimizationRationaleDialog() {
        val snapshot = DeviceBridgeSnapshot(
            initialized = true,
            permissions = listOf(
                DevicePermissionItem(
                    key = "battery",
                    title = "Unrestricted Battery",
                    description = "Keep background monitoring active without throttling",
                    permissions = listOf(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS),
                    isGranted = false,
                    isSpecialAccess = true
                )
            ),
            grantedCount = 0,
            totalCount = 1,
            allRequiredGranted = false
        )

        composeRule.setContent {
            OctoSpaceTheme(darkTheme = true) {
                PermissionPromptHost(
                    bridge = snapshot,
                    missingRuntimePermissions = { emptyArray() },
                    onPermissionsResult = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("battery_rationale_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("prompt_allow_battery_button")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("prompt_decline_battery_button")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }
}
