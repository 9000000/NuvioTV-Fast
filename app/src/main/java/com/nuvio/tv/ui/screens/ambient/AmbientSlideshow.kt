package com.nuvio.tv.ui.screens.ambient

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.nuvio.tv.data.trailer.TrailerPlaybackSource
import com.nuvio.tv.domain.model.AmbientDisplayMode
import com.nuvio.tv.domain.model.AmbientModeSettings
import com.nuvio.tv.ui.components.TrailerPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val BACKDROP_FADE_IN_MS = 900
private const val TITLE_DELAY_MS = 250L
private const val TITLE_FADE_IN_MS = 500
private const val GENRES_DELAY_MS = 350L
private const val GENRES_FADE_IN_MS = 450
private const val SLIDE_HOLD_MS = 10_000L
private const val MIN_FALLBACK_HOLD_MS = 2_000L
private const val GENRES_FADE_OUT_MS = 250
private const val TITLE_FADE_OUT_MS = 350
private const val BACKDROP_FADE_OUT_MS = 700
private const val TRAILER_FADE_IN_MS = 500
private const val TRAILER_ERROR_FADE_OUT_MS = 300
private const val TRAILER_FIRST_FRAME_TIMEOUT_MS = 8_000L
private const val TRAILER_NEXT_PRELOAD_SETTLE_DELAY_MS = 3_000L
private const val BLACK_HOLD_MS = 300L
private const val ALL_FAILED_RETRY_MS = 15_000L
private const val EMPTY_POLL_MS = 500L
private const val MOTION_DURATION_MS = BACKDROP_FADE_IN_MS + TITLE_DELAY_MS.toInt() + TITLE_FADE_IN_MS +
    SLIDE_HOLD_MS.toInt() + GENRES_FADE_OUT_MS + TITLE_FADE_OUT_MS + BACKDROP_FADE_OUT_MS

private enum class AmbientTrailerOutcome {
    COMPLETED,
    FAILED
}

private class ActiveAmbientTrailer(
    val token: Long,
    val source: TrailerPlaybackSource,
    val firstFrameReady: CompletableDeferred<Boolean> = CompletableDeferred(),
    val outcome: CompletableDeferred<AmbientTrailerOutcome> = CompletableDeferred()
)

private class LoadedSlide(
    val slide: AmbientSlide,
    val backdrop: ImageBitmap,
    val logo: ImageBitmap?,
    val trailerDeferred: Deferred<TrailerPlaybackSource?>?
)

@Composable
fun AmbientSlideshow(
    slides: List<AmbientSlide>,
    displayMode: AmbientDisplayMode = AmbientModeSettings.DEFAULT_DISPLAY_MODE,
    trailerAudioEnabled: Boolean = AmbientModeSettings.DEFAULT_TRAILER_AUDIO_ENABLED,
    allowTrailerPlayback: Boolean = true,
    resolveTrailer: suspend (AmbientSlide) -> TrailerPlaybackSource? = { null },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val titleOffsetPx = with(density) { 18.dp.toPx() }
    val genresOffsetPx = with(density) { 12.dp.toPx() }

    var current by remember { mutableStateOf<LoadedSlide?>(null) }
    var activeTrailer by remember { mutableStateOf<ActiveAmbientTrailer?>(null) }
    val backdropAlpha = remember { Animatable(0f) }
    val videoAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val genresAlpha = remember { Animatable(0f) }
    val motionProgress = remember { Animatable(0f) }
    var motion by remember { mutableStateOf(nextAmbientMotion(null)) }

    val latestSlides by rememberUpdatedState(slides)
    val latestDisplayMode by rememberUpdatedState(displayMode)
    val latestAllowTrailer by rememberUpdatedState(allowTrailerPlayback)
    val latestResolveTrailer by rememberUpdatedState(resolveTrailer)

    LaunchedEffect(allowTrailerPlayback, displayMode) {
        if (!allowTrailerPlayback || displayMode != AmbientDisplayMode.TRAILER) {
            activeTrailer?.let { session ->
                session.firstFrameReady.complete(false)
                session.outcome.complete(AmbientTrailerOutcome.FAILED)
            }
            activeTrailer = null
        }
    }

    LaunchedEffect(displayMode) {
        coroutineScope {
            var index = 0
            var misses = 0
            var trailerToken = 0L
            var pending: Deferred<LoadedSlide?>? = null
            try {
                while (true) {
                    val available = latestSlides
                    if (available.isEmpty()) {
                        pending?.cancel()
                        pending = null
                        delay(EMPTY_POLL_MS)
                        continue
                    }
                    val wantTrailer = latestDisplayMode == AmbientDisplayMode.TRAILER
                    val loaded = (pending ?: async {
                        loadSlide(
                            scope = this@coroutineScope,
                            context = context,
                            slide = available[index % available.size],
                            widthPx = screenWidthPx,
                            heightPx = screenHeightPx,
                            preloadTrailer = wantTrailer,
                            resolveTrailer = latestResolveTrailer
                        )
                    }).await()
                    pending = null
                    index += 1
                    fun scheduleNextPreload() {
                        if (pending != null) return
                        val upcoming = latestSlides
                        pending = upcoming.takeIf { it.isNotEmpty() }?.let { list ->
                            val next = list[index % list.size]
                            async {
                                loadSlide(
                                    scope = this@coroutineScope,
                                    context = context,
                                    slide = next,
                                    widthPx = screenWidthPx,
                                    heightPx = screenHeightPx,
                                    preloadTrailer = latestDisplayMode == AmbientDisplayMode.TRAILER,
                                    resolveTrailer = latestResolveTrailer
                                )
                            }
                        }
                    }
                    if (!wantTrailer) {
                        scheduleNextPreload()
                    }
                    if (loaded == null) {
                        scheduleNextPreload()
                        misses += 1
                        if (misses >= available.size) {
                            misses = 0
                            delay(ALL_FAILED_RETRY_MS)
                        }
                        continue
                    }
                    misses = 0
                    current = loaded
                    activeTrailer = null
                    motion = nextAmbientMotion(motion)
                    motionProgress.snapTo(0f)
                    videoAlpha.snapTo(0f)
                    var motionJob = launch {
                        motionProgress.animateTo(1f, tween(MOTION_DURATION_MS, easing = LinearEasing))
                    }
                    backdropAlpha.animateTo(1f, tween(BACKDROP_FADE_IN_MS, easing = FastOutSlowInEasing))
                    delay(TITLE_DELAY_MS)
                    val genresJob = launch {
                        delay(GENRES_DELAY_MS)
                        genresAlpha.animateTo(1f, tween(GENRES_FADE_IN_MS, easing = LinearOutSlowInEasing))
                    }
                    titleAlpha.animateTo(1f, tween(TITLE_FADE_IN_MS, easing = LinearOutSlowInEasing))
                    val titleShownAtMs = SystemClock.elapsedRealtime()

                    if (latestDisplayMode == AmbientDisplayMode.TRAILER) {
                        // Wait until AmbientModeHost finishes stopping any previous Home/Detail trailer.
                        if (!latestAllowTrailer) {
                            withTimeoutOrNull(2_000L) {
                                snapshotFlow { latestAllowTrailer }.first { it }
                            }
                        }
                        val trailerSource = loaded.trailerDeferred?.await()
                        if (trailerSource != null && latestAllowTrailer && latestDisplayMode == AmbientDisplayMode.TRAILER) {
                            trailerToken += 1L
                            val session = ActiveAmbientTrailer(token = trailerToken, source = trailerSource)
                            activeTrailer = session
                            val renderedFirstFrame = withTimeoutOrNull(TRAILER_FIRST_FRAME_TIMEOUT_MS) {
                                session.firstFrameReady.await()
                            } ?: false

                            if (renderedFirstFrame && latestAllowTrailer) {
                                motionJob.cancel()
                                videoAlpha.animateTo(1f, tween(TRAILER_FADE_IN_MS, easing = FastOutSlowInEasing))
                                // Defer next slide bitmap decode & YouTube probe until playback has settled
                                // so initial 1080p/4K video buffering never competes for CPU or bandwidth.
                                val deferredPreloadJob = launch {
                                    delay(TRAILER_NEXT_PRELOAD_SETTLE_DELAY_MS)
                                    scheduleNextPreload()
                                }
                                val outcome = session.outcome.await()
                                deferredPreloadJob.cancel()
                                scheduleNextPreload()
                                if (outcome == AmbientTrailerOutcome.FAILED && latestAllowTrailer) {
                                    videoAlpha.animateTo(
                                        0f,
                                        tween(TRAILER_ERROR_FADE_OUT_MS, easing = FastOutSlowInEasing)
                                    )
                                    activeTrailer = null
                                    val remainingMs = SLIDE_HOLD_MS - (SystemClock.elapsedRealtime() - titleShownAtMs)
                                    if (remainingMs > 0L) {
                                        motionJob = launch {
                                            motionProgress.animateTo(1f, tween(remainingMs.toInt(), easing = LinearEasing))
                                        }
                                        delay(remainingMs)
                                    }
                                }
                            } else {
                                activeTrailer = null
                                scheduleNextPreload()
                                val elapsedMs = SystemClock.elapsedRealtime() - titleShownAtMs
                                val remainingMs = (SLIDE_HOLD_MS - elapsedMs).coerceAtLeast(MIN_FALLBACK_HOLD_MS)
                                delay(remainingMs)
                            }
                        } else {
                            scheduleNextPreload()
                            val elapsedMs = SystemClock.elapsedRealtime() - titleShownAtMs
                            val remainingMs = (SLIDE_HOLD_MS - elapsedMs).coerceAtLeast(0L)
                            if (remainingMs > 0L) delay(remainingMs)
                        }
                    } else {
                        delay(SLIDE_HOLD_MS)
                    }

                    loaded.trailerDeferred?.cancel()
                    genresJob.cancel()
                    genresAlpha.animateTo(0f, tween(GENRES_FADE_OUT_MS, easing = FastOutSlowInEasing))
                    titleAlpha.animateTo(0f, tween(TITLE_FADE_OUT_MS, easing = FastOutSlowInEasing))
                    backdropAlpha.animateTo(0f, tween(BACKDROP_FADE_OUT_MS, easing = FastOutSlowInEasing))
                    activeTrailer = null
                    videoAlpha.snapTo(0f)
                    motionJob.cancel()
                    delay(BLACK_HOLD_MS)
                }
            } finally {
                activeTrailer = null
                pending?.cancel()
            }
        }
    }

    val activeSession = if (allowTrailerPlayback && displayMode == AmbientDisplayMode.TRAILER) activeTrailer else null
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
    ) {
        val loaded = current ?: return@Box
        if (activeSession != null) {
            key(activeSession.token) {
                TrailerPlayer(
                    trailerUrl = activeSession.source.videoUrl,
                    trailerAudioUrl = activeSession.source.audioUrl,
                    isPlaying = true,
                    muted = !trailerAudioEnabled,
                    onFirstFrameRendered = {
                        activeSession.firstFrameReady.complete(true)
                    },
                    onEnded = {
                        activeSession.firstFrameReady.complete(true)
                        activeSession.outcome.complete(AmbientTrailerOutcome.COMPLETED)
                    },
                    onError = {
                        activeSession.firstFrameReady.complete(false)
                        activeSession.outcome.complete(AmbientTrailerOutcome.FAILED)
                    },
                    // Hardware Overlay zero-copy SurfaceView prevents GPU TextureView frame drops
                    // during full-screen 1080p60/4K playback; fades are driven by the scrim above.
                    cropToFill = true,
                    autoCropLetterbox = false,
                    detectLetterbox = false,
                    showSubtitles = false,
                    useSurfaceView = true,
                    trackProgress = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Black scrim fades out once first frame renders and fades back in on slide exit,
            // replacing TextureView ModulateAlpha with zero-copy SurfaceView composition.
            val videoScrimAlpha = 1f - (videoAlpha.value * backdropAlpha.value)
            if (videoScrimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = videoScrimAlpha }
                        .background(Color.Black)
                )
            }
        }
        val effectiveBackdropAlpha = backdropAlpha.value * (1f - videoAlpha.value)
        if (effectiveBackdropAlpha > 0f) {
            Image(
                bitmap = loaded.backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = effectiveBackdropAlpha
                        applyAmbientMotion(motion, motionProgress.value)
                    }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backdropAlpha.value }
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.7f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 72.dp, end = 72.dp, bottom = 64.dp)
                .widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val titleModifier = Modifier.graphicsLayer {
                alpha = titleAlpha.value
                translationY = (1f - titleAlpha.value) * titleOffsetPx
            }
            if (loaded.logo != null) {
                Image(
                    bitmap = loaded.logo,
                    contentDescription = loaded.slide.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter,
                    modifier = titleModifier
                        .heightIn(max = 150.dp)
                        .widthIn(max = 460.dp)
                )
            } else {
                Text(
                    text = loaded.slide.title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = titleModifier
                )
            }
            if (loaded.slide.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = loaded.slide.genres.joinToString("  •  "),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        alpha = genresAlpha.value
                        translationY = (1f - genresAlpha.value) * genresOffsetPx
                    }
                )
            }
        }
    }
}

private suspend fun loadSlide(
    scope: CoroutineScope,
    context: Context,
    slide: AmbientSlide,
    widthPx: Int,
    heightPx: Int,
    preloadTrailer: Boolean,
    resolveTrailer: suspend (AmbientSlide) -> TrailerPlaybackSource?
): LoadedSlide? = coroutineScope {
    val logo = slide.logoUrl?.let { url -> async { loadBitmap(context, url, null) } }
    // Launch trailer resolution on the parent slideshow scope so it keeps running while the backdrop renders.
    val trailerDeferred = if (preloadTrailer) {
        scope.async { resolveTrailer(slide) }
    } else {
        null
    }
    val backdrop = loadBitmap(context, slide.backdropUrl, widthPx to heightPx) ?: run {
        logo?.cancel()
        trailerDeferred?.cancel()
        return@coroutineScope null
    }
    LoadedSlide(slide, backdrop, logo?.await(), trailerDeferred)
}

private suspend fun loadBitmap(
    context: Context,
    url: String,
    targetSize: Pair<Int, Int>?
): ImageBitmap? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .apply { targetSize?.let { (width, height) -> size(width, height) } }
        .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
    return runCatching { result.image.toBitmap().asImageBitmap() }.getOrNull()
}
