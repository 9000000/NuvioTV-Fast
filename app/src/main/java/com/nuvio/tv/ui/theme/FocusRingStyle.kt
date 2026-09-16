package com.nuvio.tv.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.tv.domain.model.PosterBorderStyle

@Immutable
class NuvioFocusRingStyle internal constructor(
    val solidColor: Color,
    private val gradientColors: List<Color>
) {
    private val fullBrush = createThemeBrush(gradientColors)

    fun brush(alpha: Float = 1f): Brush {
        val normalizedAlpha = alpha.coerceIn(0f, 1f)
        if (normalizedAlpha == 1f) return fullBrush
        return createThemeBrush(gradientColors.map { color ->
            color.copy(alpha = color.alpha * normalizedAlpha)
        })
    }

    fun border(width: Dp, alpha: Float = 1f): BorderStroke {
        return BorderStroke(width, brush(alpha))
    }
}

fun ThemeColorPalette.accentBrush(): Brush = createThemeBrush(accentGradient)

internal fun createThemeBrush(colors: List<Color>): Brush {
    return if (colors.size == 1) {
        SolidColor(colors.first())
    } else {
        Brush.linearGradient(colors)
    }
}

internal fun createFocusRingStyle(palette: ThemeColorPalette): NuvioFocusRingStyle {
    return NuvioFocusRingStyle(
        solidColor = palette.focusRing,
        gradientColors = palette.focusRingGradient
    )
}

val PosterRainbowColors: List<Color> = listOf(
    Color(0xFFFF2A55), // Đỏ
    Color(0xFFFF7E1D), // Cam
    Color(0xFFFFD000), // Vàng
    Color(0xFF05DF72), // Lục
    Color(0xFF00D4FF), // Lam
    Color(0xFF2B7FFF), // Chàm
    Color(0xFFB026FF), // Tím
    Color(0xFFFF2A55)  // Đỏ khép vòng
)

val PosterRainbowBrush: Brush = Brush.linearGradient(PosterRainbowColors)

val PosterFocusedBorderWidth: Dp = 4.dp

val PosterFocusRingStyle: NuvioFocusRingStyle = NuvioFocusRingStyle(
    solidColor = Color(0xFFFF2A55),
    gradientColors = PosterRainbowColors
)

/** A transparent, zero-width style used when the user selects [PosterBorderStyle.NONE]. */
private val NoBorderStyle: NuvioFocusRingStyle = NuvioFocusRingStyle(
    solidColor = Color.Transparent,
    gradientColors = listOf(Color.Transparent)
)

/**
 * Maps a [PosterBorderStyle] preference to a [NuvioFocusRingStyle].
 *
 * [palette] is required for [PosterBorderStyle.THEME] so the border matches the active theme.
 * All other styles are palette-independent.
 *
 * Never returns null — [PosterBorderStyle.NONE] returns a transparent style so call sites
 * do not need null checks.
 */
fun createPosterFocusRingStyle(
    style: PosterBorderStyle,
    palette: ThemeColorPalette
): NuvioFocusRingStyle = when (style) {
    PosterBorderStyle.THEME -> createFocusRingStyle(palette) // follows theme accent (dev branch default)
    PosterBorderStyle.RAINBOW -> PosterFocusRingStyle
    PosterBorderStyle.SOLID_WHITE -> NuvioFocusRingStyle(
        solidColor = Color.White,
        gradientColors = listOf(Color.White)
    )
    PosterBorderStyle.SOLID_RED -> NuvioFocusRingStyle(
        solidColor = Color(0xFFFF2A55),
        gradientColors = listOf(Color(0xFFFF2A55))
    )
    PosterBorderStyle.SOLID_BLUE -> NuvioFocusRingStyle(
        solidColor = Color(0xFF2B7FFF),
        gradientColors = listOf(Color(0xFF2B7FFF))
    )
    PosterBorderStyle.SOLID_GOLD -> NuvioFocusRingStyle(
        solidColor = Color(0xFFFFD000),
        gradientColors = listOf(Color(0xFFFFD000))
    )
    PosterBorderStyle.NONE -> NoBorderStyle
}
