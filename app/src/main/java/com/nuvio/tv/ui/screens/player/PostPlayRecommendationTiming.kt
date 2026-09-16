package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.NextEpisodeThresholdMode
import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.domain.model.ContentType

private const val MOVIE_RECOMMENDATION_PREFETCH_LEAD_PERCENT = 5

internal fun postPlayRecommendationPrefetchProgress(
    contentType: String?,
    movieThresholdPercent: Int,
    durationMs: Long = 0L,
    skipIntervals: List<SkipInterval> = emptyList()
): Float {
    if (resolvePostPlayContentType(contentType) != ContentType.MOVIE) {
        return POST_PLAY_RECOMMENDATION_PREFETCH_PROGRESS
    }
    val threshold = movieThresholdPercent.coerceIn(
        PlayerSettings.MIN_POST_PLAY_MOVIE_THRESHOLD_PERCENT,
        PlayerSettings.MAX_POST_PLAY_MOVIE_THRESHOLD_PERCENT
    )
    val triggerProgress = if (durationMs > 0L) {
        movieRecommendationTriggerPositionMs(durationMs, threshold, skipIntervals).toFloat() / durationMs
    } else {
        threshold / 100f
    }
    return (triggerProgress - MOVIE_RECOMMENDATION_PREFETCH_LEAD_PERCENT / 100f).coerceAtLeast(0f)
}

internal fun shouldShowPostPlayRecommendation(
    contentType: String?,
    positionMs: Long,
    durationMs: Long,
    skipIntervals: List<SkipInterval>,
    movieThresholdPercent: Int,
    episodeThresholdMode: NextEpisodeThresholdMode,
    episodeThresholdPercent: Float,
    episodeThresholdMinutesBeforeEnd: Float
): Boolean {
    return when (resolvePostPlayContentType(contentType)) {
        ContentType.MOVIE -> shouldShowMovieRecommendation(
            positionMs = positionMs,
            durationMs = durationMs,
            thresholdPercent = movieThresholdPercent,
            skipIntervals = skipIntervals
        )
        ContentType.SERIES -> PlayerNextEpisodeRules.shouldShowNextEpisodeCard(
            positionMs = positionMs,
            durationMs = durationMs,
            skipIntervals = skipIntervals,
            thresholdMode = episodeThresholdMode,
            thresholdPercent = episodeThresholdPercent,
            thresholdMinutesBeforeEnd = episodeThresholdMinutesBeforeEnd
        )
        else -> false
    }
}

private fun shouldShowMovieRecommendation(
    positionMs: Long,
    durationMs: Long,
    thresholdPercent: Int,
    skipIntervals: List<SkipInterval>
): Boolean {
    if (durationMs <= 0L) return false
    val position = positionMs.coerceIn(0L, durationMs)
    return position >= movieRecommendationTriggerPositionMs(durationMs, thresholdPercent, skipIntervals)
}

private fun movieRecommendationTriggerPositionMs(
    durationMs: Long,
    thresholdPercent: Int,
    skipIntervals: List<SkipInterval>
): Long {
    val threshold = thresholdPercent.coerceIn(
        PlayerSettings.MIN_POST_PLAY_MOVIE_THRESHOLD_PERCENT,
        PlayerSettings.MAX_POST_PLAY_MOVIE_THRESHOLD_PERCENT
    )
    val fallbackPositionMs = kotlin.math.ceil(durationMs * (threshold / 100.0)).toLong()
    val credits = skipIntervals.filter {
        it.type == "movie-credits" && it.startTime.isFinite() && it.endTime.isFinite() &&
            it.startTime >= 0.0 && it.endTime > it.startTime &&
            it.startTime * 1_000.0 < durationMs &&
            it.endTime * 1_000.0 <= durationMs + PlayerNextEpisodeRules.END_OF_VIDEO_EPSILON_MS
    }
    if (credits.isEmpty()) return fallbackPositionMs

    val latestCreditsEndMs = (credits.maxOf { it.endTime } * 1_000.0).toLong()
    val postCreditsGapMs = durationMs - latestCreditsEndMs
    val userThresholdMs = durationMs - fallbackPositionMs
    // Match episode endings: a substantial tail after credits uses the user's end threshold.
    return if (postCreditsGapMs > userThresholdMs) {
        fallbackPositionMs
    } else {
        (credits.minOf { it.startTime } * 1_000.0).toLong()
    }
}
