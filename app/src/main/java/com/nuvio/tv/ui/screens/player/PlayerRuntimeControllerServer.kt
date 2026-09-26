package com.nuvio.tv.ui.screens.player

import android.widget.Toast
import com.nuvio.tv.R
import com.nuvio.tv.data.mediaserver.ServerPlaybackSession
import com.nuvio.tv.data.mediaserver.serverPlaybackMessageRes
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.ProxyHeaders
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.domain.model.enabledAddons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun PlayerRuntimeController.reportServerPlayback() {
    val url = currentStreamUrl
    if (url != reportedServerUrl) {
        serverPlayback.stop(reportedServerUrl)
        reportedServerUrl = url.takeIf(serverPlayback::isServerSource)
        refreshServerAudioTracks()
    }
    val state = _uiState.value
    serverPlayback.onPlaybackSnapshot(
        url = reportedServerUrl,
        positionMs = _playbackTimeline.value.currentPosition,
        isPlaying = state.isPlaying,
        isLoading = state.isBuffering,
        isEnded = state.playbackEnded
    )
}

internal fun PlayerRuntimeController.stopServerPlayback() {
    serverPlayback.stop(reportedServerUrl ?: currentStreamUrl)
    reportedServerUrl = null
}

internal val PlayerRuntimeController.isServerStream: Boolean
    get() = serverPlayback.isServerSource(currentStreamUrl)

internal fun PlayerRuntimeController.serverImdbId(contentId: String): String? =
    metaRepository.getCachedMeta(contentType ?: "movie", contentId)?.imdbId?.takeIf { it.startsWith("tt") }

internal suspend fun PlayerRuntimeController.streamAddonsFor(videoId: String): List<Addon> =
    if (serverStreams.isNativeRequest(videoId)) emptyList() else addonRepository.getInstalledAddons().first().enabledAddons()

internal fun PlayerRuntimeController.serverSourceNames(type: String, videoId: String): List<String> =
    serverStreams.sources(type, videoId, season = null, episode = null).map { it.name }

internal suspend fun PlayerRuntimeController.prepareServerStream(stream: Stream): Stream? {
    val target = stream.serverTarget ?: return stream
    val session = try {
        serverPlayback.prepare(target)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Toast.makeText(context, context.getString(error.serverPlaybackMessageRes()), Toast.LENGTH_SHORT).show()
        return null
    }
    val hints = stream.behaviorHints ?: StreamBehaviorHints(null, null, null, null)
    return stream.copy(
        url = session.url,
        subtitles = session.subtitles + stream.subtitles,
        behaviorHints = hints.copy(proxyHeaders = session.headers.takeIf { it.isNotEmpty() }?.let { ProxyHeaders(request = it, response = null) })
    )
}

internal fun PlayerRuntimeController.tryServerFallback(): Boolean {
    val failedUrl = currentStreamUrl
    if (!serverPlayback.canFallback(failedUrl)) return false
    val positionMs = _playbackTimeline.value.currentPosition
    errorRetryJob?.cancel()
    errorRetryJob = scope.launch {
        showRecoveryOverlay()
        val session = serverPlayback.fallback(failedUrl)
        if (session == null) {
            _uiState.update {
                it.copy(
                    error = context.getString(R.string.player_error_play_stream_failed),
                    isBuffering = false,
                    showLoadingOverlay = false,
                    showPauseOverlay = false
                )
            }
            return@launch
        }
        restartServerSession(session, positionMs)
    }
    return true
}

internal fun PlayerRuntimeController.switchServerAudio(index: Int) {
    if (_uiState.value.serverAudioTracks.any { it.isSelected && it.index == index }) return
    val url = currentStreamUrl
    val positionMs = _playbackTimeline.value.currentPosition
    scope.launch {
        val session = serverPlayback.switchAudio(url, index)
        if (session == null) {
            Toast.makeText(context, context.getString(R.string.servers_audio_switch_failed), Toast.LENGTH_SHORT).show()
            return@launch
        }
        if (currentStreamUrl != url) {
            serverPlayback.stop(session.url)
            return@launch
        }
        restartServerSession(session, positionMs)
    }
}

private fun PlayerRuntimeController.restartServerSession(session: ServerPlaybackSession, positionMs: Long) {
    currentStreamUrl = session.url
    currentHeaders = session.headers
    currentStreamMimeType = PlayerMediaSourceFactory.inferMimeType(
        url = session.url,
        filename = null,
        responseHeaders = emptyMap()
    )
    currentStreamResponseHeaders = emptyMap()
    streamSubtitles = session.subtitles
    _uiState.update { it.copy(currentStreamUrl = session.url) }
    scheduleDeferredPlayerReinitialize(fromPositionMs = positionMs)
}

private fun PlayerRuntimeController.refreshServerAudioTracks() {
    val tracks = serverPlayback.audioTracks(reportedServerUrl).map { track ->
        TrackInfo(index = track.index, name = track.label, language = track.language, isSelected = track.selected)
    }
    _uiState.update { it.copy(serverAudioTracks = tracks, isServerStream = reportedServerUrl != null) }
}
