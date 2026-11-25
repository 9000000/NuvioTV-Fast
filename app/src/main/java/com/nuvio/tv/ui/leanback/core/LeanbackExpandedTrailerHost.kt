package com.nuvio.tv.ui.leanback.core

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nuvio.tv.R
import com.nuvio.tv.core.player.LocalTrailerPlayerPool
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.data.trailer.YoutubeChunkedDataSourceFactory

/**
 * Single shared inline trailer host for expanded Leanback poster cards.
 *
 * At most one poster card can be expanded on screen at any time. Inflating and attaching a single
 * TextureView-backed [PlayerView] on demand avoids allocating media surfaces inside pooled
 * ViewHolders, respects rounded card outlines, and never touches [TrailerPlayerPool] unless the
 * user has explicitly enabled trailer autoplay.
 */
@OptIn(UnstableApi::class)
class LeanbackExpandedTrailerHost(
    private val trailerPlayerPool: TrailerPlayerPool,
    private val lifecycleOwner: LifecycleOwner
) {
    private val ownerToken = Any()
    private val locationBuffer = IntArray(2)

    private var activeOwnerKey: String? = null
    private var activeVideoUrl: String? = null
    private var activeAudioUrl: String? = null
    private var activeContainer: FrameLayout? = null
    private var playerView: PlayerView? = null
    private var activePlayer: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var layoutChangeListener: View.OnLayoutChangeListener? = null
    private var lifecycleRegistered: Boolean = false
    private var hasRenderedFirstFrame: Boolean = false
    private var onFirstFrameCallback: (() -> Unit)? = null
    private var onEndedCallback: (() -> Unit)? = null
    private var onErrorCallback: (() -> Unit)? = null

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_PAUSE,
            Lifecycle.Event.ON_STOP -> {
                detach()
            }
            else -> Unit
        }
    }

    fun isPlayingFor(ownerKey: String): Boolean = activeOwnerKey == ownerKey && activePlayer != null

    fun attachAndPlay(
        container: FrameLayout,
        ownerKey: String,
        videoUrl: String,
        audioUrl: String?,
        muted: Boolean,
        cropToFill: Boolean,
        overscanZoom: Float = 1f,
        onFirstFrameRendered: () -> Unit,
        onEnded: () -> Unit,
        onError: () -> Unit = {}
    ) {
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            return
        }
        if (videoUrl.isBlank()) {
            detach(ownerKey)
            return
        }

        val resolvedZoom = if (cropToFill) overscanZoom.coerceAtLeast(1f) else 1f
        if (activeOwnerKey == ownerKey &&
            activeVideoUrl == videoUrl &&
            activeAudioUrl == audioUrl &&
            activeContainer === container &&
            activePlayer != null &&
            playerView?.parent === container
        ) {
            onFirstFrameCallback = onFirstFrameRendered
            onEndedCallback = onEnded
            onErrorCallback = onError
            activePlayer?.let { player ->
                player.volume = if (muted) 0f else 1f
                player.videoScalingMode = if (cropToFill) {
                    C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                } else {
                    C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                }
            }
            playerView?.apply {
                resizeMode = if (cropToFill) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                scaleX = resolvedZoom
                scaleY = resolvedZoom
            }
            if (hasRenderedFirstFrame) {
                onFirstFrameRendered()
            }
            return
        }

        detach()

        val player = trailerPlayerPool.acquire() ?: return
        val view = ensurePlayerView(container.context)
        (view.parent as? ViewGroup)?.removeView(view)

        onFirstFrameCallback = onFirstFrameRendered
        onEndedCallback = onEnded
        onErrorCallback = onError
        hasRenderedFirstFrame = false
        activeOwnerKey = ownerKey
        activeVideoUrl = videoUrl
        activeAudioUrl = audioUrl
        activeContainer = container
        activePlayer = player

        registerLifecycleIfNeeded()

        view.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            keepScreenOn = true
            resizeMode = if (cropToFill) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            scaleX = resolvedZoom
            scaleY = resolvedZoom
            alpha = 0f
            this.player = player
        }

        container.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        container.visibility = View.VISIBLE

        val boundsListener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            publishOwnerBounds(v)
        }
        layoutChangeListener = boundsListener
        container.addOnLayoutChangeListener(boundsListener)
        publishOwnerBounds(container)

        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (activeOwnerKey != ownerKey) return
                hasRenderedFirstFrame = true
                playerView?.alpha = 1f
                onFirstFrameCallback?.invoke()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (activeOwnerKey != ownerKey) return
                if (playbackState == Player.STATE_ENDED && player.playWhenReady) {
                    val ended = onEndedCallback
                    detach(ownerKey)
                    ended?.invoke()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (activeOwnerKey != ownerKey) return
                Log.w(TAG, "Expanded card trailer playback error: ${error.errorCodeName}", error)
                val onErr = onErrorCallback
                detach(ownerKey)
                onErr?.invoke()
            }
        }
        playerListener = listener
        player.addListener(listener)

        try {
            player.volume = if (muted) 0f else 1f
            player.videoScalingMode = if (cropToFill) {
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            } else {
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
            if (!audioUrl.isNullOrBlank()) {
                val mediaSourceFactory = DefaultMediaSourceFactory(YoutubeChunkedDataSourceFactory())
                val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(videoUrl))
                val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioUrl))
                player.setMediaSource(MergingMediaSource(videoSource, audioSource))
            } else {
                player.setMediaItem(MediaItem.fromUri(videoUrl))
            }
            player.prepare()
            trailerPlayerPool.onMediaLoaded(ownerToken, videoUrl)
            publishOwnerBounds(container)
            player.playWhenReady = true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start expanded card trailer playback", t)
            detach(ownerKey)
            onError()
        }
    }

    fun detach(ownerKey: String? = null) {
        if (ownerKey != null && activeOwnerKey != ownerKey) return

        val container = activeContainer
        layoutChangeListener?.let { listener ->
            container?.removeOnLayoutChangeListener(listener)
        }
        layoutChangeListener = null

        val player = activePlayer
        playerListener?.let { listener ->
            runCatching { player?.removeListener(listener) }
        }
        playerListener = null

        playerView?.let { view ->
            view.player = null
            view.keepScreenOn = false
            view.alpha = 0f
            (view.parent as? ViewGroup)?.removeView(view)
        }

        if (container != null) {
            container.visibility = View.GONE
        }

        activeOwnerKey = null
        activeVideoUrl = null
        activeAudioUrl = null
        activeContainer = null
        activePlayer = null
        hasRenderedFirstFrame = false
        onFirstFrameCallback = null
        onEndedCallback = null
        onErrorCallback = null

        unregisterLifecycleIfNeeded()

        // Preserve ongoing trailer handoff when user clicks into Details while playing.
        if (!trailerPlayerPool.isHandoffPending()) {
            trailerPlayerPool.stopIfOwner(ownerToken)
        }
    }

    private fun ensurePlayerView(context: Context): PlayerView {
        val existing = playerView
        if (existing != null && existing.context === context) {
            return existing
        }
        val inflated = LayoutInflater.from(context).inflate(R.layout.trailer_player_view, null) as PlayerView
        playerView = inflated
        return inflated
    }

    private fun publishOwnerBounds(view: View) {
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return
        view.getLocationInWindow(locationBuffer)
        val left = locationBuffer[0].toFloat()
        val top = locationBuffer[1].toFloat()
        trailerPlayerPool.onOwnerBounds(
            ownerToken,
            Rect(left, top, left + view.width, top + view.height)
        )
    }

    private fun registerLifecycleIfNeeded() {
        if (!lifecycleRegistered) {
            runCatching {
                lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
                lifecycleRegistered = true
            }
        }
    }

    private fun unregisterLifecycleIfNeeded() {
        if (lifecycleRegistered) {
            runCatching { lifecycleOwner.lifecycle.removeObserver(lifecycleObserver) }
            lifecycleRegistered = false
        }
    }

    companion object {
        private const val TAG = "LeanbackExpandedTrailer"
    }
}

@Composable
fun rememberLeanbackExpandedTrailerHost(): LeanbackExpandedTrailerHost? {
    val pool = LocalTrailerPlayerPool.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val host = remember(pool, lifecycleOwner) {
        pool?.let { LeanbackExpandedTrailerHost(it, lifecycleOwner) }
    }
    DisposableEffect(host) {
        onDispose {
            host?.detach()
        }
    }
    return host
}
