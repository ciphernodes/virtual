package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.hardware.DeviceProfileManager
import com.example.data.local.OctoDatabase
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import com.example.ui.OctoUiState
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
}
