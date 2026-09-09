package com.nuvio.tv.ui.screens.player

import android.util.Log
import com.nuvio.tv.R
import com.nuvio.tv.core.torrent.TorrentState
import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "PlayerTorrent"

/**
 * Starts a torrent stream via TorrServer. Returns the HTTP stream URL for ExoPlayer.
 * TorrServer handles all piece management, buffering, and seeking internally.
 */
internal suspend fun PlayerRuntimeController.startTorrentStream(
    infoHash: String,
    fileIdx: Int?,
    filename: String? = null,
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
    return torrentService.startStream(infoHash, fileIdx, effectiveFilename, trackers)
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
        while (isActive) {
            try {
                val stats = torrServerRemoteApi.getTorrentDetails(hash, serverUrlOverride = serverUrl)
                if (stats != null) {
                    val speed = formatSpeed(context, stats.downloadSpeed)
                    val statsHidden = _uiState.value.hideTorrentStats
                    val preloadProgress = stats.preloadProgress

                    // Strictly show preload % (0% -> 100%), NEVER % of the entire torrent/movie
                    val percentStr = when {
                        preloadProgress > 0f -> "${(preloadProgress * 100).toInt()}%"
                        stats.stat == 2 -> "0%"
                        stats.stat == 1 -> stats.statString
                        else -> null
                    }

                    // Only show preload % and download speed (no peers, seeds, upload, or MBs)
                    val statusParts = listOfNotNull(
                        percentStr,
                        speed.takeIf { stats.downloadSpeed > 0 } ?: speed
                    )
                    val message = if (statsHidden) null else statusParts.joinToString(" · ")

                    if (!hasRenderedFirstFrame) {
                        recordLoadingDiagnosticEvent(
                            phase = "torrent_preloading",
                            message = message,
                            progress = preloadProgress
                        )
                        _uiState.update {
                            it.copy(
                                isTorrentStream = true,
                                showLoadingOverlay = true,
                                showTorrentStats = false,
                                loadingMessage = message,
                                loadingProgress = preloadProgress,
                                torrentDownloadSpeed = stats.downloadSpeed,
                                torrentUploadSpeed = 0L,
                                torrentPeers = 0,
                                torrentSeeds = 0,
                                torrentBufferProgress = preloadProgress,
                                torrentTotalProgress = preloadProgress,
                                torrentBufferingMessage = null
                            )
                        }
                    } else {
                        // When video is playing: turn off loading stats!
                        // Only show download speed if player is rebuffering
                        val isBuffering = _uiState.value.isBuffering
                        val rebufferingMessage = if (isBuffering && !statsHidden) speed else null

                        // Avoid redundant state updates during smooth playback
                        if (isBuffering || _uiState.value.torrentBufferingMessage != null) {
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
                                    torrentBufferingProgress = 0f
                                )
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
                750L
            } else if (_uiState.value.isBuffering) {
                1000L
            } else {
                3000L
            }
            kotlinx.coroutines.delay(pollDelay)
        }
    }
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

                    if (!hasRenderedFirstFrame) {
                        val progress = torrentState.preloadProgress
                        val percentStr = when {
                            progress > 0f -> "${(progress * 100).toInt()}%"
                            torrentState.stat == 2 -> "0%"
                            torrentState.stat == 1 -> torrentState.statString
                            else -> null
                        }
                        val statusParts = listOfNotNull(
                            percentStr,
                            speed.takeIf { torrentState.downloadSpeed > 0 } ?: speed
                        )
                        val message = if (statsHidden) null else statusParts.joinToString(" · ")
                        recordLoadingDiagnosticEvent(
                            phase = "torrent_preloading",
                            message = message,
                            progress = progress
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
                                torrentBufferProgress = progress,
                                torrentTotalProgress = progress,
                                torrentBufferingMessage = null
                            )
                        }
                    } else {
                        val isBuffering = _uiState.value.isBuffering
                        val message = if (isBuffering && !statsHidden) speed else null
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
                                torrentBufferingMessage = message
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

private fun formatSpeed(context: android.content.Context, bytesPerSec: Long): String {
    val bitsPerSec = bytesPerSec * 8.0
    return when {
        bitsPerSec >= 1_000_000.0 -> context.getString(com.nuvio.tv.R.string.unit_speed_mb_s, String.format(java.util.Locale.US, "%.1f", bitsPerSec / 1_000_000.0))
        bitsPerSec >= 1_000.0 -> context.getString(com.nuvio.tv.R.string.unit_speed_kb_s, String.format(java.util.Locale.US, "%.0f", bitsPerSec / 1_000.0))
        else -> context.getString(com.nuvio.tv.R.string.unit_speed_b_s, bitsPerSec.toLong())
    }
}

private fun formatMB(context: android.content.Context, bytes: Long): String =
    context.getString(com.nuvio.tv.R.string.unit_size_mb, String.format("%.1f", bytes / 1_048_576.0))
