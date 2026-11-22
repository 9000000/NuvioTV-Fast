package com.nuvio.tv.ui.leanback.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.AppFont
import com.nuvio.tv.domain.model.PosterBorderStyle
import com.nuvio.tv.ui.theme.LocalAppFont
import com.nuvio.tv.ui.theme.LocalPosterBorderStyle
import com.nuvio.tv.ui.theme.LocalPosterFocusRingStyle
import com.nuvio.tv.ui.theme.NuvioTheme
import java.util.concurrent.ConcurrentHashMap

private val leanbackTypefaceCache = ConcurrentHashMap<AppFont, Typeface>()

internal fun resolveLeanbackTypeface(context: Context, appFont: AppFont): Typeface? {
    leanbackTypefaceCache[appFont]?.let { return it }
    val fontRes = when (appFont) {
        AppFont.INTER -> R.font.inter_variable
        AppFont.DM_SANS -> R.font.dm_sans_variable
        AppFont.OPEN_SANS -> R.font.opensans_variable
    }
    return runCatching {
        ResourcesCompat.getFont(context, fontRes)?.also { leanbackTypefaceCache[appFont] = it }
    }.getOrNull()
}

/**
 * Theme bridge passed from Jetpack Compose [NuvioTheme] into native Android Leanback
 * ViewHolders and floating focus rings so colors, focus borders, rainbow gradients,
 * and typography stay aligned with the active theme and user preferences.
 */
@Immutable
data class LeanbackThemeStyle(
    val placeholderColor: Int = AndroidColor.parseColor("#1A1A1A"),
    val avatarPlaceholderColor: Int = AndroidColor.parseColor("#222226"),
    val titleColor: Int = AndroidColor.WHITE,
    val subtitleColor: Int = AndroidColor.parseColor("#9CA3AF"),
    val accentColor: Int = AndroidColor.parseColor("#38BDF8"),
    val focusRingColor: Int = AndroidColor.parseColor("#38BDF8"),
    val focusGradientColors: List<Int> = emptyList(),
    val posterBorderStyle: PosterBorderStyle = PosterBorderStyle.Default,
    val hideFocusBorder: Boolean = false,
    val borderWidthDp: Float = 2.5f,
    val appFont: AppFont = AppFont.INTER,
    val typeface: Typeface? = null
) {
    val isRainbowBorder: Boolean
        get() = !hideFocusBorder &&
            posterBorderStyle != PosterBorderStyle.POSTER_COLOR &&
            (posterBorderStyle == PosterBorderStyle.RAINBOW ||
                (posterBorderStyle == PosterBorderStyle.THEME && focusGradientColors.size > 1))

    /**
     * Resolves the stroke color for a focused card border.
     * - [PosterBorderStyle.NONE]: transparent
     * - [PosterBorderStyle.SOLID_WHITE], [PosterBorderStyle.SOLID_RED],
     *   [PosterBorderStyle.SOLID_BLUE], [PosterBorderStyle.SOLID_GOLD]: pure solid color without poster tinting
     * - [PosterBorderStyle.RAINBOW]: primary rainbow color fallback (when shader is not active)
     * - [PosterBorderStyle.POSTER_COLOR]: pure dominant poster color, completely independent of the active theme
     * - [PosterBorderStyle.THEME]: blends dominant poster color with the theme focus ring when [blendWithDominant] is true
     */
    fun resolveFocusedBorderColor(dominantColor: Int, blendWithDominant: Boolean = true): Int {
        if (hideFocusBorder || posterBorderStyle == PosterBorderStyle.NONE) {
            return AndroidColor.TRANSPARENT
        }
        return when (posterBorderStyle) {
            PosterBorderStyle.SOLID_WHITE,
            PosterBorderStyle.SOLID_RED,
            PosterBorderStyle.SOLID_BLUE,
            PosterBorderStyle.SOLID_GOLD,
            PosterBorderStyle.RAINBOW -> focusRingColor
            PosterBorderStyle.POSTER_COLOR -> {
                if (dominantColor != 0 && dominantColor != AndroidColor.WHITE && dominantColor != AndroidColor.BLACK) {
                    dominantColor
                } else {
                    LeanbackColorEngine.defaultFallbackColor
                }
            }
            PosterBorderStyle.THEME -> {
                if (!blendWithDominant) {
                    focusRingColor
                } else if (focusRingColor == AndroidColor.WHITE) {
                    ColorUtils.blendARGB(dominantColor, AndroidColor.WHITE, 0.90f)
                } else {
                    ColorUtils.blendARGB(dominantColor, focusRingColor, 0.72f)
                }
            }
            PosterBorderStyle.NONE -> AndroidColor.TRANSPARENT
        }
    }

    /**
     * Resolves the atmospheric glow color around a focused card.
     * - [PosterBorderStyle.NONE]: transparent
     * - [PosterBorderStyle.SOLID_WHITE], [PosterBorderStyle.SOLID_RED],
     *   [PosterBorderStyle.SOLID_BLUE], [PosterBorderStyle.SOLID_GOLD]: harmonizes glow with the solid border color
     * - [PosterBorderStyle.POSTER_COLOR], [PosterBorderStyle.THEME], [PosterBorderStyle.RAINBOW]: uses the poster's vibrant dominant color
     */
    fun resolveGlowColor(
        dominantColor: Int,
        fallbackColor: Int = LeanbackColorEngine.defaultFallbackColor
    ): Int {
        if (hideFocusBorder || posterBorderStyle == PosterBorderStyle.NONE) {
            return AndroidColor.TRANSPARENT
        }
        return when (posterBorderStyle) {
            PosterBorderStyle.SOLID_WHITE,
            PosterBorderStyle.SOLID_RED,
            PosterBorderStyle.SOLID_BLUE,
            PosterBorderStyle.SOLID_GOLD -> focusRingColor
            PosterBorderStyle.POSTER_COLOR,
            PosterBorderStyle.THEME,
            PosterBorderStyle.RAINBOW -> {
                if (dominantColor != 0 && dominantColor != AndroidColor.WHITE && dominantColor != AndroidColor.BLACK) {
                    dominantColor
                } else {
                    fallbackColor
                }
            }
            PosterBorderStyle.NONE -> AndroidColor.TRANSPARENT
        }
    }

    companion object {
        val Default = LeanbackThemeStyle()
    }
}

/**
 * Zero-allocation reusable focus border drawable supporting both solid strokes and
 * multi-stop linear gradients (e.g. [PosterBorderStyle.RAINBOW]) across rounded rectangles and ovals.
 */
class LeanbackFocusBorderDrawable(
     cornerRadiusPx: Float = 0f
) : Drawable() {
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val borderPath = Path()

    var isOval: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    var cornerRadiusPx: Float = cornerRadiusPx
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    var strokeWidthPx: Float = 4f
        private set

    private var strokeColor: Int = AndroidColor.WHITE
    private var gradientColors: IntArray? = null
    private var cachedShader: LinearGradient? = null
    private var cachedShaderWidth: Int = -1
    private var cachedShaderHeight: Int = -1

    fun configure(
        widthPx: Float,
        color: Int,
        rainbowColors: List<Int>? = null
    ) {
        val resolvedWidth = widthPx.coerceAtLeast(1f)
        val hasGradient = rainbowColors != null && rainbowColors.size > 1
        val newGradientArray = if (hasGradient) rainbowColors.toIntArray() else null
        val gradientChanged = !gradientColors.contentEquals(newGradientArray)
        val changed = strokeWidthPx != resolvedWidth ||
            strokeColor != color ||
            gradientChanged

        if (!changed) return

        strokeWidthPx = resolvedWidth
        strokeColor = color
        gradientColors = newGradientArray
        strokePaint.strokeWidth = resolvedWidth
        strokePaint.color = color
        if (gradientChanged) {
            cachedShader = null
            cachedShaderWidth = -1
            cachedShaderHeight = -1
        }
        rebuildShaderIfNeeded(bounds.width(), bounds.height())
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rebuildShaderIfNeeded(bounds.width(), bounds.height())
    }

    private fun rebuildShaderIfNeeded(w: Int, h: Int) {
        val colors = gradientColors
        if (colors == null || colors.size <= 1) {
            strokePaint.shader = null
            cachedShader = null
            return
        }
        if (w <= 0 || h <= 0) return
        if (cachedShader != null && cachedShaderWidth == w && cachedShaderHeight == h) {
            strokePaint.shader = cachedShader
            return
        }
        val shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            h.toFloat(),
            colors,
            null,
            Shader.TileMode.CLAMP
        )
        cachedShader = shader
        cachedShaderWidth = w
        cachedShaderHeight = h
        strokePaint.shader = shader
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width()
        val h = b.height()
        if (w <= 0 || h <= 0 || strokeWidthPx <= 0f) return
        if (gradientColors == null && strokeColor == AndroidColor.TRANSPARENT) return

        rebuildShaderIfNeeded(w, h)
        val inset = strokeWidthPx * 0.5f
        val left = b.left + inset
        val top = b.top + inset
        val right = b.right - inset
        val bottom = b.bottom - inset
        if (right <= left || bottom <= top) return

        borderPath.reset()
        if (isOval) {
            borderPath.addOval(left, top, right, bottom, Path.Direction.CW)
        } else if (cornerRadiusPx > 0f) {
            val adjRadius = (cornerRadiusPx - inset * 0.5f).coerceAtLeast(0f)
            borderPath.addRoundRect(left, top, right, bottom, adjRadius, adjRadius, Path.Direction.CW)
        } else {
            borderPath.addRect(left, top, right, bottom, Path.Direction.CW)
        }
        canvas.drawPath(borderPath, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

@Composable
fun rememberLeanbackThemeStyle(borderWidthDp: Float = 2.5f): LeanbackThemeStyle {
    val context = LocalContext.current
    val colors = NuvioTheme.colors
    val appFont = LocalAppFont.current
    val posterBorderStyle = LocalPosterBorderStyle.current
    val posterFocusRingStyle = LocalPosterFocusRingStyle.current
    val solidFocusColor = posterFocusRingStyle.solidColor
    val hideFocusBorder = posterBorderStyle == PosterBorderStyle.NONE ||
        solidFocusColor == Color.Transparent ||
        solidFocusColor.alpha == 0f
    val focusRingArgb = if (hideFocusBorder) AndroidColor.TRANSPARENT else solidFocusColor.toArgb()
    val gradientArgbList = remember(posterFocusRingStyle, hideFocusBorder) {
        if (hideFocusBorder) {
            emptyList()
        } else {
            posterFocusRingStyle.gradientColors.map { it.toArgb() }
        }
    }
    val typeface = remember(context, appFont) {
        resolveLeanbackTypeface(context, appFont)
    }
    val placeholderArgb = colors.BackgroundCard.toArgb()
    val avatarPlaceholderArgb = colors.BackgroundElevated.toArgb()
    val titleArgb = colors.TextPrimary.toArgb()
    val subtitleArgb = colors.TextSecondary.toArgb()
    val accentArgb = colors.Secondary.toArgb()

    return remember(
        placeholderArgb,
        avatarPlaceholderArgb,
        titleArgb,
        subtitleArgb,
        accentArgb,
        focusRingArgb,
        gradientArgbList,
        posterBorderStyle,
        hideFocusBorder,
        borderWidthDp,
        appFont,
        typeface
    ) {
        LeanbackThemeStyle(
            placeholderColor = placeholderArgb,
            avatarPlaceholderColor = avatarPlaceholderArgb,
            titleColor = titleArgb,
            subtitleColor = subtitleArgb,
            accentColor = accentArgb,
            focusRingColor = focusRingArgb,
            focusGradientColors = gradientArgbList,
            posterBorderStyle = posterBorderStyle,
            hideFocusBorder = hideFocusBorder,
            borderWidthDp = borderWidthDp,
            appFont = appFont,
            typeface = typeface
        )
    }
}
