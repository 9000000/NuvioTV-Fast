package com.nuvio.tv.features.livetv

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class LiveTvPlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var _player: ExoPlayer? = null
    val player: ExoPlayer? get() = _player

    private val _state = MutableStateFlow(LivePlaybackState())
    val state: StateFlow<LivePlaybackState> = _state.asStateFlow()

    private var currentChannel: LiveTvChannel? = null
    val playingChannel: LiveTvChannel? get() = currentChannel

    companion object {
        @Volatile
        private var INSTANCE: LiveTvPlaybackController? = null

        fun getInstance(context: Context): LiveTvPlaybackController =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiveTvPlaybackController(context.applicationContext).also { INSTANCE = it }
            }
    }

    private var currentUrl: String? = null
    val playingUrl: String? get() = currentUrl

    private var attachCount = 0
    private var releaseJob: Job? = null
    private var reconnectJob: Job? = null
    var autoReconnect: Boolean = true

    fun attach(): ExoPlayer {
        releaseJob?.cancel()
        attachCount++
        return _player ?: buildPlayer().also { _player = it }
    }

    fun detach() {
        attachCount = (attachCount - 1).coerceAtLeast(0)
        if (attachCount == 0) {
            releaseJob?.cancel()
            releaseJob = scope.launch {
                delay(1_000)
                if (attachCount == 0) release()
            }
        }
    }

    fun play(
        channel: LiveTvChannel,
        overrideUrl: String? = null,
        catchupTitle: String? = null,
        catchupStartMs: Long? = null,
        reconnectAttempt: Int = 0
    ) {
        scope.launch {
            val prepared = runCatching {
                LiveTvRepository.prepareForPlayback(channel)
            }.getOrDefault(channel)

            val targetUrl = overrideUrl ?: prepared.streamUrl
            if (targetUrl.isBlank()) {
                _state.value = _state.value.copy(error = "Stream URL is empty", isBuffering = false)
                return@launch
            }

            val sameStream = currentChannel?.id == prepared.id && currentUrl == targetUrl &&
                _state.value.catchupTitle == catchupTitle && _state.value.error == null

            if (sameStream && _player?.isPlaying == false) {
                _player?.play()
                return@launch
            }

            reconnectJob?.cancel()
            currentChannel = prepared
            currentUrl = targetUrl

            val p = attach()
            _state.value = LivePlaybackState(
                channelKey = prepared.id,
                isBuffering = true,
                catchupTitle = catchupTitle,
                reconnectAttempt = reconnectAttempt
            )

            try {
                val effectiveType = prepared.streamType?.takeIf(String::isNotBlank)
                    ?: when {
                        targetUrl.contains(".flv", ignoreCase = true) -> "flv"
                        targetUrl.contains(".ts", ignoreCase = true) -> "ts"
                        targetUrl.contains(".mp4", ignoreCase = true) -> "mp4"
                        targetUrl.contains(".mkv", ignoreCase = true) -> "mkv"
                        targetUrl.contains(".mpd", ignoreCase = true) -> "mpd"
                        targetUrl.contains(".m3u8", ignoreCase = true) -> "m3u8"
                        else -> "m3u8"
                    }
                val filename = "live.$effectiveType"
                val factory = com.nuvio.tv.ui.screens.player.PlayerMediaSourceFactory(context)
                val mediaSource = factory.createMediaSource(
                    context = context,
                    url = targetUrl,
                    headers = prepared.headers,
                    filename = filename,
                    drmType = prepared.drmType,
                    drmKey = prepared.drmKey
                )
                p.setMediaSource(mediaSource)
                p.prepare()
                p.playWhenReady = true
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Failed to play stream", isBuffering = false)
            }
        }
    }

    fun preview(channel: LiveTvChannel) {
        if (currentChannel?.id == channel.id && (_state.value.isPlaying || _state.value.isBuffering)) {
            return
        }
        play(channel)
    }

    fun togglePause() {
        val p = _player ?: return
        if (p.isPlaying) {
            p.pause()
            _state.value = _state.value.copy(userPaused = true)
        } else {
            p.play()
            _state.value = _state.value.copy(userPaused = false)
        }
    }

    fun seekBy(ms: Long) {
        val p = _player ?: return
        p.seekTo((p.currentPosition + ms).coerceAtLeast(0))
    }

    fun stop() {
        reconnectJob?.cancel()
        _player?.playWhenReady = false
        _player?.stop()
        _player?.clearMediaItems()
        currentChannel = null
        currentUrl = null
        _state.value = LivePlaybackState()
    }

    fun retry() {
        val ch = currentChannel ?: return
        val url = currentUrl
        play(ch, url, _state.value.catchupTitle)
    }

    fun audioTracks(): List<LiveTrackOption> = tracksOf(C.TRACK_TYPE_AUDIO)

    fun subtitleTracks(): List<LiveTrackOption> = tracksOf(C.TRACK_TYPE_TEXT)

    fun selectTrack(type: Int, option: LiveTrackOption?) {
        val p = _player ?: return
        val builder = p.trackSelectionParameters.buildUpon()
        if (option == null) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            val group = p.currentTracks.groups.getOrNull(option.groupIndex) ?: return
            builder.setTrackTypeDisabled(type, false)
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        }
        p.trackSelectionParameters = builder.build()
    }

    private fun tracksOf(type: Int): List<LiveTrackOption> {
        val p = _player ?: return emptyList()
        val out = mutableListOf<LiveTrackOption>()
        p.currentTracks.groups.forEachIndexed { gi, group: Tracks.Group ->
            if (group.type != type) return@forEachIndexed
            for (ti in 0 until group.length) {
                if (!group.isTrackSupported(ti)) continue
                val f = group.getTrackFormat(ti)
                val parts = listOfNotNull(
                    f.label,
                    f.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifBlank { it } },
                    f.sampleMimeType?.substringAfter('/')?.uppercase(),
                    if (f.channelCount > 0) "${f.channelCount}ch" else null
                )
                out += LiveTrackOption(
                    groupIndex = gi,
                    trackIndex = ti,
                    label = parts.distinct().joinToString(" · ").ifBlank { "Track ${out.size + 1}" },
                    selected = group.isTrackSelected(ti),
                    kind = null,
                    language = f.language,
                    channels = f.channelCount.coerceAtLeast(0)
                )
            }
        }
        return out
    }

    private fun buildMediaSource(url: String, customHeaders: Map<String, String>): MediaSource {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        if (customHeaders.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(customHeaders)
        }

        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val uri = Uri.parse(url)

        val isHls = url.contains(".m3u8", ignoreCase = true) || url.contains("m3u8", ignoreCase = true)
        val isDash = url.contains(".mpd", ignoreCase = true)

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .apply {
                if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8)
                else if (isDash) setMimeType(MimeTypes.APPLICATION_MPD)
            }
            .build()

        return if (isHls) {
            val hlsExtractorFactory = androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES,
                /* exposeCea608WhenMissingDeclarations = */ true
            )
            HlsMediaSource.Factory(dataSourceFactory)
                .setExtractorFactory(hlsExtractorFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
        } else {
            DefaultMediaSourceFactory(dataSourceFactory)
                .createMediaSource(mediaItem)
        }
    }

    private fun buildPlayer(): ExoPlayer {
        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        // Low latency buffer control for Live TV
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 3_000,
                /* maxBufferMs = */ 10_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .setBackBuffer(0, false)
            .build()

        val exoplayer = ExoPlayer.Builder(context, renderers)
            .setLoadControl(loadControl)
            .build()

        exoplayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val isBuffering = state == Player.STATE_BUFFERING
                val isEnded = state == Player.STATE_ENDED
                val isReady = state == Player.STATE_READY
                _state.value = _state.value.copy(
                    isBuffering = isBuffering,
                    isPlaying = isReady && exoplayer.playWhenReady,
                    error = if (isReady) null else _state.value.error
                )
                if (isEnded && autoReconnect) {
                    scheduleReconnect("Stream reached end")
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                val msg = formatError(error)
                _state.value = _state.value.copy(
                    error = msg,
                    isBuffering = false,
                    isPlaying = false
                )
                if (autoReconnect) {
                    scheduleReconnect(msg)
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                _state.value = _state.value.copy(
                    videoWidth = videoSize.width,
                    videoHeight = videoSize.height,
                    pixelRatio = videoSize.pixelWidthHeightRatio
                )
            }
        })

        return exoplayer
    }

    private fun scheduleReconnect(reason: String) {
        val ch = currentChannel ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val attempt = _state.value.reconnectAttempt + 1
            if (attempt > 5) {
                _state.value = _state.value.copy(
                    error = "Failed to reconnect after multiple attempts: $reason",
                    isBuffering = false
                )
                return@launch
            }
            delay(2_000L * attempt)
            play(ch, currentUrl, _state.value.catchupTitle, reconnectAttempt = attempt)
        }
    }

    private fun formatError(e: PlaybackException): String {
        return when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Network connection failed or timed out"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "Server returned an HTTP error"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED ->
                "Decoder failed for this stream format"
            else -> e.message ?: "Playback error"
        }
    }

    fun release() {
        reconnectJob?.cancel()
        _player?.release()
        _player = null
        currentChannel = null
        currentUrl = null
        _state.value = LivePlaybackState()
    }
}
