package com.nuvio.tv.features.livetv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object LiveTvStorage {
    private const val TAG = "LiveTvStorage"
    private const val KEY_PLAYLIST_URL = "playlist_url"
    private const val KEY_PLAYLISTS_BLOB = "playlists_blob"
    private const val KEY_FAVORITES_BLOB = "favorites_blob"
    private const val KEY_LAST_WATCHED_ID = "last_watched_id"
    private const val KEY_RECENT_CHANNELS_BLOB = "recent_channels_blob"
    private const val KEY_NAV_ENABLED = "nav_enabled"
    private const val KEY_STALKER_SETTINGS = "stalker_settings"
    private const val KEY_XTREAM_SETTINGS = "xtream_settings"
    private const val KEY_LAST_REFRESH_TIME = "last_channels_refresh_time"
    private const val KEY_CACHE_CONFIG_SIGNATURE = "cache_config_signature"
    private const val CHANNELS_CACHE_FILE = "livetv_channels_cache.json"

    const val CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24 hours

    private lateinit var prefs: SharedPreferences
    private var appContext: Context? = null
    private val gson = Gson()
    private val isReady: Boolean get() = ::prefs.isInitialized

    @Volatile
    private var memoryCachedChannels: List<LiveTvChannel>? = null

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
        return runCatching { gson.fromJson(json, LiveTvStalkerSettings::class.java) }.getOrDefault(LiveTvStalkerSettings())
    }
    fun saveStalkerSettings(settings: LiveTvStalkerSettings) {
        if (isReady) prefs.edit().putString(KEY_STALKER_SETTINGS, gson.toJson(settings)).apply()
    }

    fun loadXtreamSettings(): LiveTvXtreamSettings {
        if (!isReady) return LiveTvXtreamSettings()
        val json = prefs.getString(KEY_XTREAM_SETTINGS, null) ?: return LiveTvXtreamSettings()
        return runCatching { gson.fromJson(json, LiveTvXtreamSettings::class.java) }.getOrDefault(LiveTvXtreamSettings())
    }
    fun saveXtreamSettings(settings: LiveTvXtreamSettings) {
        if (isReady) prefs.edit().putString(KEY_XTREAM_SETTINGS, gson.toJson(settings)).apply()
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
        memoryCachedChannels = channels
        val dir = appContext?.filesDir ?: return
        try {
            val cacheFile = File(dir, CHANNELS_CACHE_FILE)
            val tmpFile = File(dir, "$CHANNELS_CACHE_FILE.tmp")
            tmpFile.bufferedWriter().use { writer ->
                val listType = object : TypeToken<List<LiveTvChannel>>() {}.type
                gson.toJson(channels, listType, writer)
            }
            if (tmpFile.exists()) {
                if (cacheFile.exists()) cacheFile.delete()
                tmpFile.renameTo(cacheFile)
            }
            saveLastRefreshTime(System.currentTimeMillis())
            saveCacheConfigSignature(signature)
            Log.d(TAG, "Saved ${channels.size} channels to cache successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save channels cache", e)
        }
    }

    fun loadChannelsCache(): List<LiveTvChannel> {
        val inMemory = memoryCachedChannels
        if (inMemory != null && inMemory.isNotEmpty()) {
            return inMemory
        }
        val dir = appContext?.filesDir ?: return emptyList()
        val cacheFile = File(dir, CHANNELS_CACHE_FILE)
        if (!cacheFile.exists() || cacheFile.length() == 0L) return emptyList()
        return runCatching {
            cacheFile.bufferedReader().use { reader ->
                val listType = object : TypeToken<List<LiveTvChannel>>() {}.type
                val channels: List<LiveTvChannel>? = gson.fromJson(reader, listType)
                (channels ?: emptyList()).also {
                    memoryCachedChannels = it
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to load channels cache", e)
        }.getOrDefault(emptyList())
    }

    fun clearChannelsCache() {
        memoryCachedChannels = null
        val dir = appContext?.filesDir
        if (dir != null) {
            val cacheFile = File(dir, CHANNELS_CACHE_FILE)
            if (cacheFile.exists()) cacheFile.delete()
            val tmpFile = File(dir, "$CHANNELS_CACHE_FILE.tmp")
            if (tmpFile.exists()) tmpFile.delete()
        }
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
