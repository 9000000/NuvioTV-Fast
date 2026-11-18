package com.nuvio.tv.ui.leanback.core

import android.content.Context
import android.widget.ImageView
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

    fun resolveNormalizedFallbackUrl(imageUrl: String, fallbackUrl: String?): String? {
        val trimmed = fallbackUrl?.trim()
        return if (!trimmed.isNullOrEmpty() && trimmed != imageUrl) trimmed else null
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
        if (url.isNullOrEmpty()) return
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
