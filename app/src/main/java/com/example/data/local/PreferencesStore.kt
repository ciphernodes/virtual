package com.example.data.local

import android.content.Context
import android.net.Uri
import org.json.JSONArray

class PreferencesStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDarkThemeEnabled(): Boolean = prefs.getBoolean(KEY_DARK_THEME, true)

    fun setDarkThemeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    fun getSelectedSpaceId(): Long? {
        val raw = prefs.getLong(KEY_SELECTED_SPACE_ID, UNSET_ID)
        return raw.takeIf { it > 0L }
    }

    fun setSelectedSpaceId(spaceId: Long?) {
        prefs.edit().putLong(KEY_SELECTED_SPACE_ID, spaceId ?: UNSET_ID).apply()
    }

    fun isPeriodicIntegrityEnabled(): Boolean =
        prefs.getBoolean(KEY_PERIODIC_INTEGRITY, true)

    fun setPeriodicIntegrityEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PERIODIC_INTEGRITY, enabled).apply()
    }

    fun getAttachedVaultUris(): List<Uri> {
        val raw = prefs.getString(KEY_VAULT_URIS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i)
                    if (value.isNotBlank()) {
                        add(Uri.parse(value))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun setAttachedVaultUris(uris: List<Uri>) {
        val array = JSONArray()
        uris.forEach { array.put(it.toString()) }
        prefs.edit().putString(KEY_VAULT_URIS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "cipher_virtual_prefs"
        private const val KEY_DARK_THEME = "dark_theme_enabled"
        private const val KEY_SELECTED_SPACE_ID = "selected_space_id"
        private const val KEY_PERIODIC_INTEGRITY = "periodic_integrity_enabled"
        private const val KEY_VAULT_URIS = "attached_vault_uris"
        private const val UNSET_ID = -1L
    }
}
