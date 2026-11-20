package com.nuvio.tv.ui.leanback.core

import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil3.Image
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.memory.MemoryCache
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import coil3.target.Target
import com.nuvio.tv.core.image.CustomPosterFallbackInterceptor
import com.nuvio.tv.core.image.sizedHomeArtworkUrl
import com.nuvio.tv.domain.model.isPlaceholder
import com.nuvio.tv.ui.util.BlurTransformation

/**
 * Unified Coil 3 Artwork Loader & MemoryCache Fast-Path coordinator for Android Leanback cards.
 *
 * Ensures 100% cache-key parity across:
 * - Poster, Episode (normal & blurred), Avatar, and Modern Home cards
 * - Custom Poster fallback (`CustomPosterFallbackInterceptor.FALLBACK_URL_KEY`)
 * - Horizontal ahead-prefetch and Vertical row ahead-prefetch (with `allowHardware(false)` alignment)
 */
object LeanbackArtworkLoader {
    private const val MAX_PREFETCHED_KEYS = 512
    private const val PREFETCH_TRIM_BATCH = 128

    const val DEFAULT_HORIZONTAL_PREFETCH_AHEAD = 6
    const val DEFAULT_HORIZONTAL_PREFETCH_BEHIND = 2

    fun resolveNormalizedFallbackUrl(imageUrl: String, fallbackUrl: String?): String? {
        val trimmed = fallbackUrl?.trim()
        return if (!trimmed.isNullOrEmpty() && !trimmed.isPlaceholder() && trimmed != imageUrl) trimmed else null
    }

    /**
     * Computes offscreen adapter indices to prefetch around the visible horizontal slice.
     * Forward items beyond [lastVisiblePosition] are prioritized before trailing items behind
     * [firstVisiblePosition], skipping items currently bound on screen.
     */
    fun computeHorizontalPrefetchIndices(
        focusedPosition: Int,
        firstVisiblePosition: Int,
        lastVisiblePosition: Int,
        itemCount: Int,
        aheadCount: Int = DEFAULT_HORIZONTAL_PREFETCH_AHEAD,
        behindCount: Int = DEFAULT_HORIZONTAL_PREFETCH_BEHIND
    ): List<Int> {
        if (itemCount <= 1) return emptyList()
        val safeAhead = aheadCount.coerceAtLeast(0)
        val safeBehind = behindCount.coerceAtLeast(0)
        if (safeAhead == 0 && safeBehind == 0) return emptyList()

        val hasVisibleBounds =
            lastVisiblePosition >= 0 &&
            firstVisiblePosition >= 0 &&
            lastVisiblePosition >= firstVisiblePosition

        val rightAnchor: Int
        val leftAnchor: Int
        val visibleRange: IntRange
        if (hasVisibleBounds) {
            val clampedFirst = firstVisiblePosition.coerceIn(0, itemCount - 1)
            val clampedLast = lastVisiblePosition.coerceIn(clampedFirst, itemCount - 1)
            visibleRange = clampedFirst..clampedLast
            if (focusedPosition >= 0) {
                val clampedFocus = focusedPosition.coerceIn(0, itemCount - 1)
                rightAnchor = maxOf(clampedFocus, clampedLast)
                leftAnchor = minOf(clampedFocus, clampedFirst)
            } else {
                rightAnchor = clampedLast
                leftAnchor = clampedFirst
            }
        } else {
            visibleRange = IntRange.EMPTY
            rightAnchor = focusedPosition.coerceIn(0, itemCount - 1)
            leftAnchor = rightAnchor
        }

        val result = ArrayList<Int>(safeAhead + safeBehind)
        val aheadEnd = minOf(itemCount - 1, rightAnchor + safeAhead)
        for (i in (rightAnchor + 1)..aheadEnd) {
            if (i in 0 until itemCount && i !in visibleRange) {
                result.add(i)
            }
        }
        val behindStart = maxOf(0, leftAnchor - safeBehind)
        for (i in (leftAnchor - 1) downTo behindStart) {
            if (i in 0 until itemCount && i !in visibleRange && !result.contains(i)) {
                result.add(i)
            }
        }
        return result
    }

    internal fun resolveVisibleBounds(rv: RecyclerView?): Pair<Int, Int> {
        if (rv == null || rv.childCount == 0) return -1 to -1
        var first = Int.MAX_VALUE
        var last = Int.MIN_VALUE
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i) ?: continue
            val pos = rv.getChildAdapterPosition(child)
            if (pos != RecyclerView.NO_POSITION) {
                if (pos < first) first = pos
                if (pos > last) last = pos
            }
        }
        return if (first == Int.MAX_VALUE || last == Int.MIN_VALUE) -1 to -1 else first to last
    }

    internal fun isRowNearViewport(rv: RecyclerView): Boolean {
        if (!rv.isAttachedToWindow || rv.visibility != View.VISIBLE) return false
        if (rv.width == 0 || rv.height == 0) return true
        if (!rv.isShown) return false
        val screenHeight = rv.resources.displayMetrics.heightPixels
        if (screenHeight <= 0) return true
        val location = IntArray(2)
        rv.getLocationInWindow(location)
        val top = location[1]
        val bottom = top + rv.height
        val margin = rv.height.coerceAtLeast(screenHeight / 4)
        return bottom >= -margin && top <= screenHeight + margin
    }

    fun buildBaseMemoryKey(
        imageUrl: String,
        widthPx: Int,
        heightPx: Int,
        blurred: Boolean = false
    ): String {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val mappedUrl = sizedHomeArtworkUrl(imageUrl, w, h)
        return if (blurred) "${mappedUrl}_${w}x${h}_blur" else "${mappedUrl}_${w}x${h}"
    }

    fun buildPrimaryMemoryCacheKey(baseMemKey: String, fallbackUrl: String?): MemoryCache.Key {
        return if (fallbackUrl != null) {
            MemoryCache.Key(
                baseMemKey,
                mapOf(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to fallbackUrl)
            )
        } else {
            MemoryCache.Key(baseMemKey)
        }
    }

    fun findInMemoryCache(
        imageLoader: ImageLoader,
        baseMemKey: String,
        fallbackUrl: String?
    ): MemoryCache.Value? {
        val memCache = imageLoader.memoryCache ?: return null
        val primaryKey = buildPrimaryMemoryCacheKey(baseMemKey, fallbackUrl)
        memCache.get(primaryKey)?.let { return it }
        if (fallbackUrl != null) {
            // CustomPosterFallbackInterceptor strips FALLBACK_URL_KEY when falling back to rawPosterUrl
            memCache.get(MemoryCache.Key(baseMemKey))?.let { return it }
        }
        return null
    }

    fun loadIntoImageView(
        imageView: ImageView,
        imageUrl: String,
        fallbackUrl: String? = null,
        widthPx: Int,
        heightPx: Int,
        blurred: Boolean = false,
        isStillBound: () -> Boolean,
        onCacheHit: (Image) -> Unit,
        onAsyncSuccess: (Image) -> Unit,
        onAsyncError: () -> Unit = {}
    ): Disposable? {
        if (imageUrl.isBlank() || imageUrl.trim().isPlaceholder()) return null
        val ctx = imageView.context
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val normalizedFallback = resolveNormalizedFallbackUrl(imageUrl, fallbackUrl)
        val baseMemKey = buildBaseMemoryKey(imageUrl, w, h, blurred)
        val imageLoader = SingletonImageLoader.get(ctx)

        val cachedVal = findInMemoryCache(imageLoader, baseMemKey, normalizedFallback)
        if (cachedVal != null) {
            val image = cachedVal.image
            imageView.setImageDrawable(image.asDrawable(ctx.resources))
            onCacheHit(image)
            return null
        }

        val request = ImageRequest.Builder(ctx)
            .data(imageUrl)
            .memoryCacheKey(baseMemKey)
            .size(w, h)
            .allowHardware(false)
            .crossfade(false)
            .apply {
                if (normalizedFallback != null) {
                    memoryCacheKeyExtras(
                        mapOf(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to normalizedFallback)
                    )
                }
                if (blurred) {
                    transformations(BlurTransformation())
                }
            }
            .target(object : Target {
                override fun onStart(placeholder: Image?) {
                    if (!isStillBound()) return
                    if (placeholder != null) {
                        imageView.setImageDrawable(placeholder.asDrawable(ctx.resources))
                    }
                }

                override fun onSuccess(result: Image) {
                    if (!isStillBound()) return
                    imageView.setImageDrawable(result.asDrawable(ctx.resources))
                    onAsyncSuccess(result)
                }

                override fun onError(error: Image?) {
                    if (!isStillBound()) return
                    imageView.setImageDrawable(error?.asDrawable(ctx.resources))
                    onAsyncError()
                }
            })
            .build()

        return imageLoader.enqueue(request)
    }

    fun prefetch(
        context: Context,
        imageLoader: ImageLoader = SingletonImageLoader.get(context),
        imageUrl: String?,
        fallbackUrl: String? = null,
        widthPx: Int,
        heightPx: Int,
        blurred: Boolean = false,
        prefetchedKeysTracker: LinkedHashSet<String>? = null
    ) {
        val url = imageUrl?.trim()
        if (url.isNullOrEmpty() || url.isPlaceholder()) return
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val normalizedFallback = resolveNormalizedFallbackUrl(url, fallbackUrl)
        val baseMemKey = buildBaseMemoryKey(url, w, h, blurred)
        val dedupKey = if (normalizedFallback != null) "$baseMemKey|$normalizedFallback" else baseMemKey

        if (prefetchedKeysTracker != null) {
            if (prefetchedKeysTracker.contains(dedupKey)) return
            if (prefetchedKeysTracker.size >= MAX_PREFETCHED_KEYS) {
                val iter = prefetchedKeysTracker.iterator()
                repeat(PREFETCH_TRIM_BATCH) {
                    if (iter.hasNext()) {
                        iter.next()
                        iter.remove()
                    }
                }
            }
            prefetchedKeysTracker.add(dedupKey)
        }

        if (findInMemoryCache(imageLoader, baseMemKey, normalizedFallback) != null) return

        val req = ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(baseMemKey)
            .size(w, h)
            .allowHardware(false)
            .crossfade(false)
            .apply {
                if (normalizedFallback != null) {
                    memoryCacheKeyExtras(
                        mapOf(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to normalizedFallback)
                    )
                }
                if (blurred) {
                    transformations(BlurTransformation())
                }
            }
            .build()
        imageLoader.enqueue(req)
    }
}
