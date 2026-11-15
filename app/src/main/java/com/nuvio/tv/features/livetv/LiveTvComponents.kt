package com.nuvio.tv.features.livetv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

internal val LIVE_RED = Color(0xFFE53935)
internal val GuideGlassEdge = Color(0x33FFFFFF)
internal val GuideGlassFill = Color(0x2B1E222D)

internal val LocalLiveSolidHighlight = staticCompositionLocalOf { false }
internal val LocalGuideDarkness = staticCompositionLocalOf { "darker" }

private val TEXT_OVER_PICTURE = Shadow(
    color = Color.Black.copy(alpha = 0.55f),
    offset = Offset(0f, 1.5f),
    blurRadius = 4f
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiveText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NuvioTheme.colors.TextPrimary,
    size: TextUnit = 16.sp,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    align: TextAlign? = null,
    marquee: Boolean = false,
    shadow: Boolean = false,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    Text(
        style = if (shadow) androidx.compose.material3.LocalTextStyle.current.copy(shadow = TEXT_OVER_PICTURE) else androidx.compose.material3.LocalTextStyle.current,
        text = text,
        modifier = if (marquee && maxLines == 1) {
            modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 1_200,
                repeatDelayMillis = 1_500
            )
        } else modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        maxLines = maxLines,
        softWrap = !(marquee && maxLines == 1),
        overflow = if (marquee && maxLines == 1) TextOverflow.Clip else TextOverflow.Ellipsis,
        textAlign = align,
        lineHeight = lineHeight
    )
}

@Composable
internal fun ScrollingDescription(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NuvioTheme.colors.TextSecondary,
    size: TextUnit = 14.sp,
    maxLines: Int = 2,
    weight: FontWeight = FontWeight.Normal
) {
    val density = LocalDensity.current
    val lineHeight = size * 1.3f
    val boxHeight = with(density) { lineHeight.toDp() } * maxLines.coerceAtLeast(1)
    val scroll = androidx.compose.foundation.rememberScrollState()
    val max = scroll.maxValue
    LaunchedEffect(text, max, maxLines) {
        if (max <= 0) return@LaunchedEffect
        val pxPerSecond = with(density) { lineHeight.toPx() } / 2.5f
        while (true) {
            scroll.scrollTo(0)
            delay(6_000)
            scroll.animateScrollTo(
                max,
                androidx.compose.animation.core.tween((max / pxPerSecond * 1000).toInt().coerceAtLeast(1_500), easing = androidx.compose.animation.core.LinearEasing)
            )
            delay(6_000)
        }
    }
    Box(modifier.heightIn(max = boxHeight).clipToBounds()) {
        Text(
            text = text,
            color = color,
            fontSize = size,
            fontWeight = weight,
            lineHeight = lineHeight,
            modifier = Modifier.verticalScroll(scroll, enabled = false)
        )
    }
}

internal data class TitleMarks(val title: String, val live: Boolean, val isNew: Boolean)

private val LIVE_MARK = Regex("""^\s*live\s*[:\-–|]\s*""", RegexOption.IGNORE_CASE)
private val NEW_MARK = Regex("""^\s*new\s*[:\-–|]\s*|\s*[(\[]\s*new\s*[)\]]\s*""", RegexOption.IGNORE_CASE)

internal class LiveMemo<K, V>(private val max: Int) {
    private val map = object : java.util.LinkedHashMap<K, V>(max, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > max
    }
    @Synchronized fun get(key: K, make: (K) -> V): V = map[key] ?: make(key).also { map[key] = it }
}

private val titleMarksMemo = LiveMemo<String, TitleMarks>(4096)

internal fun titleMarks(title: String): TitleMarks = titleMarksMemo.get(title) { workOutTitleMarks(it) }

private fun workOutTitleMarks(title: String): TitleMarks {
    var t = title
    val live = LIVE_MARK.containsMatchIn(t)
    if (live) t = t.replace(LIVE_MARK, "")
    val isNew = NEW_MARK.containsMatchIn(t)
    if (isNew) t = t.replace(NEW_MARK, " ").trim()
    return TitleMarks(t.ifBlank { title }, live, isNew)
}

@Composable
internal fun LiveTag(
    text: String,
    background: Color = LIVE_RED,
    color: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(text = text, color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
internal fun liveCorner(): Dp = 10.dp

@Composable
internal fun PlayerStatusMessage(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LiveText(
            text = title,
            size = if (compact) 14.sp else 18.sp,
            weight = FontWeight.Bold,
            color = Color.White
        )
        if (!detail.isNullOrBlank()) {
            LiveText(
                text = detail,
                size = if (compact) 12.sp else 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 3,
                align = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun Modifier.glassTile(
    fill: Color,
    edge: Color?,
    shape: androidx.compose.ui.graphics.Shape,
    clipChildren: Boolean = false,
    edgeWidth: Dp = 1.5.dp
): Modifier {
    val painter = remember(fill, edge, shape, edgeWidth) {
        Modifier.drawWithCache {
            val stroke = edgeWidth.toPx()
            val fillOutline = shape.createOutline(size, layoutDirection, this)
            val edgeOutline = if (edge != null) {
                val inner = androidx.compose.ui.geometry.Size((size.width - stroke).coerceAtLeast(1f), (size.height - stroke).coerceAtLeast(1f))
                shape.createOutline(inner, layoutDirection, this)
            } else null
            onDrawBehind {
                paintOutline(fillOutline, fill, null)
                if (edge != null && edgeOutline != null) {
                    translate(stroke / 2f, stroke / 2f) {
                        paintOutline(edgeOutline, edge, Stroke(stroke))
                    }
                }
            }
        }
    }
    return this.then(if (clipChildren) Modifier.clip(shape) else Modifier).then(painter)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.paintOutline(
    outline: androidx.compose.ui.graphics.Outline,
    color: Color,
    style: androidx.compose.ui.graphics.drawscope.DrawStyle?
) {
    val st = style ?: androidx.compose.ui.graphics.drawscope.Fill
    when (outline) {
        is androidx.compose.ui.graphics.Outline.Rectangle -> drawRect(color, outline.rect.topLeft, outline.rect.size, style = st)
        is androidx.compose.ui.graphics.Outline.Rounded -> {
            val r = outline.roundRect
            drawRoundRect(
                color, Offset(r.left, r.top), androidx.compose.ui.geometry.Size(r.width, r.height),
                androidx.compose.ui.geometry.CornerRadius(r.topLeftCornerRadius.x, r.topLeftCornerRadius.y), style = st
            )
        }
        is androidx.compose.ui.graphics.Outline.Generic -> drawPath(outline.path, color, style = st)
    }
}

@Composable
internal fun LivePlayerSurface(
    player: androidx.media3.common.Player?,
    modifier: Modifier = Modifier,
    resizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                useController = false
                this.resizeMode = resizeMode
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
            view.resizeMode = resizeMode
        }
    )
}

@Composable
fun LivePosterImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    fallback: @Composable () -> Unit = {}
) {
    if (url.isNullOrBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            fallback()
        }
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageRequest: coil3.request.ImageRequest = androidx.compose.runtime.remember(url) {
        coil3.request.ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
            .build()
    }
    var wide by androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableStateOf(false) }
    var loaded by androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        modifier = modifier.then(if (wide && loaded) Modifier.background(Color.Black) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) fallback()
        coil3.compose.AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = if (wide) androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Crop,
            onSuccess = { s ->
                val size = s.painter.intrinsicSize
                wide = size.width > 0f && size.height > 0f && size.width > size.height * 1.1f
                loaded = true
            },
            onError = { loaded = false },
            modifier = Modifier.fillMaxSize()
        )
    }
}

