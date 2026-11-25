package com.nuvio.tv.ui.screens.player

import androidx.media3.common.text.Cue

internal fun PlayerRuntimeController.presentExoCues(cues: List<Cue>): List<Cue> =
    styledSubtitles.present(cues, canStyleExoSubtitles())

internal fun PlayerRuntimeController.refreshStyledSubtitles() {
    if (isUsingMpvEngine()) {
        syncMpvStyledSubtitles()
        return
    }
    val routed = styledSubtitles.refresh(canStyleExoSubtitles())
    exoSubtitleViewRef?.get()?.setCues(routed)
}

internal fun PlayerRuntimeController.syncMpvStyledSubtitles() {
    val view = mpvView ?: return
    styledSubtitles.showMpvText(view.syncStyledSubtitles(_uiState.value.subtitleStyle.usesStyledRenderer))
    if (!view.isPositionFromRequestedMedia()) {
        subtitleCueTracker.updateTextWithTiming(null, null)
        return
    }
    val subText = view.currentSubtitleText()
    val timingContext = if (_uiState.value.ttsConfig.enabled && !subText.isNullOrBlank()) {
        view.getSubtitleCueTimingContext(
            rawText = subText,
            playbackSpeed = _uiState.value.playbackSpeed
        )
    } else {
        null
    }
    subtitleCueTracker.updateTextWithTiming(subText, timingContext)
}

private fun PlayerRuntimeController.canStyleExoSubtitles(): Boolean =
    _uiState.value.subtitleStyle.usesStyledRenderer &&
        !PlayerSubtitleUtils.isAssOrSsaSelected(_exoPlayer?.currentTracks, activeSidecarSubtitleKey)
