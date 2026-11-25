package com.example

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ciphernodes.ciphervirtual.security.PermissionManager
import com.example.data.hardware.DeviceProfileManager
import com.example.data.local.OctoDatabase
import com.example.data.model.DeviceBridgeSnapshot
import com.example.data.model.DevicePermissionItem
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import com.example.ui.OctoUiState
import com.example.ui.components.PermissionPromptHost
import com.example.ui.screens.SandboxConfigScreen
import com.example.ui.theme.OctoSpaceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    fun octoRepository_ensureDefaultSpaces_combinesExistingSpacesIntoOne() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OctoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.octoDao()
            val repo = com.example.data.repository.OctoRepository(
                dao = dao,
                scanner = com.example.data.hardware.DeviceAppScanner(context),
                bridgeManager = com.example.data.hardware.DeviceBridgeManager(context)
            )
            val firstId = dao.insertSpace(VirtualSpaceEntity(name = "Primary", tag = "MAIN"))
            val secondId = dao.insertSpace(VirtualSpaceEntity(name = "Secondary", tag = "DUAL"))
            dao.insertApp(VirtualAppEntity(spaceId = firstId, packageName = "com.example.one", displayName = "One"))
            dao.insertApp(VirtualAppEntity(spaceId = secondId, packageName = "com.example.two", displayName = "Two"))

            repo.ensureDefaultSpaces()

            val spaces = dao.getAllSpacesSnapshot()
            assertEquals(1, spaces.size)
            val mergedApps = dao.observeAppsForSpace(firstId).first()
            assertEquals(2, mergedApps.size)
        } finally {
            db.close()
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
    fun sandboxConfigScreen_displaysCustomProfileAndIntegrityCard() {
        val space = VirtualSpaceEntity(id = 1L, name = "Primary", tag = "MAIN")
        val state = OctoUiState(
            spaces = listOf(space),
            selectedSpaceId = 1L,
            activeDeviceProfile = DeviceProfileManager.rogPhone8Pro(),
            availableDeviceProfiles = DeviceProfileManager.availablePresets()
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
        composeRule.onNodeWithTag("sandbox_config_list")
            .performScrollToNode(hasTestTag("security_dashboard_integrity_card"))
        composeRule.onNodeWithTag("security_dashboard_integrity_card").assertIsDisplayed()
    }

    @Test
    fun permissionManager_requiresBothRuntimeAndSpecialAccessPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = PermissionManager(context)

        val requiredItems = manager.getRequiredPermissionItems()
        assertTrue(requiredItems.any { it.key == "camera" && !it.isSpecialAccess })
        assertTrue(requiredItems.any { it.key == "phone" && !it.isSpecialAccess })
        assertTrue(requiredItems.any { it.key == "sms" && !it.isSpecialAccess })
        assertTrue(requiredItems.any { it.key == "overlay" && it.isSpecialAccess })
        assertTrue(requiredItems.any { it.key == "usage_stats" && it.isSpecialAccess })

        val missingRuntime = manager.getMissingRuntimePermissions().toList()
        assertTrue(missingRuntime.contains(Manifest.permission.CAMERA))
        assertTrue(missingRuntime.contains(Manifest.permission.READ_PHONE_STATE))
        assertTrue(missingRuntime.contains(Manifest.permission.READ_SMS))
        assertTrue(!missingRuntime.contains(Manifest.permission.SYSTEM_ALERT_WINDOW))
        assertEquals("overlay", manager.getNextMissingSpecialAccessKey())
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
}
