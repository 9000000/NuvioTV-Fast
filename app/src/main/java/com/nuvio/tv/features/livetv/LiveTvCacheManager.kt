package com.nuvio.tv.features.livetv

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File

/**
 * Cache manager for Live TV channels, playlist metadata, and groups.
 * Provides memory-efficient streaming serialization/deserialization to minimize Heap RAM usage.
 * Prevents OutOfMemory errors and GC thrashing on low-RAM TV boxes (1GB-2GB).
 */
internal object LiveTvCacheManager {
    private const val TAG = "LiveTvCacheManager"
    private const val CHANNELS_CACHE_FILE = "livetv_channels_cache.json"
    private const val GROUPS_CACHE_FILE = "livetv_groups_cache.json"

    private val gson = Gson()
    private val channelListType = object : TypeToken<List<LiveTvChannel>>() {}.type
    private val groupListType = object : TypeToken<List<String>>() {}.type

    @Volatile
    private var memoryCachedChannels: List<LiveTvChannel>? = null

    @Volatile
    private var memoryCachedGroups: List<String>? = null

    /**
     * Clear all in-memory and disk caches.
     */
    fun clearCache(context: Context?) {
        memoryCachedChannels = null
        memoryCachedGroups = null
        val dir = context?.applicationContext?.filesDir ?: return
        runCatching {
            File(dir, CHANNELS_CACHE_FILE).takeIf { it.exists() }?.delete()
            File(dir, GROUPS_CACHE_FILE).takeIf { it.exists() }?.delete()
        }
    }

    /**
     * Save channels to disk cache using buffered streaming and atomic file rename.
     * Also extracts and caches channel groups for instant category indexing.
     */
    fun saveChannels(context: Context?, channels: List<LiveTvChannel>, signature: String) {
        memoryCachedChannels = channels
        val groups = channels.mapNotNull { it.group?.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        memoryCachedGroups = groups

        val dir = context?.applicationContext?.filesDir ?: return
        try {
            // 1. Stream write channels cache
            val channelsFile = File(dir, CHANNELS_CACHE_FILE)
            val channelsTmp = File(dir, "$CHANNELS_CACHE_FILE.tmp")
            channelsTmp.bufferedWriter().use { writer ->
                val jsonWriter = JsonWriter(writer)
                gson.toJson(channels, channelListType, jsonWriter)
                jsonWriter.flush()
            }
            if (channelsTmp.exists()) {
                if (channelsFile.exists()) channelsFile.delete()
                channelsTmp.renameTo(channelsFile)
            }

            // 2. Stream write groups cache
            val groupsFile = File(dir, GROUPS_CACHE_FILE)
            val groupsTmp = File(dir, "$GROUPS_CACHE_FILE.tmp")
            groupsTmp.bufferedWriter().use { writer ->
                val jsonWriter = JsonWriter(writer)
                gson.toJson(groups, groupListType, jsonWriter)
                jsonWriter.flush()
            }
            if (groupsTmp.exists()) {
                if (groupsFile.exists()) groupsFile.delete()
                groupsTmp.renameTo(groupsFile)
            }

            LiveTvStorage.saveLastRefreshTime(System.currentTimeMillis())
            LiveTvStorage.saveCacheConfigSignature(signature)
            Log.d(TAG, "Cached ${channels.size} channels and ${groups.size} groups successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save channels cache", e)
        }
    }

    /**
     * Load channels from memory cache or disk cache via streaming JsonReader.
     */
    fun loadChannels(context: Context?): List<LiveTvChannel> {
        val inMemory = memoryCachedChannels
        if (inMemory != null && inMemory.isNotEmpty()) {
            return inMemory
        }

        val dir = context?.applicationContext?.filesDir ?: return emptyList()
        val cacheFile = File(dir, CHANNELS_CACHE_FILE)
        if (!cacheFile.exists() || cacheFile.length() == 0L) return emptyList()

        return runCatching {
            cacheFile.bufferedReader().use { reader ->
                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                val channels: List<LiveTvChannel>? = gson.fromJson(jsonReader, channelListType)
                (channels ?: emptyList()).also {
                    memoryCachedChannels = it
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to load channels cache", e)
        }.getOrDefault(emptyList())
    }

    /**
     * Fast retrieval of cached group names without needing to parse all channels.
     */
    fun loadGroups(context: Context?): List<String> {
        val inMemory = memoryCachedGroups
        if (inMemory != null && inMemory.isNotEmpty()) {
            return inMemory
        }

        val dir = context?.applicationContext?.filesDir ?: return emptyList()
        val groupsFile = File(dir, GROUPS_CACHE_FILE)
        if (!groupsFile.exists() || groupsFile.length() == 0L) return emptyList()

        return runCatching {
            groupsFile.bufferedReader().use { reader ->
                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                val groups: List<String>? = gson.fromJson(jsonReader, groupListType)
                (groups ?: emptyList()).also {
                    memoryCachedGroups = it
                }
            }
        }.getOrDefault(emptyList())
    }
}
