package com.nuvio.tv.ui.leanback.core

import android.graphics.BlurMaskFilter
import android.util.LruCache
import kotlin.math.roundToInt

/**
 * Shared LRU cache for [BlurMaskFilter] instances across [PlayStoreGlowView] and
 * Modern Home's floating focus ring so repeated blur radii reuse native Skia filters.
 */
object LeanbackBlurFilterCache {
    private val cache = object : LruCache<Int, BlurMaskFilter>(24) {
        override fun create(key: Int): BlurMaskFilter {
            val radius = (key / 2f).coerceAtLeast(1f)
            return BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
    }

    fun get(radiusPx: Float): BlurMaskFilter {
        val key = (radiusPx.coerceAtLeast(1f) * 2f).roundToInt().coerceAtLeast(2)
        return cache.get(key)
    }
}
