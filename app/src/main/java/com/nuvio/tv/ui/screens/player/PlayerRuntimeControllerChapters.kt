package com.nuvio.tv.ui.screens.player

import androidx.media3.exoplayer.SeekParameters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** mpv lists the chapters itself; they come with the file, so once read for a stream they are kept. */
internal fun PlayerRuntimeController.refreshMpvChapters(view: NuvioMpvSurfaceView) {
    if (mpvChaptersStreamUrl == currentStreamUrl && _uiState.value.chapters.isNotEmpty()) return
    val chapters = PlayerChapters.normalize(view.readChapters())
    if (chapters.isEmpty()) return
    mpvChaptersStreamUrl = currentStreamUrl
    _uiState.update { it.copy(chapters = chapters) }
    applyChapterSkipIntervals(chapters)
}

/**
 * Exact on both engines: a keyframe seek can land before the chapter start, and "next" would then
 * keep returning to the same chapter. On mpv, setting the position seeks to a keyframe when the
 * file was loaded with `hr-seek=no` (the Hi10P software fallback), so chapters ask for an exact seek.
 */
internal fun PlayerRuntimeController.seekToChapter(startMs: Long) {
    if (_playbackTimeline.value.isLive) return
    pendingPreviewSeekPosition = null
    seekPlaybackTo(startMs, SeekParameters.EXACT, exact = true)
    updatePlaybackTimeline(currentPosition = startMs)
    scheduleProgressSyncAfterSeek()
    if (_uiState.value.showControls) {
        showControlsTemporarily()
    } else {
        showSeekOverlayTemporarily()
    }
}

internal fun PlayerRuntimeController.showChaptersPanel() {
    if (_uiState.value.chapters.isEmpty()) return
    hideControlsJob?.cancel()
    _uiState.update {
        it.copy(
            showChaptersPanel = true,
            showControls = true,
            showAudioOverlay = false,
            showSubtitleOverlay = false,
            showSubtitleStylePanel = false,
            showSubtitleTimingDialog = false,
            showSpeedDialog = false,
            showMoreDialog = false
        )
    }
}

internal fun PlayerRuntimeController.dismissChaptersPanel() {
    _uiState.update { it.copy(showChaptersPanel = false) }
    scheduleHideControls()
}

/**
 * ExoPlayer does not read Matroska chapters, so they are read from the file once its first frame
 * is shown (on the first READY when tunneled, which never reports a rendered frame), when the
 * extra requests no longer compete with starting playback.
 *
 * Chapters are always loaded for skip intro detection even when the chapters UI is disabled.
 * The [exoChaptersEnabled] setting only controls whether chapters appear in the UI panel.
 */
internal fun PlayerRuntimeController.loadExoChapters() {
    exoChapterLoadJob?.cancel()
    if (isUsingMpvEngine() || _playbackTimeline.value.isLive) return
    val url = currentStreamUrl
    val headers = currentHeaders
    exoChapterLoadJob = scope.launch {
        val chapters = MatroskaChapterLoader.load(url, headers)
        if (chapters.isEmpty() || isUsingMpvEngine() || currentStreamUrl != url) return@launch
        val showInUi = playerSettingsDataStore.playerSettings.first().exoChaptersEnabled
        _uiState.update { it.copy(chapters = if (showInUi) chapters else emptyList()) }
        applyChapterSkipIntervals(chapters)
    }
}

/**
 * Merge chapter-derived skip intervals with existing API intervals.
 * API intervals have priority per category (opening/ending/recap).
 * Chapter intervals fill in categories that the API did not provide.
 */
internal fun PlayerRuntimeController.applyChapterSkipIntervals(chapters: List<PlayerChapter>) {
    if (!skipIntroEnabled) return
    val durationMs = _playbackTimeline.value.duration.takeIf { it > 0L } ?: return
    val chapterIntervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
    if (chapterIntervals.isEmpty()) return
    skipIntervals = mergeSkipIntervals(skipIntervals, chapterIntervals)
}
