package com.nuvio.tv.ui.leanback.core

import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache

/**
 * High-performance Color Extraction Engine for Android TV.
 * Uses a 16 Hue Bins Histogram to extract the most vibrant Neon dominant color from card bitmaps,
 * cached in an LRU Cache to eliminate redundant calculations across scroll frames.
 */
object LeanbackColorEngine {
    private val dominantColorCache = LruCache<String, Int>(512)
    val defaultFallbackColor: Int = Color.parseColor("#38BDF8") // Electric Cyan fallback

    fun getCachedColor(key: String): Int? = dominantColorCache.get(key)

    fun putCachedColor(key: String, color: Int) {
        dominantColorCache.put(key, color)
    }

    fun hasCachedColor(key: String): Boolean = dominantColorCache.get(key) != null

    /**
     * Extracts the most vibrant dominant color from a Bitmap using a 16 Hue Bins Histogram.
     * Weights pixels by sat * sat * brightness to prioritize radiant neon tones over dark or muddy pixels.
     */
    fun extractDominantColor(bitmap: Bitmap): Int {
        if (bitmap.isRecycled) return Color.WHITE
        return runCatching {
            val stepX = (bitmap.width / 14).coerceAtLeast(1)
            val stepY = (bitmap.height / 14).coerceAtLeast(1)
            val hsv = FloatArray(3)
            val numBins = 16
            val binScores = FloatArray(numBins)
            val binBestWeight = FloatArray(numBins)
            val binRepresentative = IntArray(numBins)

            for (x in 0 until bitmap.width step stepX) {
                for (y in 0 until bitmap.height step stepY) {
                    val pixel = bitmap.getPixel(x, y)
                    val alpha = (pixel ushr 24) and 0xFF
                    if (alpha < 128) continue
                    Color.colorToHSV(pixel, hsv)
                    val sat = hsv[1]
                    val brightness = hsv[2]
                    // Discard absolute dark/black and gray/white lacking chroma
                    if (sat > 0.22f && brightness > 0.18f) {
                        val weight = sat * sat * brightness
                        val bin = (((hsv[0] + 11.25f) % 360f) / 22.5f).toInt().coerceIn(0, numBins - 1)
                        binScores[bin] += weight
                        if (weight > binBestWeight[bin]) {
                            binBestWeight[bin] = weight
                            binRepresentative[bin] = pixel
                        }
                    }
                }
            }

            var bestBin = -1
            var maxBinScore = 0f
            for (i in 0 until numBins) {
                if (binScores[i] > maxBinScore) {
                    maxBinScore = binScores[i]
                    bestBin = i
                }
            }

            val dominantColor = if (bestBin >= 0 && binRepresentative[bestBin] != 0) {
                binRepresentative[bestBin]
            } else {
                defaultFallbackColor
            }

            Color.colorToHSV(dominantColor, hsv)
            // Boost saturation and push brightness to maximum for radiant OLED neon glow
            hsv[1] = (hsv[1] * 1.20f).coerceIn(0.75f, 1.0f)
            hsv[2] = 1.0f
            Color.HSVToColor(hsv)
        }.getOrDefault(defaultFallbackColor)
    }
}

