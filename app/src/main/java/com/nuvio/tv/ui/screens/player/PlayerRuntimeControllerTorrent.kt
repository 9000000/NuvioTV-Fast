package com.nuvio.tv.ui.screens.player

import android.util.Log
import com.nuvio.tv.R
import com.nuvio.tv.core.torrent.TorrentState
import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "PlayerTorrent"

/**
 * Starts a torrent stream via TorrServer. Returns the HTTP stream URL for ExoPlayer.
 * TorrServer handles all piece management, buffering, and seeking internally.
 */
internal suspend fun PlayerRuntimeController.startTorrentStream(
    infoHash: String,
    fileIdx: Int?,
    filename: String? = null,
    title: String? = null,
    poster: String? = null,
    trackers: List<String> = emptyList()
): String {
    isTorrentStream = true
    currentInfoHash = infoHash
    currentFileIdx = fileIdx

    setLoadingStatus(
        phase = "torrent_starting_engine",
        message = context.getString(com.nuvio.tv.R.string.player_torrent_starting_engine),
        showOverlay = true
    )
    _uiState.update {
        it.copy(
            showLoadingOverlay = true,
            loadingMessage = context.getString(com.nuvio.tv.R.string.player_torrent_starting_engine),
            loadingProgress = null,
            isTorrentStream = true
        )
    }

    val effectiveFilename = filename ?: currentFilename
    return torrentService.startStream(
        infoHash = infoHash,
        fileIdx = fileIdx,
        filename = effectiveFilename,
        title = title,
        poster = poster,
        trackers = trackers
    )
}

/**
 * Stops the current torrent stream and cleans up state.
 */
internal fun PlayerRuntimeController.stopTorrentStream() {
    torrentStreamJob?.cancel()
    torrentStreamJob = null
    torrentStateObserverJob?.cancel()
    torrentStateObserverJob = null

    if (isTorrentStream) {
        torrentService.stopStream()
        currentInfoHash?.let { hash ->
            scope.launch(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
                torrServerRemoteApi.dropTorrent(hash)
            }
        }
    }

    isTorrentStream = false
    currentInfoHash = null
    currentFileIdx = null
}

/**
 * Starts remote TorrServer stats polling.
 * Reuses the existing TorrentOverlay and loading progress logic.
 */
internal fun PlayerRuntimeController.startRemoteTorrServerStatsPolling(
    hash: String,
    streamUrl: String
) {
    torrentStateObserverJob?.cancel()
    isTorrentStream = true
    currentInfoHash = hash

    val serverUrl = runCatching {
        val uri = android.net.Uri.parse(streamUrl)
        "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
    }.getOrNull()

    _uiState.update {
        it.copy(
            isTorrentStream = true,
            showLoadingOverlay = true,
            showTorrentStats = false,
            hideTorrentStats = false
        )
    }

    torrentStateObserverJob = scope.launch {
        var lastLoadingMessage: String? = null
        var lastLoadingProgress: Float? = -1f
        var lastDownloadSpeed = Long.MIN_VALUE
        var lastLoadedSize = Long.MIN_VALUE
        var lastBufferingMessage: String? = null
        val hasPreloadParam = runCatching {
            val uri = android.net.Uri.parse(streamUrl)
            uri.queryParameterNames.any { it.equals("preload", ignoreCase = true) }
        }.getOrDefault(false)

        while (isActive) {
            try {
                val stats = torrServerRemoteApi.getTorrentDetails(hash, serverUrlOverride = serverUrl)
                if (stats != null) {
                    val statsHidden = _uiState.value.hideTorrentStats
                    val speed = formatSpeed(context, stats.downloadSpeed)
                    val (message, progress) = formatTorrentLoadingDisplay(
                        context = context,
                        isPreloadActive = hasPreloadParam,
                        isPreloadReady = stats.isPreloadReady,
                        preloadProgress = stats.preloadProgress,
                        stat = stats.stat,
                        statString = stats.statString,
                        downloadSpeed = stats.downloadSpeed,
                        loadedSize = stats.loadedSize,
                        statsHidden = statsHidden
                    )

                    if (!hasRenderedFirstFrame) {
                        recordLoadingDiagnosticEvent(
                            phase = if (progress != null) "torrent_preloading" else "torrent_buffering",
                            message = message,
                            progress = progress ?: 0f
                        )
                        val changed = message != lastLoadingMessage ||
                            progress != lastLoadingProgress ||
                            stats.downloadSpeed != lastDownloadSpeed ||
                            stats.loadedSize != lastLoadedSize
                        if (changed) {
                            _uiState.update {
                                it.copy(
                                    isTorrentStream = true,
                                    showLoadingOverlay = true,
                                    showTorrentStats = false,
                                    loadingMessage = message,
                                    loadingProgress = progress,
                                    torrentDownloadSpeed = stats.downloadSpeed,
                                    torrentUploadSpeed = 0L,
                                    torrentPeers = 0,
                                    torrentSeeds = 0,
                                    torrentBufferProgress = progress ?: 0f,
                                    torrentTotalProgress = progress ?: 0f,
                                    torrentBufferingMessage = null,
                                    streamDownloadSpeed = stats.downloadSpeed,
                                    streamLoadedBytes = stats.loadedSize
                                )
                            }
                            lastLoadingMessage = message
                            lastLoadingProgress = progress
                            lastDownloadSpeed = stats.downloadSpeed
                            lastLoadedSize = stats.loadedSize
                        }
                    } else {
                        // When video is playing: turn off loading stats!
                        // Only show download speed if player is rebuffering
                        val isBuffering = _uiState.value.isBuffering
                        val rebufferingMessage = if (isBuffering && !statsHidden) speed else null

                        // Avoid redundant state updates during smooth playback
                        if (isBuffering || _uiState.value.torrentBufferingMessage != null) {
                            val changed = rebufferingMessage != lastBufferingMessage ||
                                stats.downloadSpeed != lastDownloadSpeed
                            if (changed) {
                                _uiState.update {
                                    it.copy(
                                        isTorrentStream = true,
                                        showTorrentStats = false,
                                        loadingProgress = null,
                                        torrentDownloadSpeed = stats.downloadSpeed,
                                        torrentUploadSpeed = 0L,
                                        torrentPeers = 0,
                                        torrentSeeds = 0,
                                        torrentBufferProgress = 0f,
                                        torrentTotalProgress = 0f,
                                        torrentBufferingMessage = rebufferingMessage,
                                        torrentBufferingProgress = 0f,
                                        bufferingMessage = rebufferingMessage,
                                        streamDownloadSpeed = stats.downloadSpeed,
                                        streamLoadedBytes = stats.loadedSize
                                    )
                                }
                                lastBufferingMessage = rebufferingMessage
                                lastDownloadSpeed = stats.downloadSpeed
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Remote TorrServer stats polling error: ${e.message}")
            }
            val pollDelay = if (!hasRenderedFirstFrame) {
                1000L
            } else if (_uiState.value.isBuffering) {
                1000L
            } else {
                3000L
            }
            kotlinx.coroutines.delay(pollDelay)
        }
    }
}

internal suspend fun PlayerRuntimeController.awaitRemoteTorrServerPreload(
    hash: String,
    streamUrl: String
): String {
    val uri = runCatching { android.net.Uri.parse(streamUrl) }.getOrNull()
        ?: return streamUrl
    if (!uri.queryParameterNames.any { it.equals("preload", ignoreCase = true) }) {
        return streamUrl
    }

    val serverUrl = "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
    val preloadCall = torrServerRemoteApi.newStreamCall(streamUrl)
    val preloadJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            preloadCall.execute().use { response ->
                if (response.isSuccessful) {
                    val byteStream = response.body?.byteStream()
                    val buffer = ByteArray(16384)
                    while (isActive) {
                        val read = byteStream?.read(buffer) ?: -1
                        if (read == -1) break
                    }
                } else {
                    Log.w(TAG, "Remote TorrServer preload request failed: ${response.code}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Remote TorrServer preload request ended: ${e.message}")
        }
    }

    return try {
        val playbackUrl = withTimeoutOrNull(60_000L) {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val stats = torrServerRemoteApi.getTorrentDetails(hash, serverUrlOverride = serverUrl)
                if (stats?.isPreloadReady == true) {
                    Log.d(TAG, "Remote TorrServer preload is active; handing stream to player")
                    return@withTimeoutOrNull uri.toTorrServerPlaybackUrl()
                }
                delay(500L)
            }
            null
        }

        if (playbackUrl == null) {
            Log.w(TAG, "Remote TorrServer preload status timeout; handing stream to player anyway")
        }
        playbackUrl ?: uri.toTorrServerPlaybackUrl()
    } finally {
        preloadCall.cancel()
        preloadJob.cancel()
    }
}

private fun android.net.Uri.toTorrServerPlaybackUrl(): String {
    val builder = buildUpon().clearQuery()
    var hasPlay = false
    queryParameterNames
        .filterNot { it.equals("preload", ignoreCase = true) }
        .forEach { name ->
            if (name.equals("play", ignoreCase = true)) {
                hasPlay = true
            }
            getQueryParameters(name).forEach { value ->
                if (value.isNullOrEmpty()) {
                    builder.appendQueryParameter(name, "")
                } else {
                    builder.appendQueryParameter(name, value)
                }
            }
        }
    if (!hasPlay) {
        builder.appendQueryParameter("play", "")
    }
    return builder.build().toString()
        .replace("play=", "play")
        .replace("save=", "save")
        .replace("&&", "&")
        .trimEnd('&', '?')
}

/**
 * Collects TorrentService state and maps it to PlayerUiState fields.
 */
internal fun PlayerRuntimeController.observeTorrentState() {
    torrentStateObserverJob?.cancel()
    torrentStateObserverJob = scope.launch {
        torrentService.state.collectLatest { torrentState ->
            when (torrentState) {
                is TorrentState.Idle -> { /* No-op */ }

                is TorrentState.Connecting -> {
                    if (!hasRenderedFirstFrame) {
                        recordLoadingDiagnosticEvent(
                            phase = "torrent_connecting_peers",
                            message = context.getString(com.nuvio.tv.R.string.player_torrent_connecting_peers)
                        )
                        _uiState.update {
                            it.copy(
                                showLoadingOverlay = true,
                                loadingMessage = context.getString(com.nuvio.tv.R.string.player_torrent_connecting_peers),
                                loadingProgress = null,
                                torrentBufferingMessage = null
                            )
                        }
                    }
                }

                is TorrentState.Streaming -> {
                    val speed = formatSpeed(context, torrentState.downloadSpeed)
                    val statsHidden = _uiState.value.hideTorrentStats
                    val isPreloadActive = torrServerConfigData.preload && (torrentState.preloadSize > 0 || torrentState.stat == 2)
                    val (message, progress) = formatTorrentLoadingDisplay(
                        context = context,
                        isPreloadActive = isPreloadActive,
                        isPreloadReady = torrentState.isPreloadReady,
                        preloadProgress = torrentState.preloadProgress,
                        stat = torrentState.stat,
                        statString = torrentState.statString,
                        downloadSpeed = torrentState.downloadSpeed,
                        loadedSize = torrentState.loadedSize,
                        statsHidden = statsHidden
                    )

                    if (!hasRenderedFirstFrame) {
                        recordLoadingDiagnosticEvent(
                            phase = if (progress != null) "torrent_preloading" else "torrent_buffering",
                            message = message,
                            progress = progress ?: 0f
                        )
                        _uiState.update {
                            it.copy(
                                showLoadingOverlay = true,
                                showTorrentStats = false,
                                loadingMessage = message,
                                loadingProgress = progress,
                                torrentDownloadSpeed = torrentState.downloadSpeed,
                                torrentUploadSpeed = 0L,
                                torrentPeers = 0,
                                torrentBufferProgress = progress ?: 0f,
                                torrentTotalProgress = progress ?: 0f,
                                torrentBufferingMessage = null,
                                streamDownloadSpeed = torrentState.downloadSpeed,
                                streamLoadedBytes = torrentState.loadedSize
                            )
                        }
                    } else {
                        val isBuffering = _uiState.value.isBuffering
                        val rebufferingMessage = if (isBuffering && !statsHidden) speed else null
                        _uiState.update {
                            it.copy(
                                showTorrentStats = false,
                                loadingProgress = null,
                                torrentDownloadSpeed = torrentState.downloadSpeed,
                                torrentUploadSpeed = 0L,
                                torrentPeers = 0,
                                torrentSeeds = 0,
                                torrentBufferProgress = 0f,
                                torrentTotalProgress = 0f,
                                torrentBufferingMessage = rebufferingMessage,
                                bufferingMessage = rebufferingMessage,
                                streamDownloadSpeed = torrentState.downloadSpeed,
                                streamLoadedBytes = torrentState.loadedSize
                            )
                        }
                    }
                }

                is TorrentState.Error -> {
                    Log.e(TAG, "Torrent error: ${torrentState.message}")
                    _uiState.update {
                        it.copy(
                            error = context.getString(com.nuvio.tv.R.string.player_error_torrent, torrentState.message),
                            showLoadingOverlay = false,
                            torrentBufferingMessage = null
                        )
                    }
                }
            }
        }
    }
}

/**
 * Launches a torrent stream for source/episode stream switching.
 */
internal fun PlayerRuntimeController.launchTorrentSourceStream(
    stream: Stream,
    infoHash: String,
    loadSavedProgress: Boolean
) {
    torrentStreamJob?.cancel()
    torrentStreamJob = scope.launch {
        try {
            observeTorrentState()

            currentTorrentSources = stream.sources
            val trackers = stream.sources
                ?.filter { it.startsWith("tracker:") }
                ?.map { it.removePrefix("tracker:") }
                ?: emptyList()
            val localUrl = startTorrentStream(
                infoHash = infoHash,
                fileIdx = stream.getEffectiveFileIdx(),
                filename = stream.behaviorHints?.filename,
                title = contentName ?: title,
                poster = poster,
                trackers = trackers
            )

            currentStreamUrl = localUrl
            currentHeaders = emptyMap()
            currentStreamMimeType = null

            preparePlaybackBeforeStart(
                url = localUrl,
                headers = emptyMap(),
                loadSavedProgress = loadSavedProgress
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start torrent stream", e)
            _uiState.update {
                it.copy(
                    error = context.getString(
                        R.string.player_error_failed_start_torrent,
                        e.message ?: context.getString(R.string.error_unknown)
                    ),
                    showLoadingOverlay = false,
                    loadingProgress = null
                )
            }
        }
    }
}

private fun formatTorrentLoadingDisplay(
    context: android.content.Context,
    isPreloadActive: Boolean,
    isPreloadReady: Boolean,
    preloadProgress: Float,
    stat: Int,
    statString: String?,
    downloadSpeed: Long,
    loadedSize: Long,
    statsHidden: Boolean
): Pair<String?, Float?> {
    if (statsHidden) return Pair(null, null)
    val speed = formatSpeed(context, downloadSpeed)

    // Preload phase: only when preload is configured and not yet ready
    if (isPreloadActive && !isPreloadReady && (preloadProgress < 1f || stat == 2)) {
        val percentStr = when {
            preloadProgress > 0f -> "${(preloadProgress * 100).toInt()}%"
            stat == 2 -> "0%"
            stat == 1 -> statString
            else -> null
        }
        val statusParts = listOfNotNull(
            percentStr,
            speed.takeIf { downloadSpeed > 0 } ?: speed
        )
        return Pair(statusParts.joinToString(" · "), preloadProgress)
    }

    // Player buffering phase (preload completed or disabled):
    // Display MB loaded into buffer + speed. Progress bar is hidden (null).
    val mbStr = if (loadedSize > 0) formatMB(context, loadedSize) else null
    val statusParts = listOfNotNull(
        mbStr,
        speed.takeIf { downloadSpeed > 0 } ?: speed
    )
    val message = if (statusParts.isNotEmpty()) {
        statusParts.joinToString(" · ")
    } else {
        context.getString(R.string.player_loading_buffering)
    }
    return Pair(message, null)
}

private fun formatSpeed(context: android.content.Context, bytesPerSec: Long): String {
    val bitsPerSec = bytesPerSec * 8.0
    return when {
        bitsPerSec >= 1_000_000.0 -> context.getString(com.nuvio.tv.R.string.unit_speed_mb_s, String.format(java.util.Locale.US, "%.1f", bitsPerSec / 1_000_000.0))
        bitsPerSec >= 1_000.0 -> context.getString(com.nuvio.tv.R.string.unit_speed_kb_s, String.format(java.util.Locale.US, "%.0f", bitsPerSec / 1_000.0))
        else -> context.getString(com.nuvio.tv.R.string.unit_speed_b_s, bitsPerSec.toLong())
    }
}

private fun formatMB(context: android.content.Context, bytes: Long): String =
    context.getString(com.nuvio.tv.R.string.unit_size_mb, String.format(java.util.Locale.US, "%.1f", bytes / 1_048_576.0))

