package com.nuvio.tv.core.tts

/**
 * Platform-neutral representation of a raw voice entry reported by Android `TextToSpeech`.
 */
data class RawSystemVoice(
    val name: String,
    val localeTag: String,
    val language: String,
    val country: String,
    val requiresNetwork: Boolean,
)

/**
 * Pure Kotlin voice filtering, sorting, and fallback selection for Google Offline TTS.
 */
object GoogleOfflineTtsVoiceSelector {
    const val DEFAULT_LANGUAGE = "vi"
    private const val VIETNAMESE_LANGUAGE = "vi"
    private const val BADGE_OFFLINE = "(Offline)"
    private const val BADGE_ONLINE = "(Online)"
    private const val LOCAL_SUFFIX = "-local"
    private const val NETWORK_SUFFIX = "-network"

    /**
     * Resolves the TTS voice language code from subtitle language preferences, falling back to
     * Vietnamese ([DEFAULT_LANGUAGE]) when no explicit language is configured.
     */
    fun resolvePreferredLanguageCode(
        preferredSubtitleLanguage: String?,
        secondarySubtitleLanguage: String? = null,
    ): String {
        return normalizeLanguagePreference(preferredSubtitleLanguage)
            ?: normalizeLanguagePreference(secondarySubtitleLanguage)
            ?: DEFAULT_LANGUAGE
    }

    private fun normalizeLanguagePreference(code: String?): String? {
        val trimmed = code?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        if (lower == "none" || lower == "off" || lower == "forced" || lower == "und" || lower == "device") {
            return null
        }
        return trimmed.replace('_', '-')
    }

    /**
     * Filters [rawVoices] to offline-capable voices (unless [allowNetworkFallback] is `true`),
     * optionally restricts to voices matching [preferredLanguageCode], formats clean display
     * labels, and sorts preferred/exact-region matches first, then offline before online, then
     * alphabetically by display name.
     */
    fun selectAvailableVoices(
        rawVoices: List<RawSystemVoice>,
        allowNetworkFallback: Boolean,
        preferredLanguageCode: String? = null,
    ): List<GoogleTtsVoiceInfo> {
        val targetLang = preferredLanguageCode?.trim()?.takeIf { it.isNotBlank() }?.replace('_', '-')
        return rawVoices
            .asSequence()
            .filter { it.name.isNotBlank() }
            .filter { allowNetworkFallback || !it.requiresNetwork }
            .filter { raw ->
                targetLang == null || matchesLanguage(
                    localeTag = resolveLocaleTag(raw),
                    language = raw.language,
                    targetLanguageCode = targetLang,
                )
            }
            .map { raw ->
                val normalizedLocale = resolveLocaleTag(raw)
                GoogleTtsVoiceInfo(
                    name = raw.name,
                    displayName = formatVoiceDisplayName(raw, normalizedLocale),
                    localeTag = normalizedLocale,
                    isOffline = !raw.requiresNetwork,
                )
            }
            .sortedWith(
                compareByDescending<GoogleTtsVoiceInfo> { voice ->
                    if (targetLang != null) {
                        voice.localeTag.equals(targetLang, ignoreCase = true)
                    } else {
                        isVietnameseLocale(voice.localeTag)
                    }
                }
                    .thenByDescending { it.isOffline }
                    .thenBy { it.displayName.lowercase() }
                    .thenBy { it.name },
            )
            .distinctBy { it.name }
            .toList()
    }

    /**
     * Picks the best available voice from [voices], checking in priority order:
     * exact [preferredVoiceName] match -> first offline voice matching [preferredLanguageCode]
     * (or Vietnamese) -> first offline voice -> first available voice.
     */
    fun pickBestVoice(
        voices: List<GoogleTtsVoiceInfo>,
        preferredVoiceName: String?,
        preferredLanguageCode: String? = null,
    ): GoogleTtsVoiceInfo? {
        if (voices.isEmpty()) return null

        val preferred = preferredVoiceName?.takeIf { it.isNotBlank() }
        if (preferred != null) {
            voices.firstOrNull { it.name == preferred }?.let { return it }
        }

        val targetLang = preferredLanguageCode?.trim()?.takeIf { it.isNotBlank() } ?: VIETNAMESE_LANGUAGE
        voices.firstOrNull {
            matchesLanguage(localeTag = it.localeTag, targetLanguageCode = targetLang) && it.isOffline
        }?.let { return it }
        voices.firstOrNull { it.isOffline }?.let { return it }
        return voices.firstOrNull()
    }

    /** Returns `true` when [localeTag] or [language] shares the base language of [targetLanguageCode]. */
    fun matchesLanguage(
        localeTag: String,
        language: String = "",
        targetLanguageCode: String,
    ): Boolean {
        val targetBase = extractBaseLanguage(targetLanguageCode) ?: return false
        val tagBase = extractBaseLanguage(localeTag)
        val langBase = extractBaseLanguage(language)
        return targetBase.equals(tagBase, ignoreCase = true) ||
            targetBase.equals(langBase, ignoreCase = true)
    }

    /** Returns `true` when [localeTag] or [language] represents a Vietnamese locale (`vi` / `vi-VN`). */
    fun isVietnameseLocale(localeTag: String, language: String = ""): Boolean =
        matchesLanguage(localeTag = localeTag, language = language, targetLanguageCode = VIETNAMESE_LANGUAGE)

    private fun extractBaseLanguage(code: String): String? {
        val normalized = code.trim().replace('_', '-')
        if (normalized.isEmpty()) return null
        val base = normalized.substringBefore('-').lowercase()
        return base.takeIf { it.isNotEmpty() && it != "und" }
    }

    private fun resolveLocaleTag(raw: RawSystemVoice): String {
        val tag = raw.localeTag.trim()
        if (tag.isNotBlank() && !tag.equals("und", ignoreCase = true)) {
            return tag
        }
        val lang = raw.language.trim()
        val country = raw.country.trim()
        return when {
            lang.isNotBlank() && country.isNotBlank() -> "$lang-$country"
            lang.isNotBlank() -> lang
            else -> "und"
        }
    }

    private fun formatVoiceDisplayName(raw: RawSystemVoice, localeTag: String): String {
        val badge = if (raw.requiresNetwork) BADGE_ONLINE else BADGE_OFFLINE
        val withoutModeSuffix = raw.name.trim()
            .removeSuffixIgnoreCase(LOCAL_SUFFIX)
            .removeSuffixIgnoreCase(NETWORK_SUFFIX)
        val localePrefix = "${localeTag.lowercase()}-x-"
        val fallbackPrefix = if (raw.language.isNotBlank() && raw.country.isNotBlank()) {
            "${raw.language.lowercase()}-${raw.country.lowercase()}-x-"
        } else {
            null
        }

        val variant = when {
            withoutModeSuffix.lowercase().startsWith(localePrefix) ->
                withoutModeSuffix.substring(localePrefix.length)
            fallbackPrefix != null && withoutModeSuffix.lowercase().startsWith(fallbackPrefix) ->
                withoutModeSuffix.substring(fallbackPrefix.length)
            else -> withoutModeSuffix
        }.trim('-', '_', ' ')

        return if (variant.isNotBlank() && !variant.equals(localeTag, ignoreCase = true)) {
            "$localeTag • $variant $badge"
        } else {
            "$localeTag $badge"
        }
    }

    private fun String.removeSuffixIgnoreCase(suffix: String): String =
        if (endsWith(suffix, ignoreCase = true)) {
            substring(0, length - suffix.length)
        } else {
            this
        }
}
