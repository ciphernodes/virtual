package com.cipher.virtual.data.local

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.cipherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cipher_virtual_datastore",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "cipher_virtual_prefs"))
    }
)

class PreferencesStore(context: Context) {

    private val dataStore = context.applicationContext.cipherDataStore

    val darkThemeFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DARK_THEME] ?: true
    }

    val periodicIntegrityFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_PERIODIC_INTEGRITY] ?: true
    }

    val attachedVaultUrisFlow: Flow<List<Uri>> = dataStore.data.map { prefs ->
        parseUriList(prefs[KEY_VAULT_URIS])
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_DARK_THEME] = enabled }
    }

    suspend fun setPeriodicIntegrityEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_PERIODIC_INTEGRITY] = enabled }
    }

    suspend fun setAttachedVaultUris(uris: List<Uri>) {
        val array = JSONArray()
        uris.forEach { array.put(it.toString()) }
        dataStore.edit { prefs -> prefs[KEY_VAULT_URIS] = array.toString() }
    }

    suspend fun getDarkThemeEnabled(): Boolean = darkThemeFlow.first()

    suspend fun getPeriodicIntegrityEnabled(): Boolean = periodicIntegrityFlow.first()

    suspend fun getAttachedVaultUris(): List<Uri> = attachedVaultUrisFlow.first()

    private fun parseUriList(raw: String?): List<Uri> {
        if (raw.isNullOrBlank()) return emptyList()
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

    companion object {
        private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme_enabled")
        private val KEY_PERIODIC_INTEGRITY = booleanPreferencesKey("periodic_integrity_enabled")
        private val KEY_VAULT_URIS = stringPreferencesKey("attached_vault_uris")
    }
}
