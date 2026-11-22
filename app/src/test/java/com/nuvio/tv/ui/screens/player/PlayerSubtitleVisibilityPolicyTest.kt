package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSubtitleVisibilityPolicyTest {

    @Test
    fun `hides visual subtitles when tts hide option is active regardless of post play`() {
        assertTrue(
            PlayerSubtitleVisibilityPolicy.shouldHideVisualSubtitles(
                ttsHideVisualSubtitles = true,
                postPlayRecommendationVisible = false
            )
        )
        assertTrue(
            PlayerSubtitleVisibilityPolicy.shouldHideVisualSubtitles(
                ttsHideVisualSubtitles = true,
                postPlayRecommendationVisible = true
            )
        )
        assertFalse(
            PlayerSubtitleVisibilityPolicy.shouldShowExoSubtitleView(
                ttsHideVisualSubtitles = true,
                postPlayRecommendationVisible = false
            )
        )
    }

    @Test
    fun `hides visual subtitles during post play even when tts hide is off`() {
        assertTrue(
            PlayerSubtitleVisibilityPolicy.shouldHideVisualSubtitles(
                ttsHideVisualSubtitles = false,
                postPlayRecommendationVisible = true
            )
        )
        assertFalse(
            PlayerSubtitleVisibilityPolicy.shouldShowExoSubtitleView(
                ttsHideVisualSubtitles = false,
                postPlayRecommendationVisible = true
            )
        )
    }

    @Test
    fun `shows exo subtitle view when neither tts hide nor post play is active`() {
        assertFalse(
            PlayerSubtitleVisibilityPolicy.shouldHideVisualSubtitles(
                ttsHideVisualSubtitles = false,
                postPlayRecommendationVisible = false
            )
        )
        assertTrue(
            PlayerSubtitleVisibilityPolicy.shouldShowExoSubtitleView(
                ttsHideVisualSubtitles = false,
                postPlayRecommendationVisible = false
            )
        )
    }

    @Test
    fun `disables libass overlay whenever visual subtitles are hidden`() {
        assertFalse(
            PlayerSubtitleVisibilityPolicy.shouldEnableLibassOverlay(
                useLibass = true,
                hideVisualSubtitles = true
            )
        )
        assertFalse(
            PlayerSubtitleVisibilityPolicy.shouldEnableLibassOverlay(
                useLibass = false,
                hideVisualSubtitles = false
            )
        )
        assertTrue(
            PlayerSubtitleVisibilityPolicy.shouldEnableLibassOverlay(
                useLibass = true,
                hideVisualSubtitles = false
            )
        )
    }

    @Test
    fun `mpv native subtitles stay hidden across reloads and track changes when tts hide is enabled`() {
        assertFalse(
            PlayerSubtitleVisibilityPolicy.shouldShowMpvNativeSubtitles(
                styledSubtitlesActive = false,
                ttsHideSubtitles = true,
                hasSelectedSubtitleTrack = true
            )
        )
        assertFalse(
            PlayerSubtitleVisibilityPolicy.shouldShowMpvNativeSubtitles(
                styledSubtitlesActive = true,
                ttsHideSubtitles = true,
                hasSelectedSubtitleTrack = true
            )
        )
    }

    @Test
    fun `mpv native subtitles stay hidden when styled compose renderer handles the track or no track is selected`() {
        assertFalse(
            PlayerSubtitleVisibilityPolicy.shouldShowMpvNativeSubtitles(
                styledSubtitlesActive = true,
                ttsHideSubtitles = false,
                hasSelectedSubtitleTrack = true
            )
        )
        assertFalse(
            PlayerSubtitleVisibilityPolicy.shouldShowMpvNativeSubtitles(
                styledSubtitlesActive = false,
                ttsHideSubtitles = false,
                hasSelectedSubtitleTrack = false
            )
        )
    }

    @Test
    fun `mpv native subtitles show when track is selected and neither styled renderer nor tts hide is active`() {
        assertTrue(
            PlayerSubtitleVisibilityPolicy.shouldShowMpvNativeSubtitles(
                styledSubtitlesActive = false,
                ttsHideSubtitles = false,
                hasSelectedSubtitleTrack = true
            )
        )
    }
}
