package com.nuvio.tv.ui.leanback.core

import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity

/**
 * Creates a rounded pill-shaped progress bar LayerDrawable for TV cards.
 * Prevents edge clipping and aligns with rounded corner designs.
 */
object PillProgressBarDrawable {
    fun create(
        heightPx: Float,
        trackColor: Int = Color.parseColor("#4D000000"),
        progressColor: Int = Color.parseColor("#38BDF8")
    ): LayerDrawable {
        val radius = heightPx / 2f
        val trackPill = GradientDrawable().apply {
            setColor(trackColor)
            cornerRadius = radius
        }
        val progPill = GradientDrawable().apply {
            setColor(progressColor)
            cornerRadius = radius
        }
        val clipProg = ClipDrawable(progPill, Gravity.START, ClipDrawable.HORIZONTAL)
        return LayerDrawable(arrayOf(trackPill, clipProg)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
    }
}

