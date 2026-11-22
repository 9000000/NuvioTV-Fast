package com.nuvio.tv.ui.screens.player

/**
 * Pure policy resolving visual subtitle visibility across ExoPlayer, libass overlay,
 * Compose styled subtitles, and MPV so hiding visual subtitles (e.g. TTS hide-subtitles
 * or PostPlay overlay) stays consistent across movie changes, stream switches, track changes,
 * and surface/view recreation.
 */
internal object PlayerSubtitleVisibilityPolicy {

    fun shouldHideVisualSubtitles(
        ttsHideVisualSubtitles: Boolean,
        postPlayRecommendationVisible: Boolean
    ): Boolean = ttsHideVisualSubtitles || postPlayRecommendationVisible

    fun shouldShowExoSubtitleView(
        ttsHideVisualSubtitles: Boolean,
        postPlayRecommendationVisible: Boolean
    ): Boolean = !shouldHideVisualSubtitles(
        ttsHideVisualSubtitles = ttsHideVisualSubtitles,
        postPlayRecommendationVisible = postPlayRecommendationVisible
    )

    fun shouldEnableLibassOverlay(
        useLibass: Boolean,
        hideVisualSubtitles: Boolean
    ): Boolean = useLibass && !hideVisualSubtitles

    fun shouldShowMpvNativeSubtitles(
        styledSubtitlesActive: Boolean,
        ttsHideSubtitles: Boolean,
        hasSelectedSubtitleTrack: Boolean = true
    ): Boolean = hasSelectedSubtitleTrack && !styledSubtitlesActive && !ttsHideSubtitles
}
