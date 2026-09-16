package com.nuvio.tv.core.torrent

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "webm", "ts", "m4v", "mov", "wmv", "flv")

internal fun torrServerDisplayTitle(title: String?): String? =
    title?.trim()?.takeIf { it.isNotBlank() }?.let { "[NuvioF] $it" }

@Singleton
class TorrentService @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val remoteApi: TorrServerRemoteApi,
    private val addonConfig: TorrServerAddonConfig
) {
    companion object {
        private const val TAG = "TorrentService"
        private val DEFAULT_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.torrent.eu.org:451/announce"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<TorrentState>(TorrentState.Idle)
    val state: StateFlow<TorrentState> = _state.asStateFlow()

    private var statsJob: Job? = null
    private var preloadJob: Job? = null
    private var currentHash: String? = null

    /**
     * Starts streaming a torrent via the remote TorrServer instance. Returns the HTTP URL for ExoPlayer.
     */
    suspend fun startStream(
        infoHash: String,
        fileIdx: Int?,
        filename: String? = null,
        title: String? = null,
        poster: String? = null,
        trackers: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        stopStream()
        _state.value = TorrentState.Connecting

        val config = addonConfig.config.first()
        val serverUrl = config.serverUrl.trim().trimEnd('/')
        if (serverUrl.isBlank()) {
            val errorMsg = appContext.getString(com.nuvio.tv.R.string.torrserver_error_not_configured)
            _state.value = TorrentState.Error(errorMsg)
            throw TorrentException(errorMsg)
        }

        val magnetLink = buildMagnetUri(infoHash, trackers)
        Log.d(TAG, "Starting remote TorrServer stream on $serverUrl: $magnetLink")

        // Add torrent to remote TorrServer
        val hash = remoteApi.addTorrent(
            magnetLink = magnetLink,
            title = torrServerDisplayTitle(title),
            poster = poster,
            serverUrlOverride = serverUrl
        ) ?: throw TorrentException(appContext.getString(com.nuvio.tv.R.string.torrent_error_add_failed))
        currentHash = hash

        // Resolve file index from metadata
        val resolvedIdx = resolveFileIndex(hash, fileIdx, filename, serverUrl)

        val isPreload = config.preload
        val streamUrl = remoteApi.buildStreamUrl(
            serverUrl = serverUrl,
            magnetLink = magnetLink,
            fileIdx = resolvedIdx,
            preload = false,
            save = config.saveToDb,
            gst = config.gst,
            hash = hash
        )
        Log.d(TAG, "Playback stream URL: $streamUrl")

        // Start stats polling
        startStatsPolling(hash, serverUrl)

        _state.value = TorrentState.Streaming(
            localUrl = streamUrl,
            downloadSpeed = 0,
            uploadSpeed = 0,
            peers = 0,
            seeds = 0,
            bufferProgress = 0f,
            totalProgress = 0f
        )

        if (isPreload) {
            val preloadUrl = remoteApi.buildStreamUrl(
                serverUrl = serverUrl,
                magnetLink = magnetLink,
                fileIdx = resolvedIdx,
                preload = true,
                save = config.saveToDb,
                gst = config.gst,
                hash = hash
            )
            val call = remoteApi.newStreamCall(preloadUrl)
            preloadJob = scope.launch {
                try {
                    call.execute().use { response ->
                        if (response.isSuccessful) {
                            val byteStream = response.body?.byteStream()
                            val buffer = ByteArray(16384)
                            while (isActive) {
                                val read = byteStream?.read(buffer) ?: -1
                                if (read == -1) break
                            }
                        } else {
                            Log.w(TAG, "TorrServer preload request failed: ${response.code}")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "TorrServer preload request ended: ${e.message}")
                }
            }
            try {
                awaitPreloadReady(hash, serverUrl)
            } finally {
                call.cancel()
                preloadJob?.cancel()
                preloadJob = null
            }
        }

        streamUrl
    }

    fun stopStream() {
        statsJob?.cancel()
        statsJob = null
        preloadJob?.cancel()
        preloadJob = null

        currentHash?.let { hash ->
            scope.launch(Dispatchers.IO) {
                try {
                    remoteApi.dropTorrent(hash)
                } catch (e: Exception) {
                    Log.w(TAG, "Error dropping torrent: $hash", e)
                }
            }
        }
        currentHash = null
        _state.value = TorrentState.Idle
    }

    private suspend fun awaitPreloadReady(hash: String, serverUrl: String) {
        val deadline = System.currentTimeMillis() + 60_000L
        while (System.currentTimeMillis() < deadline) {
            val stats = remoteApi.getTorrentDetails(hash, serverUrlOverride = serverUrl)
            if (stats != null) {
                val currentState = _state.value
                if (currentState is TorrentState.Streaming) {
                    _state.value = currentState.copy(
                        downloadSpeed = stats.downloadSpeed,
                        uploadSpeed = stats.uploadSpeed,
                        peers = stats.activePeers,
                        seeds = stats.connectedSeeders,
                        preloadedBytes = stats.preloadedBytes,
                        preloadSize = stats.preloadSize,
                        loadedSize = stats.loadedSize,
                        stat = stats.stat,
                        statString = stats.statString,
                        bufferProgress = stats.preloadProgress,
                        totalProgress = stats.preloadProgress
                    )
                }
                if (stats.isPreloadReady) {
                    Log.d(TAG, "TorrServer preload is active; handing stream to player")
                    return
                }
            }
            delay(250L)
        }
        Log.w(TAG, "TorrServer preload status timeout; handing stream to player anyway")
    }

    fun shutdown() {
        stopStream()
    }

    private fun buildMagnetUri(infoHash: String, extraTrackers: List<String>): String {
        val trackers = (DEFAULT_TRACKERS + extraTrackers).distinct()
        val trackerParams = trackers.joinToString("") { "&tr=$it" }
        return "magnet:?xt=urn:btih:$infoHash$trackerParams"
    }

    private suspend fun resolveFileIndex(
        hash: String,
        requestedIdx: Int?,
        filename: String?,
        serverUrl: String
    ): Int {
        val deadline = System.currentTimeMillis() + 15_000L
        var files: List<TorrServerRemoteFile> = emptyList()

        while (System.currentTimeMillis() < deadline) {
            files = remoteApi.getTorrentDetails(hash, serverUrlOverride = serverUrl)?.files ?: emptyList()
            if (files.isNotEmpty()) break
            Log.d(TAG, "Waiting for torrent metadata...")
            delay(1_000L)
        }

        if (files.isEmpty()) {
            Log.w(TAG, "No files after metadata timeout, guessing index ${requestedIdx?.plus(1) ?: 1}")
            return requestedIdx?.plus(1) ?: 1
        }

        Log.d(TAG, "Torrent has ${files.size} files")

        // Strategy 1: Match by filename (most reliable for season packs)
        if (!filename.isNullOrBlank()) {
            val name = filename.trim()
            val exact = files.firstOrNull { f ->
                f.path.substringAfterLast('/').equals(name, ignoreCase = true)
            }
            if (exact != null) {
                Log.d(TAG, "File resolved by exact filename match: ${exact.path} -> id=${exact.id}")
                return exact.id
            }
            val contains = files.firstOrNull { f ->
                f.path.contains(name, ignoreCase = true)
            }
            if (contains != null) {
                Log.d(TAG, "File resolved by filename contains match: ${contains.path} -> id=${contains.id}")
                return contains.id
            }
        }

        // Strategy 2: Match by ID offset (requestedIdx + 1)
        if (requestedIdx != null) {
            val tsIdx = requestedIdx + 1
            if (files.any { it.id == tsIdx }) {
                Log.d(TAG, "File resolved by ID offset: id=$tsIdx")
                return tsIdx
            }
        }

        // Strategy 3: Positional index (handles TorrServer alphabetical sort mismatch)
        if (requestedIdx != null && requestedIdx in files.indices) {
            val positionalFile = files[requestedIdx]
            Log.d(TAG, "File resolved by positional index: [$requestedIdx] -> ${positionalFile.path} (id=${positionalFile.id})")
            return positionalFile.id
        }

        // Strategy 4: Fallback to largest video file
        val videoFile = files
            .filter { f ->
                val ext = f.path.substringAfterLast('.', "").lowercase()
                ext in VIDEO_EXTENSIONS
            }
            .maxByOrNull { it.length }

        val result = videoFile?.id ?: files.maxByOrNull { it.length }?.id ?: 1
        Log.d(TAG, "File resolved by largest video fallback: id=$result")
        return result
    }

    private fun startStatsPolling(hash: String, serverUrl: String) {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                try {
                    val stats = remoteApi.getTorrentDetails(hash, serverUrlOverride = serverUrl)
                    val currentState = _state.value
                    if (stats != null && currentState is TorrentState.Streaming) {
                        _state.value = currentState.copy(
                            downloadSpeed = stats.downloadSpeed,
                            uploadSpeed = stats.uploadSpeed,
                            peers = stats.activePeers,
                            seeds = stats.connectedSeeders,
                            preloadedBytes = stats.preloadedBytes,
                            preloadSize = stats.preloadSize,
                            loadedSize = stats.loadedSize,
                            stat = stats.stat,
                            statString = stats.statString,
                            bufferProgress = stats.preloadProgress,
                            totalProgress = stats.preloadProgress
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Stats polling error", e)
                }
                delay(1000)
            }
        }
    }
}
