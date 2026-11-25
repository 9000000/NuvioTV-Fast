package com.nuvio.tv.ui.leanback.core

import android.view.KeyEvent
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget
import com.nuvio.tv.domain.model.isPlaceholder
import kotlin.math.roundToInt

/**
 * Shared sizing, artwork, metadata, and gating rules for the
 * "Expand Focused Poster to Backdrop" feature across Modern Home, Classic Home,
 * and Collection Folder rows.
 */
object LeanbackCardExpandSpec {
    const val EXPANDED_BACKDROP_ASPECT_RATIO: Float = 16f / 9f
    const val CARD_EXPAND_ANIMATION_DURATION_MS: Long = 280L
    const val CARD_COLLAPSE_ANIMATION_DURATION_MS: Long = 200L
    const val BACKDROP_FADE_IN_DURATION_MS: Long = 180L
    const val TRAILER_COVER_FADE_OUT_DURATION_MS: Long = 220L
    const val MIN_MODERN_EXPAND_DELAY_MS: Long = 150L
    const val MIN_CLASSIC_EXPAND_DELAY_MS: Long = 370L
    const val TRAILER_REQUEST_FOCUS_DEBOUNCE_MS: Long = 150L
    const val EXPANDED_LABEL_MIN_HEIGHT_DP: Float = 60f
    const val EXPANDED_LOGO_HEIGHT_FRACTION: Float = 0.34f
    const val EXPANDED_LOGO_WIDTH_FRACTION: Float = 0.65f
    const val MODERN_EXPANDED_TRAILER_OVERSCAN_ZOOM: Float = 1.35f

    /**
     * Smooth Material emphasized-decelerate curve matching the original Compose spring/easing feel.
     */
    fun createExpandInterpolator(): Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    private val YEAR_REGEX = Regex("""\b(19|20)\d{2}\b""")
    private val YEAR_RANGE_REGEX = Regex("""^((19|20)\d{2})\s*[-–]\s*((19|20)\d{2})?$""")

    data class ExpandedArtworkResolution(
        val imageUrl: String?,
        val fallbackUrl: String?,
        val usesLandscapePosterArt: Boolean,
        val showOverlay: Boolean
    )

    fun computeExpandedCardWidthPx(
        baseWidthPx: Int,
        cardHeightPx: Int,
        isLandscape: Boolean
    ): Int {
        val safeBase = baseWidthPx.coerceAtLeast(1)
        if (isLandscape) return safeBase
        val safeHeight = cardHeightPx.coerceAtLeast(1)
        return (safeHeight * EXPANDED_BACKDROP_ASPECT_RATIO).roundToInt().coerceAtLeast(safeBase)
    }

    fun computeBackdropExpandDelayMs(
        delaySeconds: Int,
        minDelayMs: Long
    ): Long {
        val clampedSeconds = delaySeconds.coerceAtLeast(0)
        return if (clampedSeconds == 0) {
            minDelayMs
        } else {
            (clampedSeconds * 1000L).coerceAtLeast(minDelayMs)
        }
    }

    /**
     * Modern Home's HERO_MEDIA_NO_EXPAND target keeps posters collapsed while trailers
     * play in the hero backdrop above.
     */
    fun shouldSuppressModernCardExpand(
        effectiveAutoplayEnabled: Boolean,
        trailerPlaybackTarget: FocusedPosterTrailerPlaybackTarget
    ): Boolean {
        return effectiveAutoplayEnabled &&
            trailerPlaybackTarget == FocusedPosterTrailerPlaybackTarget.HERO_MEDIA_NO_EXPAND
    }

    /**
     * Inline trailer playback inside an expanded Modern Home card is strictly opt-in:
     * it runs only when autoplay trailer is enabled AND the target is EXPANDED_CARD.
     */
    fun shouldPlayTrailerInModernExpandedCard(
        effectiveAutoplayEnabled: Boolean,
        trailerPlaybackTarget: FocusedPosterTrailerPlaybackTarget,
        isBackdropExpanded: Boolean,
        trailerUrl: String?
    ): Boolean {
        return effectiveAutoplayEnabled &&
            trailerPlaybackTarget == FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD &&
            isBackdropExpanded &&
            !trailerUrl.isNullOrBlank()
    }

    /**
     * Inline trailer playback inside an expanded Classic / FolderDetail card is strictly opt-in:
     * it runs only when both backdrop expand and expanded-card trailer autoplay are enabled.
     */
    fun shouldPlayTrailerInClassicExpandedCard(
        focusedPosterBackdropExpandEnabled: Boolean,
        focusedPosterBackdropTrailerEnabled: Boolean,
        isBackdropExpanded: Boolean,
        trailerUrl: String?
    ): Boolean {
        return focusedPosterBackdropExpandEnabled &&
            focusedPosterBackdropTrailerEnabled &&
            isBackdropExpanded &&
            !trailerUrl.isNullOrBlank()
    }

    fun resolveExpandedBackdropArtwork(
        posterUrl: String?,
        backdropUrl: String?,
        landscapePosterUrl: String?,
        rawPosterFallbackUrl: String?,
        alwaysShowLandscapeClearlogo: Boolean,
        isLandscape: Boolean,
        trailerFirstFrameRendered: Boolean = false
    ): ExpandedArtworkResolution {
        val cleanLandscape = landscapePosterUrl?.trim()?.takeIf { it.isNotEmpty() && !it.isPlaceholder() }
        val cleanBackdrop = backdropUrl?.trim()?.takeIf { it.isNotEmpty() && !it.isPlaceholder() }
        val cleanPoster = posterUrl?.trim()?.takeIf { it.isNotEmpty() && !it.isPlaceholder() }
        val cleanRawFallback = rawPosterFallbackUrl?.trim()?.takeIf { it.isNotEmpty() && !it.isPlaceholder() }

        val useLandscapeArt = !alwaysShowLandscapeClearlogo && cleanLandscape != null
        val resolvedImageUrl = when {
            useLandscapeArt -> cleanLandscape
            cleanBackdrop != null -> cleanBackdrop
            else -> cleanPoster
        }
        val resolvedFallbackUrl = when {
            useLandscapeArt -> cleanBackdrop ?: cleanRawFallback ?: cleanPoster
            else -> cleanRawFallback ?: cleanPoster
        }?.takeIf { it != resolvedImageUrl }

        // Landscape custom posters (e.g. RPDB) already embed title artwork, so suppress
        // gradient + clearlogo overlay until a trailer frame replaces the static art.
        val showOverlay = !(useLandscapeArt && !trailerFirstFrameRendered) &&
            (!isLandscape || alwaysShowLandscapeClearlogo || cleanLandscape == null || trailerFirstFrameRendered)

        return ExpandedArtworkResolution(
            imageUrl = resolvedImageUrl,
            fallbackUrl = resolvedFallbackUrl,
            usesLandscapePosterArt = useLandscapeArt,
            showOverlay = showOverlay
        )
    }

    fun formatExpandedMetaTokens(
        apiType: String,
        contentType: ContentType,
        genres: List<String>,
        seasonCount: Int?,
        releaseInfo: String?,
        imdbRating: Float?,
        showImdbRatings: Boolean,
        localizeGenre: (String) -> String = { it }
    ): List<String> {
        return buildList {
            val normalizedType = apiType.trim()
            if (normalizedType.isNotEmpty()) {
                add(normalizedType.replaceFirstChar { ch -> ch.uppercase() })
            }
            genres.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { genre ->
                val localized = localizeGenre(genre).trim()
                if (localized.isNotEmpty()) add(localized)
            }
            val isSeries = contentType == ContentType.SERIES ||
                normalizedType.equals("series", ignoreCase = true)
            if (isSeries && seasonCount != null && seasonCount > 0) {
                add("$seasonCount ${if (seasonCount == 1) "season" else "seasons"}")
            }
            releaseInfo?.trim()?.takeIf { it.isNotEmpty() }?.let { info ->
                val rangeMatch = YEAR_RANGE_REGEX.find(info)
                val yearToken = if (rangeMatch != null) {
                    val startYear = rangeMatch.groupValues[1]
                    val endYear = rangeMatch.groupValues[3]
                    if (endYear.isNotBlank()) "$startYear–$endYear" else startYear
                } else {
                    YEAR_REGEX.find(info)?.value
                }
                if (!yearToken.isNullOrBlank()) add(yearToken)
            }
            if (showImdbRatings && imdbRating != null && imdbRating > 0f) {
                add(String.format(java.util.Locale.US, "%.1f", imdbRating))
            }
        }
    }

    /**
     * Directional and Back keys collapse/reset the expand timer immediately.
     * Select keys (Center / Enter) are intentionally excluded so holding OK to open
     * the context menu does not collapse and re-expand the card (#2574).
     */
    fun shouldResetExpandTimerOnKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BACK -> true
            else -> false
        }
    }

    /**
     * Single horizontal D-pad steps animate the previously expanded card back to its poster width
     * for visual parity with the Compose implementation; fast key-repeats and vertical/back
     * exits snap immediately to preserve 60fps navigation responsiveness.
     */
    fun shouldAnimateCollapseOnKey(
        keyCode: Int,
        repeatCount: Int,
        animationsEnabled: Boolean
    ): Boolean {
        if (!animationsEnabled || repeatCount > 0) return false
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
    }

    /**
     * Resolves the authoritative pixel dimension of a card without reading stale pre-layout
     * [android.view.View.getWidth] / [android.view.View.getHeight] values right after a
     * `LayoutParams` mutation.
     */
    fun resolveEffectiveCardDimensionPx(
        explicitPx: Int?,
        layoutParamPx: Int?,
        measuredPx: Int,
        isLayoutRequested: Boolean,
        fallbackPx: Int = 0
    ): Int {
        if (explicitPx != null && explicitPx > 0) return explicitPx
        if (isLayoutRequested && layoutParamPx != null && layoutParamPx > 0) return layoutParamPx
        if (measuredPx > 0) return measuredPx
        if (layoutParamPx != null && layoutParamPx > 0) return layoutParamPx
        return fallbackPx.coerceAtLeast(0)
    }

    /**
     * Keeps an in-flight width animator alive when secondary state updates (e.g. TMDB enrichment
     * or trailer URL availability) arrive for the same target width.
     */
    fun shouldRetainRunningWidthAnimation(
        isRunning: Boolean,
        animatingTargetWidthPx: Int,
        desiredTargetWidthPx: Int
    ): Boolean {
        return isRunning && animatingTargetWidthPx > 0 && animatingTargetWidthPx == desiredTargetWidthPx
    }
}
