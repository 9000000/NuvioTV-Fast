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
 * Chapters are loaded when the chapters UI is enabled OR skip intro is enabled.
 * The [exoChaptersEnabled] setting controls only the UI panel; skip intro uses chapters
 * independently.
 */
internal fun PlayerRuntimeController.loadExoChapters() {
    exoChapterLoadJob?.cancel()
    if (isUsingMpvEngine() || _playbackTimeline.value.isLive) return
    val url = currentStreamUrl
    val headers = currentHeaders
    exoChapterLoadJob = scope.launch {
        val settings = playerSettingsDataStore.playerSettings.first()
        if (!settings.exoChaptersEnabled && !skipIntroEnabled) return@launch
        val chapters = MatroskaChapterLoader.load(url, headers)
        if (chapters.isEmpty() || isUsingMpvEngine() || currentStreamUrl != url) return@launch
        if (settings.exoChaptersEnabled) {
            _uiState.update { it.copy(chapters = chapters) }
        }
        applyChapterSkipIntervals(chapters)
    }
}

/**
 * Store chapter-derived skip intervals and recompute the merged result.
 * If the duration is not yet known, the chapters are stored and intervals will be
 * computed once [recomputeChapterSkipIntervalsIfNeeded] is called when duration updates.
 */
internal fun PlayerRuntimeController.applyChapterSkipIntervals(chapters: List<PlayerChapter>) {
    if (!skipIntroEnabled) return
    val durationMs = _playbackTimeline.value.duration.takeIf { it > 0L }
    if (durationMs != null) {
        chapterSkipIntervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
    }
    recomputeMergedSkipIntervals()
}

/**
 * Retry chapter skip interval computation when the duration becomes known.
 * Called from the playback tick when the timeline duration changes from 0 to a real value.
 */
internal fun PlayerRuntimeController.recomputeChapterSkipIntervalsIfNeeded() {
    if (chapterSkipIntervals.isNotEmpty()) return
    val chapters = _uiState.value.chapters.takeIf { it.size >= 2 } ?: return
    val durationMs = _playbackTimeline.value.duration.takeIf { it > 0L } ?: return
    chapterSkipIntervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
    if (chapterSkipIntervals.isNotEmpty()) recomputeMergedSkipIntervals()
}
