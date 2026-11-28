package com.cipher.virtual.data.hardware

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class DeviceProfileManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    class CustomProfileBuilder(
        base: DeviceProfile = nativeHostProfile()
    ) {
        private var profileId: String = UUID.randomUUID().toString()
        private var displayName: String = "Custom Profile"
        private var model: String = base.model
        private var manufacturer: String = base.manufacturer
        private var brand: String = base.brand
        private var device: String = base.device
        private var product: String = base.product
        private var board: String = base.board
        private var hardware: String = base.hardware
        private var androidVersion: String = base.androidVersion
        private var sdkInt: Int = base.sdkInt
        private var densityDpi: Int = base.densityDpi

        fun profileId(id: String) = apply {
            if (id.isNotBlank()) this.profileId = id.trim()
        }

        fun displayName(name: String) = apply {
            if (name.isNotBlank()) this.displayName = name.trim()
        }

        fun model(value: String) = apply { if (value.isNotBlank()) this.model = value.trim() }
        fun manufacturer(value: String) = apply { if (value.isNotBlank()) this.manufacturer = value.trim() }
        fun brand(value: String) = apply { if (value.isNotBlank()) this.brand = value.trim() }
        fun device(value: String) = apply { if (value.isNotBlank()) this.device = value.trim() }
        fun product(value: String) = apply { if (value.isNotBlank()) this.product = value.trim() }
        fun board(value: String) = apply { if (value.isNotBlank()) this.board = value.trim() }
        fun hardware(value: String) = apply { if (value.isNotBlank()) this.hardware = value.trim() }
        fun androidVersion(value: String) = apply { if (value.isNotBlank()) this.androidVersion = value.trim() }
        fun sdkInt(value: Int) = apply { this.sdkInt = value.coerceIn(26, 36) }
        fun densityDpi(value: Int) = apply { this.densityDpi = value.coerceIn(160, 640) }

        fun build(): DeviceProfile = DeviceProfile(
            profileId = profileId,
            presetKey = displayName,
            displayName = displayName,
            model = model,
            manufacturer = manufacturer,
            brand = brand,
            device = device,
            product = product,
            board = board,
            hardware = hardware,
            androidVersion = androidVersion,
            sdkInt = sdkInt,
            densityDpi = densityDpi,
            isCustom = true
        )
    }

    fun getAvailableProfiles(): List<DeviceProfile> {
        return availablePresets() + getCustomProfiles()
    }

    fun getCustomProfiles(): List<DeviceProfile> {
        val raw = prefs.getString(KEY_CUSTOM_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(DeviceProfile.fromJson(obj))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun getActiveProfile(spaceId: Long? = null): DeviceProfile {
        val scopedKey = activeProfileStorageKey(spaceId)
        val raw = prefs.getString(scopedKey, null)
            ?: prefs.getString(KEY_ACTIVE_PROFILE, null)
        if (!raw.isNullOrBlank()) {
            runCatching {
                return DeviceProfile.fromJson(JSONObject(raw))
            }
        }
        return rogPhone8Pro()
    }

    fun resolveForPresetName(presetName: String, spaceId: Long? = null): DeviceProfile {
        val customMatch = getCustomProfiles().firstOrNull {
            it.presetKey.equals(presetName, ignoreCase = true) ||
                it.displayName.equals(presetName, ignoreCase = true)
        }
        if (customMatch != null) {
            setActiveProfile(customMatch, spaceId)
            return customMatch
        }
        val active = getActiveProfile(spaceId)
        if (active.presetKey.equals(presetName, ignoreCase = true)) {
            return active
        }
        val preset = presetForName(presetName)
        setActiveProfile(preset, spaceId)
        return preset
    }

    fun selectPreset(presetName: String, spaceId: Long? = null): DeviceProfile {
        val customMatch = getCustomProfiles().firstOrNull {
            it.presetKey.equals(presetName, ignoreCase = true) ||
                it.displayName.equals(presetName, ignoreCase = true)
        }
        val chosen = customMatch ?: presetForName(presetName)
        setActiveProfile(chosen, spaceId)
        return chosen
    }

    fun saveCustomProfile(profile: DeviceProfile, spaceId: Long? = null): DeviceProfile {
        val normalized = profile.copy(
            presetKey = profile.displayName.ifBlank { "Custom Profile" },
            isCustom = true
        )
        val existing = getCustomProfiles().filterNot { it.profileId == normalized.profileId }
        val updated = (listOf(normalized) + existing).take(MAX_CUSTOM_PROFILES)
        val jsonArray = JSONArray()
        updated.forEach { jsonArray.put(it.toJson()) }
        prefs.edit().putString(KEY_CUSTOM_PROFILES, jsonArray.toString()).apply()
        setActiveProfile(normalized, spaceId)
        return normalized
    }

    fun deleteCustomProfile(profileId: String, spaceId: Long? = null) {
        val remaining = getCustomProfiles().filterNot { it.profileId == profileId }
        val jsonArray = JSONArray()
        remaining.forEach { jsonArray.put(it.toJson()) }
        prefs.edit().putString(KEY_CUSTOM_PROFILES, jsonArray.toString()).apply()
        val current = getActiveProfile(spaceId)
        if (current.profileId == profileId) {
            setActiveProfile(rogPhone8Pro(), spaceId)
        }
    }

    fun setActiveProfile(profile: DeviceProfile, spaceId: Long? = null) {
        val serialized = profile.toJson().toString()
        prefs.edit()
            .putString(KEY_ACTIVE_PROFILE, serialized)
            .apply {
                if (spaceId != null) {
                    putString(activeProfileStorageKey(spaceId), serialized)
                }
            }
            .apply()
    }

    fun getProfileUuid(spaceId: Long? = null): String = getActiveProfile(spaceId).profileId
    fun getModel(spaceId: Long? = null): String = getActiveProfile(spaceId).model
    fun getManufacturer(spaceId: Long? = null): String = getActiveProfile(spaceId).manufacturer
    fun getBrand(spaceId: Long? = null): String = getActiveProfile(spaceId).brand
    fun getDevice(spaceId: Long? = null): String = getActiveProfile(spaceId).device
    fun getProduct(spaceId: Long? = null): String = getActiveProfile(spaceId).product
    fun getBoard(spaceId: Long? = null): String = getActiveProfile(spaceId).board
    fun getHardware(spaceId: Long? = null): String = getActiveProfile(spaceId).hardware
    fun getAndroidVersion(spaceId: Long? = null): String = getActiveProfile(spaceId).androidVersion
    fun getSdkInt(spaceId: Long? = null): Int = getActiveProfile(spaceId).sdkInt
    fun getDensityDpi(spaceId: Long? = null): Int = getActiveProfile(spaceId).densityDpi

    private fun activeProfileStorageKey(spaceId: Long?): String {
        return if (spaceId != null) "${KEY_SPACE_PROFILE_PREFIX}$spaceId" else KEY_ACTIVE_PROFILE
    }

    companion object {
        private const val PREFS_NAME = "cipher_device_profiles"
        private const val KEY_ACTIVE_PROFILE = "active_device_profile"
        private const val KEY_SPACE_PROFILE_PREFIX = "space_device_profile_"
        private const val KEY_CUSTOM_PROFILES = "custom_device_profiles"
        private const val MAX_CUSTOM_PROFILES = 12

        fun pixel8(): DeviceProfile = DeviceProfile(
            profileId = UUID.nameUUIDFromBytes("preset:pixel_8".toByteArray()).toString(),
            presetKey = "Pixel 8",
            displayName = "Pixel 8",
            model = "Pixel 8",
            manufacturer = "Google",
            brand = "google",
            device = "shiba",
            product = "shiba",
            board = "shiba",
            hardware = "shiba",
            androidVersion = "14",
            sdkInt = 34,
            densityDpi = 420
        )

        fun pixel9Pro(): DeviceProfile = DeviceProfile(
            profileId = UUID.nameUUIDFromBytes("preset:pixel_9_pro".toByteArray()).toString(),
            presetKey = "Pixel 9 Pro",
            displayName = "Pixel 9 Pro",
            model = "Pixel 9 Pro",
            manufacturer = "Google",
            brand = "google",
            device = "caiman",
            product = "caiman",
            board = "caiman",
            hardware = "caiman",
            androidVersion = "15",
            sdkInt = 35,
            densityDpi = 480
        )

        fun galaxyS24(): DeviceProfile = DeviceProfile(
            profileId = UUID.nameUUIDFromBytes("preset:galaxy_s24".toByteArray()).toString(),
            presetKey = "Galaxy S24",
            displayName = "Galaxy S24",
            model = "SM-S921B",
            manufacturer = "samsung",
            brand = "samsung",
            device = "e1s",
            product = "e1sx",
            board = "s5e9945",
            hardware = "s5e9945",
            androidVersion = "14",
            sdkInt = 34,
            densityDpi = 420
        )

        fun galaxyS24Ultra(): DeviceProfile = DeviceProfile(
            profileId = UUID.nameUUIDFromBytes("preset:galaxy_s24_ultra".toByteArray()).toString(),
            presetKey = "Galaxy S24 Ultra",
            displayName = "Galaxy S24 Ultra",
            model = "SM-S928B",
            manufacturer = "samsung",
            brand = "samsung",
            device = "e3q",
            product = "e3qx",
            board = "pineapple",
            hardware = "qcom",
            androidVersion = "15",
            sdkInt = 35,
            densityDpi = 480
        )

        fun rogPhone8Pro(): DeviceProfile = DeviceProfile(
            profileId = UUID.nameUUIDFromBytes("preset:rog_phone_8_pro".toByteArray()).toString(),
            presetKey = "ROG Phone 8 Pro",
            displayName = "ROG Phone 8 Pro",
            model = "ASUS_AI2401",
            manufacturer = "asus",
            brand = "asus",
            device = "AI2401",
            product = "WW_AI2401",
            board = "pineapple",
            hardware = "qcom",
            androidVersion = "14",
            sdkInt = 34,
            densityDpi = 440
        )

        fun redMagic9SPro(): DeviceProfile = DeviceProfile(
            profileId = UUID.nameUUIDFromBytes("preset:redmagic_9s_pro".toByteArray()).toString(),
            presetKey = "RedMagic 9S Pro",
            displayName = "RedMagic 9S Pro",
            model = "NX769J",
            manufacturer = "nubia",
            brand = "nubia",
            device = "NX769J",
            product = "NX769J",
            board = "pineapple",
            hardware = "qcom",
            androidVersion = "14",
            sdkInt = 34,
            densityDpi = 440
        )

        fun nativeHostProfile(): DeviceProfile = DeviceProfile(
            profileId = UUID.nameUUIDFromBytes("preset:native_host".toByteArray()).toString(),
            presetKey = "Native Hardware",
            displayName = "Native Hardware",
            model = runCatching { Build.MODEL }.getOrNull().orEmpty().ifBlank { "Android" },
            manufacturer = runCatching { Build.MANUFACTURER }.getOrNull().orEmpty().ifBlank { "Android" },
            brand = runCatching { Build.BRAND }.getOrNull().orEmpty().ifBlank { "android" },
            device = runCatching { Build.DEVICE }.getOrNull().orEmpty().ifBlank { "generic" },
            product = runCatching { Build.PRODUCT }.getOrNull().orEmpty().ifBlank { "generic" },
            board = runCatching { Build.BOARD }.getOrNull().orEmpty().ifBlank { "unknown" },
            hardware = runCatching { Build.HARDWARE }.getOrNull().orEmpty().ifBlank { "unknown" },
            androidVersion = runCatching { Build.VERSION.RELEASE }.getOrNull().orEmpty().ifBlank { "15" },
            sdkInt = runCatching { Build.VERSION.SDK_INT }.getOrDefault(35).let { if (it > 0) it else 35 },
            densityDpi = 440
        )

        fun availablePresets(): List<DeviceProfile> = listOf(
            rogPhone8Pro(),
            pixel9Pro(),
            pixel8(),
            galaxyS24Ultra(),
            galaxyS24(),
            redMagic9SPro(),
            nativeHostProfile()
        )

        fun presetForName(name: String): DeviceProfile {
            return availablePresets().firstOrNull {
                it.presetKey.equals(name, ignoreCase = true) ||
                    it.displayName.equals(name, ignoreCase = true)
            } ?: rogPhone8Pro()
        }
    }
}
