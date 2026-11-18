package com.nuvio.tv.ui.leanback.core

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * High-performance Color Extraction Engine for Android TV.
 * Uses a 16 Hue Bins Histogram to extract the most vibrant Neon dominant color from card bitmaps,
 * cached in an LRU Cache with in-flight request deduplication and single-JNI-pass pixel sampling.
 */
object LeanbackColorEngine {
    private const val SAMPLE_GRID_DIM = 14
    private const val MAX_SAMPLE_PIXELS = SAMPLE_GRID_DIM * SAMPLE_GRID_DIM
    private const val NUM_HUE_BINS = 16

    const val defaultFallbackColor: Int = 0xFF38BDF8.toInt() // Electric Cyan fallback

    private val cacheLock = Any()
    private val dominantColorCache = HashMap<String, Int>(256)
    private val cacheOrder = LinkedHashMap<String, Int>(256, 0.75f, true)
    private const val MAX_CACHE_ENTRIES = 512

    private val inFlightCallbacks = HashMap<String, MutableList<(Int) -> Unit>>()

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val extractionExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "LeanbackColorExtractor").apply {
            priority = Thread.NORM_PRIORITY - 1
            isDaemon = true
        }
    }

    private val pixelBufferPool = object : ThreadLocal<IntArray>() {
        override fun initialValue(): IntArray = IntArray(MAX_SAMPLE_PIXELS)
    }

    fun getCachedColor(key: String): Int? = synchronized(cacheLock) {
        if (!dominantColorCache.containsKey(key)) return@synchronized null
        cacheOrder[key]
    }

    fun putCachedColor(key: String, color: Int) {
        synchronized(cacheLock) {
            dominantColorCache[key] = color
            cacheOrder[key] = color
            if (cacheOrder.size > MAX_CACHE_ENTRIES) {
                val eldestKey = cacheOrder.entries.firstOrNull()?.key
                if (eldestKey != null) {
                    cacheOrder.remove(eldestKey)
                    dominantColorCache.remove(eldestKey)
                }
            }
        }
    }

    fun hasCachedColor(key: String): Boolean = getCachedColor(key) != null

    /**
     * Asynchronously extracts the dominant neon color off the main thread and caches it by [key].
     * Concurrent requests for the same [key] coalesce into a single extraction pass.
     */
    fun extractDominantColorAsync(
        key: String,
        bitmap: Bitmap,
        onExtracted: (Int) -> Unit
    ) {
        val cached = getCachedColor(key)
        if (cached != null) {
            onExtracted(cached)
            return
        }
        if (bitmap.isRecycled) return

        synchronized(cacheLock) {
            val secondCheck = cacheOrder[key]
            if (secondCheck != null) {
                onExtracted(secondCheck)
                return
            }
            val existingListeners = inFlightCallbacks[key]
            if (existingListeners != null) {
                existingListeners.add(onExtracted)
                return
            }
            inFlightCallbacks[key] = mutableListOf(onExtracted)
        }

        extractionExecutor.execute {
            val extracted = if (bitmap.isRecycled) {
                defaultFallbackColor
            } else {
                try {
                    extractDominantColor(bitmap)
                } catch (_: Exception) {
                    defaultFallbackColor
                }
            }
            completeInFlightExtraction(
                key = key,
                extractedColor = extracted,
                dispatch = { runnable -> mainHandler.post(runnable) }
            )
        }
    }

    internal fun runCoalescedExtractionForTest(
        key: String,
        compute: () -> Int,
        dispatch: (Runnable) -> Unit,
        onExtracted: (Int) -> Unit,
        fallbackColor: Int = defaultFallbackColor
    ) {
        synchronized(cacheLock) {
            val cached = cacheOrder[key]
            if (cached != null) {
                onExtracted(cached)
                return
            }
            val existing = inFlightCallbacks[key]
            if (existing != null) {
                existing.add(onExtracted)
                return
            }
            inFlightCallbacks[key] = mutableListOf(onExtracted)
        }

        extractionExecutor.execute {
            val extracted = runCatching { compute() }.getOrDefault(fallbackColor)
            completeInFlightExtraction(key, extracted, dispatch)
        }
    }

    private fun completeInFlightExtraction(
        key: String,
        extractedColor: Int,
        dispatch: (Runnable) -> Unit
    ) {
        val listeners: List<(Int) -> Unit>
        synchronized(cacheLock) {
            dominantColorCache[key] = extractedColor
            cacheOrder[key] = extractedColor
            if (cacheOrder.size > MAX_CACHE_ENTRIES) {
                val eldestKey = cacheOrder.entries.firstOrNull()?.key
                if (eldestKey != null) {
                    cacheOrder.remove(eldestKey)
                    dominantColorCache.remove(eldestKey)
                }
            }
            listeners = inFlightCallbacks.remove(key).orEmpty()
        }
        if (listeners.isNotEmpty()) {
            dispatch(Runnable {
                for (i in listeners.indices) {
                    listeners[i](extractedColor)
                }
            })
        }
    }

    /**
     * Extracts the most vibrant dominant color from a Bitmap using a single-JNI-pass
     * 14x14 downsampled buffer and a 16 Hue Bins Histogram.
     */
    fun extractDominantColor(bitmap: Bitmap): Int {
        if (bitmap.isRecycled) return defaultFallbackColor
        return runCatching {
            if (bitmap.isRecycled) return defaultFallbackColor
            val readableBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return defaultFallbackColor
            } else {
                bitmap
            }
            var scaledBitmap: Bitmap? = null
            try {
                val w = readableBitmap.width
                val h = readableBitmap.height
                if (w <= 0 || h <= 0) return defaultFallbackColor

                val sampleW = w.coerceAtMost(SAMPLE_GRID_DIM)
                val sampleH = h.coerceAtMost(SAMPLE_GRID_DIM)
                val targetBitmap = if (w != sampleW || h != sampleH) {
                    Bitmap.createScaledBitmap(readableBitmap, sampleW, sampleH, false).also {
                        scaledBitmap = it
                    }
                } else {
                    readableBitmap
                }

                val sampleCount = sampleW * sampleH
                val buffer = pixelBufferPool.get() ?: IntArray(MAX_SAMPLE_PIXELS)
                targetBitmap.getPixels(buffer, 0, sampleW, 0, 0, sampleW, sampleH)
                computeDominantNeonColorFromPixels(buffer, sampleCount, defaultFallbackColor)
            } finally {
                if (scaledBitmap != null && scaledBitmap !== readableBitmap && !scaledBitmap!!.isRecycled) {
                    scaledBitmap!!.recycle()
                }
                if (readableBitmap !== bitmap && !readableBitmap.isRecycled) {
                    readableBitmap.recycle()
                }
            }
        }.getOrDefault(defaultFallbackColor)
    }

    /**
     * Pure Kotlin 16-Hue-Bin histogram extractor & OLED neon booster.
     * Zero JNI calls and zero per-pixel object allocations.
     */
    internal fun computeDominantNeonColorFromPixels(
        pixels: IntArray,
        count: Int,
        fallbackColor: Int = defaultFallbackColor
    ): Int {
        val limit = count.coerceAtMost(pixels.size)
        if (limit <= 0) return fallbackColor

        val binScores = FloatArray(NUM_HUE_BINS)
        val binBestWeight = FloatArray(NUM_HUE_BINS)
        val binBestHue = FloatArray(NUM_HUE_BINS)
        val binBestSat = FloatArray(NUM_HUE_BINS)
        val binHasColor = BooleanArray(NUM_HUE_BINS)

        for (idx in 0 until limit) {
            val pixel = pixels[idx]
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 128) continue

            val r = ((pixel ushr 16) and 0xFF) / 255f
            val g = ((pixel ushr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            val maxC = maxOf(r, g, b)
            if (maxC <= 0.18f) continue // Discard absolute dark/black pixels
            val minC = minOf(r, g, b)
            val delta = maxC - minC
            val sat = delta / maxC
            if (sat <= 0.22f) continue // Discard gray/white lacking chroma

            var hue = when (maxC) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }
            if (hue < 0f) hue += 360f

            val weight = sat * sat * maxC
            val bin = (((hue + 11.25f) % 360f) / 22.5f).toInt().coerceIn(0, NUM_HUE_BINS - 1)
            binScores[bin] += weight
            if (weight > binBestWeight[bin]) {
                binBestWeight[bin] = weight
                binBestHue[bin] = hue
                binBestSat[bin] = sat
                binHasColor[bin] = true
            }
        }

        var bestBin = -1
        var maxBinScore = 0f
        for (i in 0 until NUM_HUE_BINS) {
            if (binScores[i] > maxBinScore) {
                maxBinScore = binScores[i]
                bestBin = i
            }
        }

        if (bestBin < 0 || !binHasColor[bestBin]) {
            return fallbackColor
        }

        // Boost saturation and push brightness (V) to 1.0f for radiant OLED neon glow
        val boostedHue = binBestHue[bestBin]
        val boostedSat = (binBestSat[bestBin] * 1.20f).coerceIn(0.75f, 1.0f)
        return hsvToArgb(boostedHue, boostedSat, 1.0f)
    }

    private fun hsvToArgb(hueDeg: Float, sat: Float, value: Float): Int {
        val h = ((hueDeg % 360f) + 360f) % 360f
        val c = value * sat
        val sector = h / 60f
        val x = c * (1f - kotlin.math.abs((sector % 2f) - 1f))
        val m = value - c
        val (r1, g1, b1) = when (sector.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val rInt = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val gInt = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val bInt = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
    }
}
