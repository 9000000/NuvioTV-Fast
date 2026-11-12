package com.nuvio.tv.ui.screens.detail

import android.text.format.DateFormat
import com.nuvio.tv.core.util.parseEpisodeReleaseLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats an episode release value using the locale's long date pattern (e.g.
 * "9 tháng 11, 2026"). Returns an empty string when the value cannot be parsed as a date so
 * callers can hide the date instead of showing raw provider data.
 */
fun formatReleaseDate(isoDate: String): String {
    val releaseDate = parseEpisodeReleaseLocalDate(isoDate) ?: return ""
    val locale = Locale.getDefault()
    val pattern = runCatching { DateFormat.getBestDateTimePattern(locale, "dMMMMy") }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_RELEASE_DATE_PATTERN
    return runCatching { DateTimeFormatter.ofPattern(pattern, locale).format(releaseDate) }
        .getOrDefault("")
}

/**
 * Formats an episode runtime in minutes as "45m" or "1h 5m".
 */
fun formatEpisodeRuntime(runtimeMinutes: Int?): String? {
    if (runtimeMinutes == null || runtimeMinutes <= 0) return null
    val hours = runtimeMinutes / 60
    val minutes = runtimeMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private const val DEFAULT_RELEASE_DATE_PATTERN = "d MMMM, y"
