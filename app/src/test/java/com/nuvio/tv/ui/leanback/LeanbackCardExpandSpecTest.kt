package com.nuvio.tv.ui.leanback

import android.view.KeyEvent
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget
import com.nuvio.tv.domain.model.PLACEHOLDER_IMAGE_URL
import com.nuvio.tv.ui.leanback.core.LeanbackCardExpandSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeanbackCardExpandSpecTest {

    @Test
    fun `portrait poster expands to 16 by 9 width from height`() {
        val baseWidthPx = 126
        val cardHeightPx = 189

        val expanded = LeanbackCardExpandSpec.computeExpandedCardWidthPx(
            baseWidthPx = baseWidthPx,
            cardHeightPx = cardHeightPx,
            isLandscape = false
        )

        assertEquals(336, expanded)
    }

    @Test
    fun `landscape poster preserves base width when expanded`() {
        val baseWidthPx = 260
        val cardHeightPx = 146

        val expanded = LeanbackCardExpandSpec.computeExpandedCardWidthPx(
            baseWidthPx = baseWidthPx,
            cardHeightPx = cardHeightPx,
            isLandscape = true
        )

        assertEquals(baseWidthPx, expanded)
    }

    @Test
    fun `expand delay respects zero-second minimum debounce and positive seconds`() {
        assertEquals(
            LeanbackCardExpandSpec.MIN_MODERN_EXPAND_DELAY_MS,
            LeanbackCardExpandSpec.computeBackdropExpandDelayMs(0, LeanbackCardExpandSpec.MIN_MODERN_EXPAND_DELAY_MS)
        )
        assertEquals(
            LeanbackCardExpandSpec.MIN_CLASSIC_EXPAND_DELAY_MS,
            LeanbackCardExpandSpec.computeBackdropExpandDelayMs(-2, LeanbackCardExpandSpec.MIN_CLASSIC_EXPAND_DELAY_MS)
        )
        assertEquals(
            3_000L,
            LeanbackCardExpandSpec.computeBackdropExpandDelayMs(3, LeanbackCardExpandSpec.MIN_CLASSIC_EXPAND_DELAY_MS)
        )
    }

    @Test
    fun `modern card expand is suppressed only when autoplay is enabled with HERO_MEDIA_NO_EXPAND`() {
        assertTrue(
            LeanbackCardExpandSpec.shouldSuppressModernCardExpand(
                effectiveAutoplayEnabled = true,
                trailerPlaybackTarget = FocusedPosterTrailerPlaybackTarget.HERO_MEDIA_NO_EXPAND
            )
        )
        assertFalse(
            LeanbackCardExpandSpec.shouldSuppressModernCardExpand(
                effectiveAutoplayEnabled = false,
                trailerPlaybackTarget = FocusedPosterTrailerPlaybackTarget.HERO_MEDIA_NO_EXPAND
            )
        )
        assertFalse(
            LeanbackCardExpandSpec.shouldSuppressModernCardExpand(
                effectiveAutoplayEnabled = true,
                trailerPlaybackTarget = FocusedPosterTrailerPlaybackTarget.HERO_MEDIA
            )
        )
        assertFalse(
            LeanbackCardExpandSpec.shouldSuppressModernCardExpand(
                effectiveAutoplayEnabled = true,
                trailerPlaybackTarget = FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD
            )
        )
    }

    @Test
    fun `expanded card trailer plays strictly when trailer toggle is enabled`() {
        val url = "https://example.com/trailer.mp4"

        // Modern Home: autoplay disabled -> never plays
        assertFalse(
            LeanbackCardExpandSpec.shouldPlayTrailerInModernExpandedCard(
                effectiveAutoplayEnabled = false,
                trailerPlaybackTarget = FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD,
                isBackdropExpanded = true,
                trailerUrl = url
            )
        )
        // Modern Home: autoplay enabled with HERO_MEDIA -> never plays inline
        assertFalse(
            LeanbackCardExpandSpec.shouldPlayTrailerInModernExpandedCard(
                effectiveAutoplayEnabled = true,
                trailerPlaybackTarget = FocusedPosterTrailerPlaybackTarget.HERO_MEDIA,
                isBackdropExpanded = true,
                trailerUrl = url
            )
        )
        // Modern Home: autoplay enabled with EXPANDED_CARD and expanded -> plays
        assertTrue(
            LeanbackCardExpandSpec.shouldPlayTrailerInModernExpandedCard(
                effectiveAutoplayEnabled = true,
                trailerPlaybackTarget = FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD,
                isBackdropExpanded = true,
                trailerUrl = url
            )
        )

        // Classic Home: trailer toggle off -> never plays even when expanded
        assertFalse(
            LeanbackCardExpandSpec.shouldPlayTrailerInClassicExpandedCard(
                focusedPosterBackdropExpandEnabled = true,
                focusedPosterBackdropTrailerEnabled = false,
                isBackdropExpanded = true,
                trailerUrl = url
            )
        )
        // Classic Home: both expand and trailer enabled -> plays
        assertTrue(
            LeanbackCardExpandSpec.shouldPlayTrailerInClassicExpandedCard(
                focusedPosterBackdropExpandEnabled = true,
                focusedPosterBackdropTrailerEnabled = true,
                isBackdropExpanded = true,
                trailerUrl = url
            )
        )
    }

    @Test
    fun `artwork resolution prefers landscapePoster when clearlogo override is off and hides overlay until trailer starts`() {
        val res = LeanbackCardExpandSpec.resolveExpandedBackdropArtwork(
            posterUrl = "https://img/poster.jpg",
            backdropUrl = "https://img/backdrop.jpg",
            landscapePosterUrl = "https://img/landscape.jpg",
            rawPosterFallbackUrl = "https://img/raw.jpg",
            alwaysShowLandscapeClearlogo = false,
            isLandscape = false,
            trailerFirstFrameRendered = false
        )

        assertEquals("https://img/landscape.jpg", res.imageUrl)
        assertEquals("https://img/backdrop.jpg", res.fallbackUrl)
        assertTrue(res.usesLandscapePosterArt)
        assertFalse(res.showOverlay)

        val resWithTrailer = LeanbackCardExpandSpec.resolveExpandedBackdropArtwork(
            posterUrl = "https://img/poster.jpg",
            backdropUrl = "https://img/backdrop.jpg",
            landscapePosterUrl = "https://img/landscape.jpg",
            rawPosterFallbackUrl = "https://img/raw.jpg",
            alwaysShowLandscapeClearlogo = false,
            isLandscape = false,
            trailerFirstFrameRendered = true
        )
        assertTrue(resWithTrailer.showOverlay)
    }

    @Test
    fun `artwork resolution uses backdrop and shows overlay when clearlogo override is on or no landscapePoster`() {
        val resOverride = LeanbackCardExpandSpec.resolveExpandedBackdropArtwork(
            posterUrl = "https://img/poster.jpg",
            backdropUrl = "https://img/backdrop.jpg",
            landscapePosterUrl = "https://img/landscape.jpg",
            rawPosterFallbackUrl = null,
            alwaysShowLandscapeClearlogo = true,
            isLandscape = false
        )

        assertEquals("https://img/backdrop.jpg", resOverride.imageUrl)
        assertEquals("https://img/poster.jpg", resOverride.fallbackUrl)
        assertFalse(resOverride.usesLandscapePosterArt)
        assertTrue(resOverride.showOverlay)

        val resFallbackToPoster = LeanbackCardExpandSpec.resolveExpandedBackdropArtwork(
            posterUrl = "https://img/poster.jpg",
            backdropUrl = PLACEHOLDER_IMAGE_URL,
            landscapePosterUrl = null,
            rawPosterFallbackUrl = null,
            alwaysShowLandscapeClearlogo = false,
            isLandscape = false
        )
        assertEquals("https://img/poster.jpg", resFallbackToPoster.imageUrl)
        assertNull(resFallbackToPoster.fallbackUrl)
        assertTrue(resFallbackToPoster.showOverlay)
    }

    @Test
    fun `directional and back keys reset expand timer while select keys do not`() {
        assertTrue(LeanbackCardExpandSpec.shouldResetExpandTimerOnKey(KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(LeanbackCardExpandSpec.shouldResetExpandTimerOnKey(KeyEvent.KEYCODE_DPAD_DOWN))
        assertTrue(LeanbackCardExpandSpec.shouldResetExpandTimerOnKey(KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue(LeanbackCardExpandSpec.shouldResetExpandTimerOnKey(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue(LeanbackCardExpandSpec.shouldResetExpandTimerOnKey(KeyEvent.KEYCODE_BACK))

        assertFalse(LeanbackCardExpandSpec.shouldResetExpandTimerOnKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(LeanbackCardExpandSpec.shouldResetExpandTimerOnKey(KeyEvent.KEYCODE_ENTER))
        assertFalse(LeanbackCardExpandSpec.shouldResetExpandTimerOnKey(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertFalse(LeanbackCardExpandSpec.shouldResetExpandTimerOnKey(KeyEvent.KEYCODE_MENU))
    }

    @Test
    fun `formatExpandedMetaTokens formats series seasons year range and optional imdb rating`() {
        val seriesTokens = LeanbackCardExpandSpec.formatExpandedMetaTokens(
            apiType = "series",
            contentType = ContentType.SERIES,
            genres = listOf("Drama", "Sci-Fi"),
            seasonCount = 4,
            releaseInfo = "2019–2024",
            imdbRating = 8.74f,
            showImdbRatings = true
        )
        assertEquals(listOf("Series", "Drama", "4 seasons", "2019–2024", "8.7"), seriesTokens)

        val movieNoRating = LeanbackCardExpandSpec.formatExpandedMetaTokens(
            apiType = "movie",
            contentType = ContentType.MOVIE,
            genres = listOf("Action"),
            seasonCount = null,
            releaseInfo = "Released 2025-05-01",
            imdbRating = 7.9f,
            showImdbRatings = false
        )
        assertEquals(listOf("Movie", "Action", "2025"), movieNoRating)
    }

    @Test
    fun `resolveEffectiveCardDimensionPx avoids stale pre-layout measured width`() {
        // Explicit animated width always wins
        assertEquals(
            210,
            LeanbackCardExpandSpec.resolveEffectiveCardDimensionPx(
                explicitPx = 210,
                layoutParamPx = 105,
                measuredPx = 336,
                isLayoutRequested = true,
                fallbackPx = 105
            )
        )
        // After LayoutParams shrinks to 105 while View.getWidth() is still stale 336 before layout
        assertEquals(
            105,
            LeanbackCardExpandSpec.resolveEffectiveCardDimensionPx(
                explicitPx = null,
                layoutParamPx = 105,
                measuredPx = 336,
                isLayoutRequested = true,
                fallbackPx = 105
            )
        )
        // Once laid out (isLayoutRequested == false), measuredPx is authoritative
        assertEquals(
            105,
            LeanbackCardExpandSpec.resolveEffectiveCardDimensionPx(
                explicitPx = null,
                layoutParamPx = -1,
                measuredPx = 105,
                isLayoutRequested = false,
                fallbackPx = 126
            )
        )
        // Falls back cleanly before first measure
        assertEquals(
            126,
            LeanbackCardExpandSpec.resolveEffectiveCardDimensionPx(
                explicitPx = null,
                layoutParamPx = 0,
                measuredPx = 0,
                isLayoutRequested = false,
                fallbackPx = 126
            )
        )
    }

    @Test
    fun `shouldRetainRunningWidthAnimation prevents cancelling active tween for same target`() {
        assertTrue(
            LeanbackCardExpandSpec.shouldRetainRunningWidthAnimation(
                isRunning = true,
                animatingTargetWidthPx = 336,
                desiredTargetWidthPx = 336
            )
        )
        assertFalse(
            LeanbackCardExpandSpec.shouldRetainRunningWidthAnimation(
                isRunning = true,
                animatingTargetWidthPx = 336,
                desiredTargetWidthPx = 126
            )
        )
        assertFalse(
            LeanbackCardExpandSpec.shouldRetainRunningWidthAnimation(
                isRunning = false,
                animatingTargetWidthPx = 336,
                desiredTargetWidthPx = 336
            )
        )
    }

    @Test
    fun `shouldAnimateCollapseOnKey animates single horizontal presses and snaps holds or vertical exits`() {
        assertTrue(LeanbackCardExpandSpec.shouldAnimateCollapseOnKey(KeyEvent.KEYCODE_DPAD_RIGHT, 0, true))
        assertTrue(LeanbackCardExpandSpec.shouldAnimateCollapseOnKey(KeyEvent.KEYCODE_DPAD_LEFT, 0, true))

        assertFalse(LeanbackCardExpandSpec.shouldAnimateCollapseOnKey(KeyEvent.KEYCODE_DPAD_RIGHT, 1, true))
        assertFalse(LeanbackCardExpandSpec.shouldAnimateCollapseOnKey(KeyEvent.KEYCODE_DPAD_LEFT, 0, false))
        assertFalse(LeanbackCardExpandSpec.shouldAnimateCollapseOnKey(KeyEvent.KEYCODE_DPAD_UP, 0, true))
        assertFalse(LeanbackCardExpandSpec.shouldAnimateCollapseOnKey(KeyEvent.KEYCODE_DPAD_DOWN, 0, true))
        assertFalse(LeanbackCardExpandSpec.shouldAnimateCollapseOnKey(KeyEvent.KEYCODE_BACK, 0, true))
    }

    @Test
    fun `expand and collapse durations provide visible smooth transitions`() {
        assertEquals(280L, LeanbackCardExpandSpec.CARD_EXPAND_ANIMATION_DURATION_MS)
        assertEquals(200L, LeanbackCardExpandSpec.CARD_COLLAPSE_ANIMATION_DURATION_MS)
        assertEquals(220L, LeanbackCardExpandSpec.CARD_COLLAPSE_SLIDE_DURATION_MS)
        assertEquals(180L, LeanbackCardExpandSpec.RING_COLLAPSE_MORPH_DURATION_MS)
        assertEquals(180L, LeanbackCardExpandSpec.BACKDROP_FADE_IN_DURATION_MS)
    }

    @Test
    fun `computeSiblingFlipDeltaPx keeps sibling visually stationary across collapse layout`() {
        // LTR: sibling sat at left=348 before collapse, layout shifted it to left=173 -> +175f compensation
        assertEquals(
            175f,
            LeanbackCardExpandSpec.computeSiblingFlipDeltaPx(
                oldLeftPx = 348,
                oldTranslationXPx = 0f,
                newLeftPx = 173
            ),
            0.001f
        )
        // RTL: sibling sat at left=120 before collapse, layout shifted it to left=295 -> -175f compensation
        assertEquals(
            -175f,
            LeanbackCardExpandSpec.computeSiblingFlipDeltaPx(
                oldLeftPx = 120,
                oldTranslationXPx = 0f,
                newLeftPx = 295
            ),
            0.001f
        )
        // Preserves any in-flight GPU translation offset during chained transitions
        assertEquals(
            100f,
            LeanbackCardExpandSpec.computeSiblingFlipDeltaPx(
                oldLeftPx = 200,
                oldTranslationXPx = 50f,
                newLeftPx = 150
            ),
            0.001f
        )
    }

    @Test
    fun `shouldUseNavigationCollapseTransition activates only for single animated horizontal steps away from expanded card`() {
        assertTrue(
            LeanbackCardExpandSpec.shouldUseNavigationCollapseTransition(
                currentPos = 2,
                targetPos = 1,
                hasExpandedOrAnimatingCard = true,
                repeatCount = 0,
                animationsEnabled = true
            )
        )
        assertTrue(
            LeanbackCardExpandSpec.shouldUseNavigationCollapseTransition(
                currentPos = 2,
                targetPos = 3,
                hasExpandedOrAnimatingCard = true,
                repeatCount = 0,
                animationsEnabled = true
            )
        )
        // Staying on the same item uses in-place collapse instead of navigation transition
        assertFalse(
            LeanbackCardExpandSpec.shouldUseNavigationCollapseTransition(
                currentPos = 0,
                targetPos = 0,
                hasExpandedOrAnimatingCard = true,
                repeatCount = 0,
                animationsEnabled = true
            )
        )
        // Holding key, no expanded card, or animations disabled -> bypass FLIP transition
        assertFalse(
            LeanbackCardExpandSpec.shouldUseNavigationCollapseTransition(
                currentPos = 2,
                targetPos = 1,
                hasExpandedOrAnimatingCard = true,
                repeatCount = 1,
                animationsEnabled = true
            )
        )
        assertFalse(
            LeanbackCardExpandSpec.shouldUseNavigationCollapseTransition(
                currentPos = 2,
                targetPos = 1,
                hasExpandedOrAnimatingCard = false,
                repeatCount = 0,
                animationsEnabled = true
            )
        )
        assertFalse(
            LeanbackCardExpandSpec.shouldUseNavigationCollapseTransition(
                currentPos = 2,
                targetPos = 1,
                hasExpandedOrAnimatingCard = true,
                repeatCount = 0,
                animationsEnabled = false
            )
        )
    }
}
