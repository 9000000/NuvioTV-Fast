package com.nuvio.tv.domain.model

enum class AmbientDisplayMode(val storageValue: String) {
    BACKDROP("backdrop"),
    TRAILER("trailer");

    companion object {
        fun fromStorage(raw: String?): AmbientDisplayMode =
            entries.firstOrNull { it.storageValue.equals(raw, ignoreCase = true) } ?: BACKDROP
    }
}

data class AmbientModeSettings(
    val enabled: Boolean = true,
    val timeoutMinutes: Int = DEFAULT_TIMEOUT_MINUTES,
    val catalogKeys: List<String> = emptyList(),
    val displayMode: AmbientDisplayMode = AmbientDisplayMode.BACKDROP,
    val trailerAudioEnabled: Boolean = false
) {
    companion object {
        const val DEFAULT_TIMEOUT_MINUTES = 5
        const val MAX_CATALOGS = 3
        val TIMEOUT_OPTIONS_MINUTES = listOf(1, 2, 5, 10, 15, 30)
    }
}
