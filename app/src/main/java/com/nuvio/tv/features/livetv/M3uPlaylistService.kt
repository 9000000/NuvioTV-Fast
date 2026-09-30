package com.nuvio.tv.features.livetv

import android.util.Log
import com.nuvio.tv.core.network.DynamicHostFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Service responsible for loading M3U playlists from URLs and local content.
 * Uses OkHttp streaming to avoid loading the entire M3U payload into RAM.
 */
internal object M3uPlaylistService {
    private const val TAG = "M3uPlaylistService"

    /**
     * Load channels from a single playlist, using OkHttp streaming for URL sources.
     */
    suspend fun loadPlaylist(
        httpClient: OkHttpClient,
        playlist: LiveTvPlaylist,
    ): List<LiveTvChannel> = withContext(Dispatchers.IO) {
        when (playlist.type) {
            LiveTvPlaylistType.Url -> loadFromUrl(httpClient, playlist)
            LiveTvPlaylistType.LocalFile -> loadFromLocalContent(playlist)
        }
    }

    /**
     * Load channels from multiple playlists in parallel.
     * Each playlist is independently isolated — failure in one does not affect others.
     * Returns a pair: (loadedChannels, failedPlaylistNames).
     */
    suspend fun loadPlaylistsParallel(
        httpClient: OkHttpClient,
        playlists: List<LiveTvPlaylist>,
    ): Pair<List<LiveTvChannel>, List<String>> = coroutineScope {
        val results = playlists.map { playlist ->
            async {
                runCatching {
                    loadPlaylist(httpClient, playlist)
                }.fold(
                    onSuccess = { channels -> Result.success(playlist.name to channels) },
                    onFailure = { error ->
                        Log.w(TAG, "Failed to load playlist ${playlist.name}", error)
                        Result.failure(Exception(playlist.name, error))
                    }
                )
            }
        }.awaitAll()

        val loadedChannels = mutableListOf<LiveTvChannel>()
        val failedNames = mutableListOf<String>()

        results.forEach { result ->
            result.fold(
                onSuccess = { (_, channels) -> loadedChannels += channels },
                onFailure = { error -> failedNames += error.message ?: "Unknown" }
            )
        }

        loadedChannels to failedNames
    }

    /**
     * Stream-based URL loading: reads the response body as a stream,
     * parsing M3U line-by-line without holding the entire payload in RAM.
     */
    private fun loadFromUrl(httpClient: OkHttpClient, playlist: LiveTvPlaylist): List<LiveTvChannel> {
        DynamicHostFallback.registerWorkingHost(playlist.source)

        val request = Request.Builder()
            .url(playlist.source)
            .header("Accept", "*/*")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw java.io.IOException("HTTP ${response.code} ${response.message} for ${playlist.source}")
        }
        val body = response.body
        return body.byteStream().bufferedReader().use { reader ->
            M3uStreamParser.parse(reader, playlist)
        }
    }

    /**
     * Parse M3U from local content (already a String in memory).
     */
    private fun loadFromLocalContent(playlist: LiveTvPlaylist): List<LiveTvChannel> {
        return M3uStreamParser.parseFromString(playlist.source, playlist)
    }
}
