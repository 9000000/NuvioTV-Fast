package com.nuvio.tv.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Resolves the host Activity [Window] from any Compose [Context].
 */
internal tailrec fun Context.findHostWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findHostWindow()
    else -> null
}

/**
 * Captures a downscaled snapshot of [regionInWindow] from [window] and applies a fast
 * in-place StackBlur on a background thread. Used as a lightweight frosted-glass fallback
 * on Android < 12 (API 24..30) where GPU RenderEffect / Haze blur is unavailable.
 */
internal suspend fun captureBlurredWindowRegion(
    window: Window,
    regionInWindow: Rect,
    blurRadiusPx: Float,
    maxSampleDimension: Int = 160
): Bitmap? {
    val decorView = window.decorView
    if (decorView.width <= 0 || decorView.height <= 0) return null

    val clampedRect = Rect(
        regionInWindow.left.coerceIn(0, decorView.width),
        regionInWindow.top.coerceIn(0, decorView.height),
        regionInWindow.right.coerceIn(0, decorView.width),
        regionInWindow.bottom.coerceIn(0, decorView.height)
    )
    val srcWidth = clampedRect.width()
    val srcHeight = clampedRect.height()
    if (srcWidth < 4 || srcHeight < 4) return null

    val scale = (maxSampleDimension.toFloat() / max(srcWidth, srcHeight).toFloat())
        .coerceIn(0.08f, 0.25f)
    val destWidth = (srcWidth * scale).roundToInt().coerceAtLeast(8)
    val destHeight = (srcHeight * scale).roundToInt().coerceAtLeast(8)

    val bitmap = runCatching {
        Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
    }.getOrNull() ?: return null

    val captured = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        captureWithPixelCopy(window, clampedRect, bitmap)
    } else {
        captureWithSoftwareCanvas(decorView, clampedRect, scale, bitmap)
    }

    if (!captured) {
        bitmap.recycle()
        return null
    }

    val scaledRadius = (blurRadiusPx * scale * 1.4f).roundToInt().coerceIn(6, 24)
    val blurred = runCatching {
        withContext(Dispatchers.Default) {
            stackBlurInPlace(bitmap, scaledRadius)
        }
        true
    }.getOrDefault(false)

    if (!blurred) {
        bitmap.recycle()
        return null
    }
    return bitmap
}

private suspend fun captureWithPixelCopy(
    window: Window,
    srcRect: Rect,
    destBitmap: Bitmap
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val result = withTimeoutOrNull(140L) {
        suspendCancellableCoroutine<Int> { cont ->
            try {
                val handler = Handler(Looper.getMainLooper())
                PixelCopy.request(
                    window,
                    srcRect,
                    destBitmap,
                    { copyResult ->
                        if (cont.isActive) {
                            cont.resume(copyResult)
                        }
                    },
                    handler
                )
            } catch (_: Throwable) {
                if (cont.isActive) {
                    cont.resume(PixelCopy.ERROR_UNKNOWN)
                }
            }
        }
    }
    return result == PixelCopy.SUCCESS
}

private fun captureWithSoftwareCanvas(
    decorView: android.view.View,
    srcRect: Rect,
    scale: Float,
    destBitmap: Bitmap
): Boolean = runCatching {
    val canvas = Canvas(destBitmap)
    canvas.scale(scale, scale)
    canvas.translate(-srcRect.left.toFloat(), -srcRect.top.toFloat())
    decorView.draw(canvas)
    true
}.getOrDefault(false)

/**
 * Fast O(W*H) in-place StackBlur (Mario Klingemann algorithm) on an ARGB_8888 [Bitmap].
 * Operates only on RGB channels and writes opaque alpha (0xFF) so edges never bleed.
 */
internal fun stackBlurInPlace(bitmap: Bitmap, radius: Int) {
    if (radius < 1) return
    val w = bitmap.width
    val h = bitmap.height
    if (w < 2 || h < 2) return

    val pix = IntArray(w * h)
    bitmap.getPixels(pix, 0, w, 0, 0, w, h)

    val wm = w - 1
    val hm = h - 1
    val wh = w * h
    val div = radius + radius + 1
    val r = IntArray(wh)
    val g = IntArray(wh)
    val b = IntArray(wh)
    val vmin = IntArray(max(w, h))

    val r1 = radius + 1
    val divsum = r1 * r1

    val stackR = IntArray(div)
    val stackG = IntArray(div)
    val stackB = IntArray(div)

    var yw = 0
    var yi = 0

    // Horizontal pass
    for (y in 0 until h) {
        var rinsum = 0
        var ginsum = 0
        var binsum = 0
        var routsum = 0
        var goutsum = 0
        var boutsum = 0
        var rsum = 0
        var gsum = 0
        var bsum = 0

        for (i in -radius..radius) {
            val p = pix[yi + min(wm, max(i, 0))]
            val sir = i + radius
            val pr = (p shr 16) and 0xFF
            val pg = (p shr 8) and 0xFF
            val pb = p and 0xFF
            stackR[sir] = pr
            stackG[sir] = pg
            stackB[sir] = pb
            val rbs = r1 - abs(i)
            rsum += pr * rbs
            gsum += pg * rbs
            bsum += pb * rbs
            if (i > 0) {
                rinsum += pr
                ginsum += pg
                binsum += pb
            } else {
                routsum += pr
                goutsum += pg
                boutsum += pb
            }
        }
        var stackPointer = radius

        for (x in 0 until w) {
            r[yi] = (rsum / divsum).coerceIn(0, 255)
            g[yi] = (gsum / divsum).coerceIn(0, 255)
            b[yi] = (bsum / divsum).coerceIn(0, 255)

            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum

            val stackStart = stackPointer - radius + div
            var sir = stackStart % div
            routsum -= stackR[sir]
            goutsum -= stackG[sir]
            boutsum -= stackB[sir]

            if (y == 0) {
                vmin[x] = min(x + radius + 1, wm)
            }
            val p = pix[yw + vmin[x]]
            val pr = (p shr 16) and 0xFF
            val pg = (p shr 8) and 0xFF
            val pb = p and 0xFF
            stackR[sir] = pr
            stackG[sir] = pg
            stackB[sir] = pb

            rinsum += pr
            ginsum += pg
            binsum += pb

            rsum += rinsum
            gsum += ginsum
            bsum += binsum

            stackPointer = (stackPointer + 1) % div
            sir = stackPointer
            routsum += stackR[sir]
            goutsum += stackG[sir]
            boutsum += stackB[sir]

            rinsum -= stackR[sir]
            ginsum -= stackG[sir]
            binsum -= stackB[sir]

            yi++
        }
        yw += w
    }

    // Vertical pass
    for (x in 0 until w) {
        var rinsum = 0
        var ginsum = 0
        var binsum = 0
        var routsum = 0
        var goutsum = 0
        var boutsum = 0
        var rsum = 0
        var gsum = 0
        var bsum = 0
        var yp = -radius * w

        for (i in -radius..radius) {
            yi = max(0, yp) + x
            val sir = i + radius
            val pr = r[yi]
            val pg = g[yi]
            val pb = b[yi]
            stackR[sir] = pr
            stackG[sir] = pg
            stackB[sir] = pb
            val rbs = r1 - abs(i)
            rsum += pr * rbs
            gsum += pg * rbs
            bsum += pb * rbs
            if (i > 0) {
                rinsum += pr
                ginsum += pg
                binsum += pb
            } else {
                routsum += pr
                goutsum += pg
                boutsum += pb
            }
            if (i < hm) {
                yp += w
            }
        }
        yi = x
        var stackPointer = radius

        for (y in 0 until h) {
            val outR = (rsum / divsum).coerceIn(0, 255)
            val outG = (gsum / divsum).coerceIn(0, 255)
            val outB = (bsum / divsum).coerceIn(0, 255)
            pix[yi] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB

            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum

            val stackStart = stackPointer - radius + div
            var sir = stackStart % div
            routsum -= stackR[sir]
            goutsum -= stackG[sir]
            boutsum -= stackB[sir]

            if (x == 0) {
                vmin[y] = min(y + r1, hm) * w
            }
            val p = x + vmin[y]
            val pr = r[p]
            val pg = g[p]
            val pb = b[p]
            stackR[sir] = pr
            stackG[sir] = pg
            stackB[sir] = pb

            rinsum += pr
            ginsum += pg
            binsum += pb

            rsum += rinsum
            gsum += ginsum
            bsum += binsum

            stackPointer = (stackPointer + 1) % div
            sir = stackPointer
            routsum += stackR[sir]
            goutsum += stackG[sir]
            boutsum += stackB[sir]

            rinsum -= stackR[sir]
            ginsum -= stackG[sir]
            binsum -= stackB[sir]

            yi += w
        }
    }

    bitmap.setPixels(pix, 0, w, 0, 0, w, h)
}
