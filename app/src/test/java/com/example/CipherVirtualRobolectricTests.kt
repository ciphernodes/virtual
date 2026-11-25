package com.example

import android.Manifest
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ciphernodes.ciphervirtual.security.PermissionManager
import com.example.data.hardware.DeviceAppScanner
import com.example.data.hardware.DeviceBridgeManager
import com.example.data.hardware.DeviceProfileManager
import com.example.data.local.OctoDatabase
import com.example.data.local.WorkspaceManager
import com.example.data.local.WorkspaceProcessRouter
import com.example.data.local.WorkspaceStorageManager
import com.example.data.model.DeviceBridgeSnapshot
import com.example.data.model.DevicePermissionItem
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import com.example.data.repository.OctoRepository
import com.example.ui.OctoUiState
import com.example.ui.components.PermissionPromptHost
import com.example.ui.screens.SandboxConfigScreen
import com.example.ui.theme.OctoSpaceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CipherVirtualRobolectricTests {

    @get:Rule
    val composeRule = createComposeRule()

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
                    packageName = "com.example.qa",
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
            val app1Id = dao.insertApp(VirtualAppEntity(spaceId = firstId, packageName = "com.example.one", displayName = "One"))
            val app2Id = dao.insertApp(VirtualAppEntity(spaceId = secondId, packageName = "com.example.two", displayName = "Two"))

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
                VALUES (10, 1, 'com.example.migrated', 'Migrated App', 'Instance #1', 'Turbo', 'Saved note', NULL, 4, 2000, 1500)
                """.trimIndent()
            )
            rawDb.version = 3
            rawDb.close()

            val migratedDb = Room.databaseBuilder(context, OctoDatabase::class.java, dbFile.absolutePath)
                .addMigrations(
                    OctoDatabase.MIGRATION_1_2,
                    OctoDatabase.MIGRATION_2_3,
                    OctoDatabase.MIGRATION_3_4,
                    OctoDatabase.MIGRATION_4_5
                )
                .allowMainThreadQueries()
                .build()
            try {
                val spaces = migratedDb.octoDao().getAllSpacesSnapshot()
                val apps = migratedDb.octoDao().getAllAppsSnapshot()
                assertEquals(1, spaces.size)
                assertEquals(1, apps.size)
                assertEquals("com.example.migrated", apps.first().packageName)
                assertEquals("Saved note", apps.first().notes)

                val instanceDao = migratedDb.workspaceInstanceDao()
                instanceDao.upsert(
                    com.example.data.local.WorkspaceInstance(
                        instanceId = "migrated_inst",
                        label = "Migrated Instance",
                        targetPackageName = "com.example.migrated",
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

            manager.createInstance("Account A", "com.example.target", instanceId = "inst_a", activate = true).getOrThrow()
            manager.createInstance("Account B", "com.example.target", instanceId = "inst_b", activate = false).getOrThrow()

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
}
