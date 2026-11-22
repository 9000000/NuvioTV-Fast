package com.nuvio.tv.features.livetv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson

object LiveTvStorage {
    private const val TAG = "LiveTvStorage"
    private const val KEY_PLAYLIST_URL = "playlist_url"
    private const val KEY_PLAYLISTS_BLOB = "playlists_blob"
    private const val KEY_FAVORITES_BLOB = "favorites_blob"
    private const val KEY_LAST_WATCHED_ID = "last_watched_id"
    private const val KEY_LAST_WATCHED_PLAYLIST_ID = "last_watched_playlist_id"
    private const val KEY_LAST_WATCHED_GROUP = "last_watched_group"
    private const val KEY_LAST_WATCHED_FILTER_TYPE = "last_watched_filter_type"
    private const val KEY_GUIDE_PLAYLIST_ID = "guide_selected_playlist_id"
    private const val KEY_GUIDE_GROUP_ID = "guide_selected_group_id"
    private const val ALL_PLAYLISTS_SENTINEL = "__all__"
    private const val KEY_RECENT_CHANNELS_BLOB = "recent_channels_blob"
    private const val KEY_NAV_ENABLED = "nav_enabled"
    private const val KEY_STALKER_SETTINGS = "stalker_settings"
    private const val KEY_XTREAM_SETTINGS = "xtream_settings"
    private const val KEY_LIVETV_SETTINGS = "livetv_settings"
    private const val KEY_LAST_REFRESH_TIME = "last_channels_refresh_time"
    private const val KEY_CACHE_CONFIG_SIGNATURE = "cache_config_signature"

    const val CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24 hours

    private lateinit var prefs: SharedPreferences
    private var appContext: Context? = null
    private val gson = Gson()
    private val isReady: Boolean get() = ::prefs.isInitialized

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences("nuvio_livetv_prefs", Context.MODE_PRIVATE)
    }

    fun loadPlaylistUrl(): String? = if (isReady) prefs.getString(KEY_PLAYLIST_URL, null) else null
    fun savePlaylistUrl(url: String) {
        if (isReady) prefs.edit().putString(KEY_PLAYLIST_URL, url).apply()
    }

    fun loadPlaylistsBlob(): String? = if (isReady) prefs.getString(KEY_PLAYLISTS_BLOB, null) else null
    fun savePlaylistsBlob(blob: String) {
        if (isReady) prefs.edit().putString(KEY_PLAYLISTS_BLOB, blob).apply()
    }

    fun loadFavoriteChannelIdsBlob(): String? = if (isReady) prefs.getString(KEY_FAVORITES_BLOB, null) else null
    fun saveFavoriteChannelIdsBlob(blob: String) {
        if (isReady) prefs.edit().putString(KEY_FAVORITES_BLOB, blob).apply()
    }

    fun loadLastWatchedChannelId(): String? = if (isReady) prefs.getString(KEY_LAST_WATCHED_ID, null) else null
    fun saveLastWatchedChannelId(channelId: String) {
        if (isReady) prefs.edit().putString(KEY_LAST_WATCHED_ID, channelId).apply()
    }

    fun loadLastWatchedPlaylistId(): String? = if (isReady) prefs.getString(KEY_LAST_WATCHED_PLAYLIST_ID, null) else null
    fun saveLastWatchedPlaylistId(playlistId: String?) {
        if (isReady) {
            prefs.edit().apply {
                if (playlistId != null) putString(KEY_LAST_WATCHED_PLAYLIST_ID, playlistId)
                else remove(KEY_LAST_WATCHED_PLAYLIST_ID)
            }.apply()
        }
    }

    fun loadLastWatchedGroup(): String? = if (isReady) prefs.getString(KEY_LAST_WATCHED_GROUP, null) else null
    fun saveLastWatchedGroup(group: String?) {
        if (isReady) {
            prefs.edit().apply {
                if (group != null) putString(KEY_LAST_WATCHED_GROUP, group)
                else remove(KEY_LAST_WATCHED_GROUP)
            }.apply()
        }
    }

    fun loadLastWatchedFilterType(): String? = if (isReady) prefs.getString(KEY_LAST_WATCHED_FILTER_TYPE, null) else null
    fun saveLastWatchedFilterType(filterType: String?) {
        if (isReady) {
            prefs.edit().apply {
                if (filterType != null) putString(KEY_LAST_WATCHED_FILTER_TYPE, filterType)
                else remove(KEY_LAST_WATCHED_FILTER_TYPE)
            }.apply()
        }
    }

    fun loadGuidePlaylistId(): String? {
        if (!isReady) return null
        if (!prefs.contains(KEY_GUIDE_PLAYLIST_ID)) {
            return loadLastWatchedPlaylistId()
        }
        val stored = prefs.getString(KEY_GUIDE_PLAYLIST_ID, null)
        return stored?.takeUnless { it == ALL_PLAYLISTS_SENTINEL }
    }

    fun saveGuidePlaylistId(playlistId: String?) {
        if (isReady) {
            prefs.edit()
                .putString(KEY_GUIDE_PLAYLIST_ID, playlistId ?: ALL_PLAYLISTS_SENTINEL)
                .apply()
        }
    }

    fun loadGuideGroupId(): String? = if (isReady) prefs.getString(KEY_GUIDE_GROUP_ID, null) else null
    fun saveGuideGroupId(groupId: String?) {
        if (isReady) {
            prefs.edit().apply {
                if (groupId != null) putString(KEY_GUIDE_GROUP_ID, groupId)
                else remove(KEY_GUIDE_GROUP_ID)
            }.apply()
        }
    }

    fun loadRecentChannelIdsBlob(): String? = if (isReady) prefs.getString(KEY_RECENT_CHANNELS_BLOB, null) else null
    fun saveRecentChannelIdsBlob(blob: String) {
        if (isReady) prefs.edit().putString(KEY_RECENT_CHANNELS_BLOB, blob).apply()
    }

    fun loadNavigationEnabled(): Boolean? = 
        if (isReady && prefs.contains(KEY_NAV_ENABLED)) prefs.getBoolean(KEY_NAV_ENABLED, true) else null
    fun saveNavigationEnabled(enabled: Boolean) {
        if (isReady) prefs.edit().putBoolean(KEY_NAV_ENABLED, enabled).apply()
    }

    fun loadStalkerSettings(): LiveTvStalkerSettings {
        if (!isReady) return LiveTvStalkerSettings()
        val json = prefs.getString(KEY_STALKER_SETTINGS, null) ?: return LiveTvStalkerSettings()
        val parsed = runCatching { gson.fromJson(json, LiveTvStalkerSettings::class.java) }.getOrNull()
            ?: return LiveTvStalkerSettings()
        return sanitizeStalkerSettings(parsed)
    }
    fun saveStalkerSettings(settings: LiveTvStalkerSettings) {
        if (isReady) prefs.edit().putString(KEY_STALKER_SETTINGS, gson.toJson(settings)).apply()
    }

    fun loadXtreamSettings(): LiveTvXtreamSettings {
        if (!isReady) return LiveTvXtreamSettings()
        val json = prefs.getString(KEY_XTREAM_SETTINGS, null) ?: return LiveTvXtreamSettings()
        val parsed = runCatching { gson.fromJson(json, LiveTvXtreamSettings::class.java) }.getOrNull()
            ?: return LiveTvXtreamSettings()
        return sanitizeXtreamSettings(parsed)
    }
    fun saveXtreamSettings(settings: LiveTvXtreamSettings) {
        if (isReady) prefs.edit().putString(KEY_XTREAM_SETTINGS, gson.toJson(settings)).apply()
    }

    fun loadLiveTvSettings(): LiveTvSettings {
        if (!isReady) return LiveTvSettings()
        val json = prefs.getString(KEY_LIVETV_SETTINGS, null) ?: return LiveTvSettings()
        val parsed = runCatching { gson.fromJson(json, LiveTvSettings::class.java) }.getOrNull()
            ?: return LiveTvSettings()
        return sanitizeLiveTvSettings(parsed)
    }

    private fun safeString(value: Any?, fallback: String = ""): String =
        (value as? String) ?: fallback

    private fun sanitizeStalkerSettings(raw: LiveTvStalkerSettings): LiveTvStalkerSettings {
        val portal = safeString(raw.portalUrl)
        val mac = safeString(raw.macAddress)
        val user = safeString(raw.username)
        val pass = safeString(raw.password)
        return if ((raw.portalUrl as Any?) == null ||
            (raw.macAddress as Any?) == null ||
            (raw.username as Any?) == null ||
            (raw.password as Any?) == null
        ) {
            raw.copy(portalUrl = portal, macAddress = mac, username = user, password = pass)
        } else {
            raw
        }
    }

    private fun sanitizeXtreamSettings(raw: LiveTvXtreamSettings): LiveTvXtreamSettings {
        val server = safeString(raw.serverUrl)
        val user = safeString(raw.username)
        val pass = safeString(raw.password)
        return if ((raw.serverUrl as Any?) == null ||
            (raw.username as Any?) == null ||
            (raw.password as Any?) == null
        ) {
            raw.copy(serverUrl = server, username = user, password = pass)
        } else {
            raw
        }
    }

    private fun sanitizeLiveTvSettings(raw: LiveTvSettings): LiveTvSettings {
        val guideSize = safeString(raw.guideSize, "STANDARD").ifBlank { "STANDARD" }
        val aspectMode = safeString(raw.aspectMode, "ORIGINAL").ifBlank { "ORIGINAL" }
        val customEpg = safeString(raw.customEpgUrl, "")
        return if ((raw.guideSize as Any?) == null ||
            (raw.aspectMode as Any?) == null ||
            (raw.customEpgUrl as Any?) == null
        ) {
            // Legacy R8-obfuscated settings JSON cannot be mapped safely — restore defaults cleanly
            LiveTvSettings(
                guideSize = guideSize,
                aspectMode = aspectMode,
                customEpgUrl = customEpg
            )
        } else {
            raw
        }
    }

    fun saveLiveTvSettings(settings: LiveTvSettings) {
        if (isReady) prefs.edit().putString(KEY_LIVETV_SETTINGS, gson.toJson(settings)).apply()
    }

    fun loadLastRefreshTime(): Long =
        if (isReady) prefs.getLong(KEY_LAST_REFRESH_TIME, 0L) else 0L

    fun saveLastRefreshTime(timeMs: Long) {
        if (isReady) prefs.edit().putLong(KEY_LAST_REFRESH_TIME, timeMs).apply()
    }

    fun loadCacheConfigSignature(): String? =
        if (isReady) prefs.getString(KEY_CACHE_CONFIG_SIGNATURE, null) else null

    fun saveCacheConfigSignature(signature: String) {
        if (isReady) prefs.edit().putString(KEY_CACHE_CONFIG_SIGNATURE, signature).apply()
    }

    fun isCacheExpired(): Boolean {
        val lastRefresh = loadLastRefreshTime()
        if (lastRefresh <= 0L) return true
        val diff = System.currentTimeMillis() - lastRefresh
        return diff >= CACHE_EXPIRATION_MS || diff < 0L
    }

    fun saveChannelsCache(channels: List<LiveTvChannel>, signature: String) {
        LiveTvCacheManager.saveChannels(appContext, channels, signature)
    }

    fun loadChannelsCache(): List<LiveTvChannel> {
        return LiveTvCacheManager.loadChannels(appContext)
    }

    fun clearChannelsCache() {
        LiveTvCacheManager.clearCache(appContext)
        if (isReady) {
            prefs.edit()
                .remove(KEY_LAST_REFRESH_TIME)
                .remove(KEY_CACHE_CONFIG_SIGNATURE)
                .apply()
        }
        Log.d(TAG, "Cleared channels cache")
    }

    fun publishNavigationVisibility(visible: Boolean) {
        // In TV app, this might trigger a menu update or a notification
    }
}
