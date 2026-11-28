package com.cipher.virtual.ui

import com.cipher.virtual.R
import com.cipher.virtual.data.hardware.DeviceProfile
import com.cipher.virtual.data.hardware.DeviceProfileManager
import com.cipher.virtual.data.model.VirtualSpaceEntity
import com.cipher.virtual.data.repository.OctoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Encapsulates hardware preset selection, custom device profile persistence, and space reset logic
 * for [OctoViewModel].
 */
internal class DeviceProfileStateController(
    private val repository: OctoRepository,
    private val deviceProfileManager: DeviceProfileManager?,
    private val formatText: (Int, String, Array<out Any>) -> String
) {
    private val _activeProfile = MutableStateFlow(DeviceProfileManager.rogPhone8Pro())
    private val _availableProfiles = MutableStateFlow(
        deviceProfileManager?.getAvailableProfiles() ?: DeviceProfileManager.availablePresets()
    )

    val activeProfile: StateFlow<DeviceProfile> = _activeProfile.asStateFlow()
    val availableProfiles: StateFlow<List<DeviceProfile>> = _availableProfiles.asStateFlow()

    fun syncForActiveSpace(space: VirtualSpaceEntity?) {
        if (space != null && deviceProfileManager != null) {
            _activeProfile.value =
                deviceProfileManager.resolveForPresetName(space.deviceModelPreset, space.id)
        }
    }

    suspend fun selectPreset(space: VirtualSpaceEntity, profile: DeviceProfile) {
        val resolved = deviceProfileManager?.selectPreset(profile.presetKey, space.id) ?: profile
        _activeProfile.value = resolved
        repository.updateSpace(
            space.copy(deviceModelPreset = resolved.presetKey, virtualDpi = resolved.densityDpi)
        )
    }

    suspend fun saveCustom(space: VirtualSpaceEntity, profile: DeviceProfile): String {
        val saved = deviceProfileManager?.saveCustomProfile(profile, space.id) ?: profile
        _activeProfile.value = saved
        _availableProfiles.value =
            deviceProfileManager?.getAvailableProfiles() ?: DeviceProfileManager.availablePresets()
        repository.updateSpace(
            space.copy(deviceModelPreset = saved.presetKey, virtualDpi = saved.densityDpi)
        )
        return formatText(
            R.string.status_custom_profile_saved,
            "Saved custom profile \"${saved.displayName}\"",
            arrayOf(saved.displayName)
        )
    }

    suspend fun deleteCustom(space: VirtualSpaceEntity?, profileId: String): String {
        deviceProfileManager?.deleteCustomProfile(profileId, space?.id)
        _availableProfiles.value =
            deviceProfileManager?.getAvailableProfiles() ?: DeviceProfileManager.availablePresets()
        val fallback = deviceProfileManager?.getActiveProfile(space?.id) ?: DeviceProfileManager.rogPhone8Pro()
        _activeProfile.value = fallback
        if (space != null) {
            repository.updateSpace(
                space.copy(deviceModelPreset = fallback.presetKey, virtualDpi = fallback.densityDpi)
            )
        }
        return formatText(
            R.string.status_custom_profile_removed,
            "Removed custom hardware profile",
            emptyArray()
        )
    }

    suspend fun resetSpaceDefaults(space: VirtualSpaceEntity): String {
        val defaultProfile = DeviceProfileManager.rogPhone8Pro()
        deviceProfileManager?.selectPreset(defaultProfile.presetKey, space.id)?.let {
            _activeProfile.value = it
        }
        repository.updateSpace(
            space.copy(
                deviceModelPreset = defaultProfile.presetKey,
                virtualDpi = defaultProfile.densityDpi,
                isolatedStorage = true,
                frameRateHud = true,
                permissionBridgeEnabled = true
            )
        )
        return formatText(
            R.string.status_defaults_restored,
            "Restored default sandbox settings for ${space.name}",
            arrayOf(space.name)
        )
    }
}
