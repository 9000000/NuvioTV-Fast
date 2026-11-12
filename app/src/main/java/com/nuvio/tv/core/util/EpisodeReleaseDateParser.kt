package com.nuvio.tv.core.util

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ISO_DATE_PATTERN = Regex("(?<!\\d)\\d{4}-\\d{2}-\\d{2}(?!\\d)")
private val YEAR_FIRST_SLASH_DATE_PATTERN = Regex("(?<!\\d)\\d{4}/\\d{1,2}/\\d{1,2}(?!\\d)")
private val DAY_FIRST_DATE_PATTERN = Regex("(?<!\\d)\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4}(?!\\d)")

private val DAY_FIRST_DATE_FORMATTER = DateTimeFormatter.ofPattern("d/M/uuuu")
private val DAY_FIRST_DASH_DATE_FORMATTER = DateTimeFormatter.ofPattern("d-M-uuuu")
private val DAY_FIRST_DOT_DATE_FORMATTER = DateTimeFormatter.ofPattern("d.M.uuuu")
private val YEAR_FIRST_SLASH_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu/M/d")
private val YEAR_FIRST_DASH_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-M-d")
private val COMPACT_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd")
private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("uuuu-M-d H:mm:ss")
private val VIETNAMESE_DATE_FORMATTER =
    DateTimeFormatter.ofPattern("d 'tháng' M, uuuu", Locale.forLanguageTag("vi"))

private val MONTH_NAME_DATE_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH)
)

private val NON_ISO_DATE_FORMATTERS = listOf(
    DAY_FIRST_DATE_FORMATTER,
    DAY_FIRST_DASH_DATE_FORMATTER,
    DAY_FIRST_DOT_DATE_FORMATTER,
    YEAR_FIRST_SLASH_DATE_FORMATTER,
    YEAR_FIRST_DASH_DATE_FORMATTER,
    COMPACT_DATE_FORMATTER,
    DATE_TIME_FORMATTER,
    VIETNAMESE_DATE_FORMATTER
) + MONTH_NAME_DATE_FORMATTERS

/**
 * Converts timestamped episode releases into the viewer's local calendar date. Plain dates have
 * no timezone information and are preserved as supplied by the provider.
 *
 * Add-ons are inconsistent here: most send ISO dates or timestamps, but some send day-first
 * strings, compact dates, or epoch values. All of those are accepted so the UI does not silently
 * lose the air date.
 */
internal fun parseEpisodeReleaseLocalDate(
    raw: String?,
    zoneId: ZoneId = ZoneId.systemDefault()
): LocalDate? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    return runCatching { LocalDate.parse(value) }.getOrNull()
        ?: parseExplicitReleaseInstant(value)?.atZone(zoneId)?.toLocalDate()
        ?: runCatching { LocalDateTime.parse(value).toLocalDate() }.getOrNull()
        ?: parseEpochReleaseDate(value, zoneId)
        ?: parseNonIsoReleaseDate(value)
        ?: parseEmbeddedReleaseDate(value)
}

/**
 * Returns whether a known release has been reached. Zoned timestamps use their exact instant,
 * while date-only values use UTC midnight. A local timestamp without a zone is interpreted in
 * the viewer's timezone.
 */
internal fun isEpisodeReleaseAired(
    raw: String?,
    clock: Clock = Clock.systemDefaultZone()
): Boolean? {
    val releaseInstant = parseEpisodeReleaseInstant(raw, clock.zone) ?: return null
    return !releaseInstant.isAfter(clock.instant())
}

internal fun parseEpisodeReleaseInstant(
    raw: String?,
    zoneId: ZoneId = ZoneId.systemDefault()
): Instant? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    return parseExplicitReleaseInstant(value)
        ?: runCatching { LocalDateTime.parse(value).atZone(zoneId).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant() }.getOrNull()
        ?: parseNonIsoReleaseDate(value)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()
        ?: parseEmbeddedReleaseDate(value)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()
}

/**
 * Uses the add-on release value while it carries a parseable date. Add-ons frequently omit
 * episode dates entirely, so TMDB's broadcaster date is used as a fallback instead of dropping
 * the date from the UI. When the user explicitly opts into TMDB release dates, TMDB wins.
 */
internal fun selectEpisodeReleaseValue(
    addonReleased: String?,
    tmdbAirDate: String?,
    useTmdbReleaseDates: Boolean
): String? {
    val addonValue = addonReleased?.trim()?.takeIf { it.isNotEmpty() }
    val tmdbValue = tmdbAirDate?.trim()?.takeIf { it.isNotEmpty() }
    if (useTmdbReleaseDates) return tmdbValue ?: addonValue
    if (addonValue != null && parseEpisodeReleaseLocalDate(addonValue) != null) return addonValue
    return tmdbValue ?: addonValue
}

private fun parseExplicitReleaseInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(value).toInstant() }.getOrNull()

private fun parseEpochReleaseDate(value: String, zoneId: ZoneId): LocalDate? {
    if (!value.all(Char::isDigit)) return null
    return when (value.length) {
        10 -> runCatching {
            Instant.ofEpochSecond(value.toLong()).atZone(zoneId).toLocalDate()
        }.getOrNull()

        13 -> runCatching {
            Instant.ofEpochMilli(value.toLong()).atZone(zoneId).toLocalDate()
        }.getOrNull()

        else -> null
    }
}

private fun parseNonIsoReleaseDate(value: String): LocalDate? {
    NON_ISO_DATE_FORMATTERS.forEach { formatter ->
        runCatching { LocalDate.parse(value, formatter) }.getOrNull()?.let { return it }
    }
    return null
}

private fun parseEmbeddedReleaseDate(value: String): LocalDate? {
    ISO_DATE_PATTERN.find(value)?.value?.let { datePortion ->
        runCatching { LocalDate.parse(datePortion) }.getOrNull()?.let { return it }
    }
    YEAR_FIRST_SLASH_DATE_PATTERN.find(value)?.value?.let { datePortion ->
        runCatching { LocalDate.parse(datePortion, YEAR_FIRST_SLASH_DATE_FORMATTER) }
            .getOrNull()
            ?.let { return it }
    }
    DAY_FIRST_DATE_PATTERN.find(value)?.value?.let { datePortion ->
        val normalized = datePortion.replace('.', '/').replace('-', '/')
        runCatching { LocalDate.parse(normalized, DAY_FIRST_DATE_FORMATTER) }
            .getOrNull()
            ?.let { return it }
    }
    return null
}
