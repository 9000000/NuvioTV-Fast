package com.nuvio.tv.domain.model

enum class AmbientDisplayMode(val storageValue: String) {
    BACKDROP("backdrop"),
    TRAILER("trailer");

    companion object {
        fun fromStorage(raw: String?): AmbientDisplayMode =
            entries.firstOrNull { it.storageValue.equals(raw, ignoreCase = true) }
                ?: AmbientModeSettings.DEFAULT_DISPLAY_MODE
    }
}

data class AmbientModeSettings(
    val enabled: Boolean = DEFAULT_ENABLED,
    val timeoutMinutes: Int = DEFAULT_TIMEOUT_MINUTES,
    val catalogKeys: List<String> = emptyList(),
    val displayMode: AmbientDisplayMode = DEFAULT_DISPLAY_MODE,
    val trailerAudioEnabled: Boolean = DEFAULT_TRAILER_AUDIO_ENABLED
) {
    companion object {
        const val DEFAULT_ENABLED = true
        const val DEFAULT_TIMEOUT_MINUTES = 1
        val DEFAULT_DISPLAY_MODE = AmbientDisplayMode.TRAILER
        const val DEFAULT_TRAILER_AUDIO_ENABLED = true
        const val MAX_CATALOGS = 3
        val TIMEOUT_OPTIONS_MINUTES = listOf(1, 2, 5, 10, 15, 30)
    }
}
