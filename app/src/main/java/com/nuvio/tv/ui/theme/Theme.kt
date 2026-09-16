package com.nuvio.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.nuvio.tv.domain.model.AppFont
import com.nuvio.tv.domain.model.AppTheme
import com.nuvio.tv.domain.model.CustomThemeColors
import com.nuvio.tv.domain.model.PosterBorderStyle
import com.nuvio.tv.domain.model.SettingsUiStyle

data class NuvioExtendedColors(
    val backgroundElevated: Color,
    val backgroundCard: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val focusRing: Color,
    val focusBackground: Color,
    val rating: Color
)

val LocalNuvioColors = staticCompositionLocalOf {
    NuvioColorScheme(ThemeColors.Ocean)
}

val LocalNuvioExtendedColors = staticCompositionLocalOf {
    NuvioExtendedColors(
        backgroundElevated = Color(0xFF1A1A1A),
        backgroundCard = Color(0xFF242424),
        textSecondary = Color(0xFFB3B3B3),
        textTertiary = Color(0xFF808080),
        focusRing = ThemeColors.Ocean.focusRing,
        focusBackground = ThemeColors.Ocean.focusBackground,
        rating = Color(0xFFFFD700)
    )
}

val LocalNuvioTextStyles = staticCompositionLocalOf { NuvioTextStyles }

val LocalAppTheme = staticCompositionLocalOf { AppTheme.WHITE }

val LocalThemePalette = staticCompositionLocalOf { ThemeColors.White }

val LocalSettingsUiStyle = staticCompositionLocalOf { SettingsUiStyle.CLASSIC }

val LocalNuvioFocusRingStyle = staticCompositionLocalOf {
    createFocusRingStyle(ThemeColors.Ocean)
}

// Poster-specific focus ring, driven by user preference.
// Default mirrors the active theme accent (THEME style).
val LocalPosterFocusRingStyle = staticCompositionLocalOf {
    createFocusRingStyle(ThemeColors.Ocean)
}

val LocalAnimationsEnabled = staticCompositionLocalOf { true }

val LocalNuvioMotionTokens = staticCompositionLocalOf { NuvioMotion.tokens }

val LocalNuvioFocusTokens = staticCompositionLocalOf { NuvioFocus.tokens }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NuvioTheme(
    appTheme: AppTheme = AppTheme.WHITE,
    appFont: AppFont = AppFont.INTER,
    amoledMode: Boolean = false,
    amoledSurfacesMode: Boolean = false,
    settingsUiStyle: SettingsUiStyle = SettingsUiStyle.CLASSIC,
    customThemeColors: CustomThemeColors = CustomThemeColors.Default,
    animationsEnabled: Boolean = true,
    posterBorderStyle: PosterBorderStyle = PosterBorderStyle.Default,
    content: @Composable () -> Unit
) {
    NuvioMotion.animationsEnabled = animationsEnabled
    val palette = androidx.compose.runtime.remember(appTheme, customThemeColors) {
        ThemeColors.getColorPalette(appTheme, customThemeColors)
    }
    val focusRingStyle = createFocusRingStyle(palette)
    val posterFocusRingStyle = androidx.compose.runtime.remember(posterBorderStyle, palette) {
        createPosterFocusRingStyle(posterBorderStyle, palette)
    }
    val colorScheme = NuvioColorScheme(
        palette = palette,
        amoledMode = amoledMode,
        amoledSurfacesMode = amoledSurfacesMode
    )
    val typography = buildNuvioTypography(getFontFamily(appFont))
    val textStyles = buildNuvioTextStyles(typography)

    val materialColorScheme = darkColorScheme(
        primary = colorScheme.Primary,
        onPrimary = colorScheme.OnPrimary,
        secondary = colorScheme.Secondary,
        onSecondary = colorScheme.OnSecondary,
        background = colorScheme.Background,
        surface = colorScheme.Surface,
        surfaceVariant = colorScheme.SurfaceVariant,
        onBackground = colorScheme.TextPrimary,
        onSurface = colorScheme.TextPrimary,
        onSurfaceVariant = colorScheme.TextSecondary,
        error = colorScheme.Error
    )

    val extendedColors = NuvioExtendedColors(
        backgroundElevated = colorScheme.BackgroundElevated,
        backgroundCard = colorScheme.BackgroundCard,
        textSecondary = colorScheme.TextSecondary,
        textTertiary = colorScheme.TextTertiary,
        focusRing = colorScheme.FocusRing,
        focusBackground = colorScheme.FocusBackground,
        rating = colorScheme.Rating
    )

    val motionTokens = if (animationsEnabled) NuvioMotion.enabledTokens else NuvioMotion.disabledTokens
    val focusTokens = if (animationsEnabled) NuvioFocus.enabledTokens else NuvioFocus.disabledTokens

    CompositionLocalProvider(
        LocalNuvioColors provides colorScheme,
        LocalNuvioExtendedColors provides extendedColors,
        LocalNuvioTextStyles provides textStyles,
        LocalAppTheme provides appTheme,
        LocalThemePalette provides palette,
        LocalSettingsUiStyle provides settingsUiStyle,
        LocalNuvioFocusRingStyle provides focusRingStyle,
        LocalPosterFocusRingStyle provides posterFocusRingStyle,
        LocalAnimationsEnabled provides animationsEnabled,
        LocalNuvioMotionTokens provides motionTokens,
        LocalNuvioFocusTokens provides focusTokens
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = typography,
            content = content
        )
    }
}

object NuvioTheme {
    val palette: ThemeColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalThemePalette.current

    val colors: NuvioColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalNuvioColors.current

    val extendedColors: NuvioExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNuvioExtendedColors.current

    val textStyles: NuvioTextStyleTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalNuvioTextStyles.current

    val spacing: NuvioSpacingTokens
        get() = NuvioSpacing.tokens

    val radii: NuvioRadiusTokens
        get() = NuvioRadii.tokens

    val shapes: NuvioShapeTokens
        get() = NuvioShapes.tokens

    val sizes: NuvioSizeTokens
        get() = NuvioSizes.tokens

    val strokes: NuvioStrokeTokens
        get() = NuvioStrokes.tokens

    val elevations: NuvioElevationTokens
        get() = NuvioElevations.tokens

    val effects: NuvioEffectTokens
        get() = NuvioEffects.tokens

    val animationsEnabled: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalAnimationsEnabled.current

    val motion: NuvioMotionTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalNuvioMotionTokens.current

    val focus: NuvioFocusTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalNuvioFocusTokens.current

    val focusRing: NuvioFocusRingStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalNuvioFocusRingStyle.current

    val posterFocusRing: NuvioFocusRingStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalPosterFocusRingStyle.current

    val layout: NuvioLayoutTokens
        get() = NuvioLayout.tokens

    val media: NuvioMediaTokens
        get() = NuvioMedia.tokens

    val components: NuvioComponentTokens
        get() = NuvioComponents.tokens

    val currentTheme: AppTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current

    val settingsUiStyle: SettingsUiStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalSettingsUiStyle.current
}
