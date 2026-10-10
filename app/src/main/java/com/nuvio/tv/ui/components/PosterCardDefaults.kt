package com.nuvio.tv.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.nuvio.tv.ui.theme.NuvioComponents

val LocalLandscapePosterMode = staticCompositionLocalOf { false }
val LocalAlwaysBackdropWithLogo = staticCompositionLocalOf { false }
val LocalGpuOffscreenCompositing = staticCompositionLocalOf { false }

fun Modifier.nuvioOffscreenStrategy(forceOffscreen: Boolean): Modifier =
    if (forceOffscreen) graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else this

@Immutable
data class PosterCardStyle(
    val width: Dp = NuvioComponents.tokens.posterCard.width,
    val height: Dp = NuvioComponents.tokens.posterCard.height,
    val cornerRadius: Dp = NuvioComponents.tokens.posterCard.cornerRadius,
    val focusedBorderWidth: Dp = NuvioComponents.tokens.posterCard.focusedBorderWidth,
    val focusedScale: Float = NuvioComponents.tokens.posterCard.focusedScale
) {
    val aspectRatio: Float
        get() = width.value / height.value
}

object PosterCardDefaults {
    val Style = PosterCardStyle()
}
