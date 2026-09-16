package com.nuvio.tv.domain.model

/**
 * Controls the visual style of the focused poster border / focus ring.
 *
 * [THEME]        – Follows the active app theme accent gradient (default, like the original dev branch).
 * [RAINBOW]      – Multi-color rainbow gradient.
 * [SOLID_WHITE]  – Plain white solid border.
 * [SOLID_RED]    – Vibrant red solid border.
 * [SOLID_BLUE]   – Electric blue solid border.
 * [SOLID_GOLD]   – Gold / amber solid border.
 * [NONE]         – No border on focused poster (transparent).
 */
enum class PosterBorderStyle {
    THEME,
    RAINBOW,
    SOLID_WHITE,
    SOLID_RED,
    SOLID_BLUE,
    SOLID_GOLD,
    NONE;

    companion object {
        val Default: PosterBorderStyle = THEME
    }
}
