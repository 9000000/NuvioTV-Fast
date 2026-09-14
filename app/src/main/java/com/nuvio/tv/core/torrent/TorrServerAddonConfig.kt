package com.nuvio.tv.core.torrent

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.torrServerAddonDataStore by preferencesDataStore(
    name = "torrserver_addon_config",
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler {
        androidx.datastore.preferences.core.emptyPreferences()
    }
)

data class TorrServerAddonConfigData(
    val enabled: Boolean = false,
    val serverUrl: String = "http://127.0.0.1:8090",
    val addonUrl: String = "",
    val authUsername: String = "",
    val authPassword: String = "",
    val preload: Boolean = true,
    val saveToDb: Boolean = false,
    val gst: Boolean = false
)

@Singleton
class TorrServerAddonConfig @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val SERVER_URL = stringPreferencesKey("server_url")
        val ADDON_URL = stringPreferencesKey("addon_url")
        val AUTH_USERNAME = stringPreferencesKey("auth_username")
        val AUTH_PASSWORD = stringPreferencesKey("auth_password")
        val PRELOAD = booleanPreferencesKey("preload")
        val SAVE_TO_DB = booleanPreferencesKey("save_to_db")
        val GST = booleanPreferencesKey("gst")
    }

    val config: Flow<TorrServerAddonConfigData> = context.torrServerAddonDataStore.data
        .map { prefs ->
            TorrServerAddonConfigData(
                enabled = prefs[Keys.ENABLED] ?: false,
                serverUrl = prefs[Keys.SERVER_URL] ?: "http://127.0.0.1:8090",
                addonUrl = prefs[Keys.ADDON_URL] ?: "",
                authUsername = prefs[Keys.AUTH_USERNAME] ?: "",
                authPassword = prefs[Keys.AUTH_PASSWORD] ?: "",
                preload = prefs[Keys.PRELOAD] ?: true,
                saveToDb = prefs[Keys.SAVE_TO_DB] ?: false,
                gst = prefs[Keys.GST] ?: false
            )
        }
        .distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        scope.launch {
            context.torrServerAddonDataStore.edit { it[Keys.ENABLED] = enabled }
        }
    }

    fun setServerUrl(url: String) {
        scope.launch {
            val normalized = url.trim().trimEnd('/')
            context.torrServerAddonDataStore.edit { it[Keys.SERVER_URL] = normalized }
        }
    }

    fun setAddonUrl(url: String) {
        scope.launch {
            val normalized = url.trim()
            context.torrServerAddonDataStore.edit { it[Keys.ADDON_URL] = normalized }
        }
    }

    fun setCredentials(username: String, password: String) {
        scope.launch {
            context.torrServerAddonDataStore.edit {
                it[Keys.AUTH_USERNAME] = username.trim()
                it[Keys.AUTH_PASSWORD] = password
            }
        }
    }

    fun setPreload(preload: Boolean) {
        scope.launch {
            context.torrServerAddonDataStore.edit { it[Keys.PRELOAD] = preload }
        }
    }

    fun setSaveToDb(saveToDb: Boolean) {
        scope.launch {
            context.torrServerAddonDataStore.edit { it[Keys.SAVE_TO_DB] = saveToDb }
        }
    }

    fun setGst(gst: Boolean) {
        scope.launch {
            context.torrServerAddonDataStore.edit { it[Keys.GST] = gst }
        }
    }

    suspend fun updateConfig(config: TorrServerAddonConfigData) {
        context.torrServerAddonDataStore.edit {
            it[Keys.ENABLED] = config.enabled
            it[Keys.SERVER_URL] = config.serverUrl.trim().trimEnd('/')
            it[Keys.ADDON_URL] = config.addonUrl.trim()
            it[Keys.AUTH_USERNAME] = config.authUsername.trim()
            it[Keys.AUTH_PASSWORD] = config.authPassword
            it[Keys.PRELOAD] = config.preload
            it[Keys.SAVE_TO_DB] = config.saveToDb
            it[Keys.GST] = config.gst
        }
    }

    suspend fun clear() {
        context.torrServerAddonDataStore.edit { it.clear() }
    }
}
