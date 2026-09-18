package com.nuvio.tv.features.livetv

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

object LiveTvStorage {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private val isReady: Boolean get() = ::prefs.isInitialized

    fun init(context: Context) {
        prefs = context.getSharedPreferences("nuvio_livetv_prefs", Context.MODE_PRIVATE)
    }

    fun loadPlaylistUrl(): String? = if (isReady) prefs.getString("playlist_url", null) else null
    fun savePlaylistUrl(url: String) {
        if (isReady) prefs.edit().putString("playlist_url", url).apply()
    }

    fun loadPlaylistsBlob(): String? = if (isReady) prefs.getString("playlists_blob", null) else null
    fun savePlaylistsBlob(blob: String) {
        if (isReady) prefs.edit().putString("playlists_blob", blob).apply()
    }

    fun loadFavoriteChannelIdsBlob(): String? = if (isReady) prefs.getString("favorites_blob", null) else null
    fun saveFavoriteChannelIdsBlob(blob: String) {
        if (isReady) prefs.edit().putString("favorites_blob", blob).apply()
    }

    fun loadLastWatchedChannelId(): String? = if (isReady) prefs.getString("last_watched_id", null) else null
    fun saveLastWatchedChannelId(channelId: String) {
        if (isReady) prefs.edit().putString("last_watched_id", channelId).apply()
    }

    fun loadRecentChannelIdsBlob(): String? = if (isReady) prefs.getString("recent_channels_blob", null) else null
    fun saveRecentChannelIdsBlob(blob: String) {
        if (isReady) prefs.edit().putString("recent_channels_blob", blob).apply()
    }

    fun loadNavigationEnabled(): Boolean? = 
        if (isReady && prefs.contains("nav_enabled")) prefs.getBoolean("nav_enabled", true) else null
    fun saveNavigationEnabled(enabled: Boolean) {
        if (isReady) prefs.edit().putBoolean("nav_enabled", enabled).apply()
    }

    fun loadStalkerSettings(): LiveTvStalkerSettings {
        if (!isReady) return LiveTvStalkerSettings()
        val json = prefs.getString("stalker_settings", null) ?: return LiveTvStalkerSettings()
        return runCatching { gson.fromJson(json, LiveTvStalkerSettings::class.java) }.getOrDefault(LiveTvStalkerSettings())
    }
    fun saveStalkerSettings(settings: LiveTvStalkerSettings) {
        if (isReady) prefs.edit().putString("stalker_settings", gson.toJson(settings)).apply()
    }

    fun loadXtreamSettings(): LiveTvXtreamSettings {
        if (!isReady) return LiveTvXtreamSettings()
        val json = prefs.getString("xtream_settings", null) ?: return LiveTvXtreamSettings()
        return runCatching { gson.fromJson(json, LiveTvXtreamSettings::class.java) }.getOrDefault(LiveTvXtreamSettings())
    }
    fun saveXtreamSettings(settings: LiveTvXtreamSettings) {
        if (isReady) prefs.edit().putString("xtream_settings", gson.toJson(settings)).apply()
    }

    fun publishNavigationVisibility(visible: Boolean) {
        // In TV app, this might trigger a menu update or a notification
    }
}
