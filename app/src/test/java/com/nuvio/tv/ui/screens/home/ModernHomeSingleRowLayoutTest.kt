package com.nuvio.tv.ui.screens.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernHomeSingleRowLayoutTest {

    @Test
    fun `disabled single row preserves default screen height fraction`() {
        val portraitHeight = modernRowsViewportHeight(
            screenHeight = 540.dp,
            defaultFraction = 0.52f,
            singleRowEnabled = false,
            rowTitleHeight = 24.dp,
            catalogCardHeight = 171.5.dp,
            continueWatchingCardHeight = 118.3.dp,
            hasContinueWatchingRow = true
        )
        val landscapeHeight = modernRowsViewportHeight(
            screenHeight = 540.dp,
            defaultFraction = 0.49f,
            singleRowEnabled = false,
            rowTitleHeight = 24.dp,
            catalogCardHeight = 118.3.dp,
            continueWatchingCardHeight = 118.3.dp,
            hasContinueWatchingRow = true
        )

        assertEquals(540f * 0.52f, portraitHeight.value, 0.01f)
        assertEquals(540f * 0.49f, landscapeHeight.value, 0.01f)
    }

    @Test
    fun `enabled single row uses catalog height when continue watching cards are shorter`() {
        val portraitCardHeight = 171.5.dp
        val cwCardHeight = 118.3.dp
        val viewportHeight = modernRowsViewportHeight(
            screenHeight = 540.dp,
            defaultFraction = 0.52f,
            singleRowEnabled = true,
            rowTitleHeight = 24.dp,
            catalogCardHeight = portraitCardHeight,
            continueWatchingCardHeight = cwCardHeight,
            hasContinueWatchingRow = true
        )

        val expected = 24.dp +
            MODERN_ROW_TITLE_BOTTOM_SPACING +
            portraitCardHeight +
            (MODERN_ROW_CARD_VERTICAL_PADDING * 2) +
            MODERN_SINGLE_ROW_BOTTOM_INSET

        assertEquals(expected.value, viewportHeight.value, 0.01f)
        assertTrue(viewportHeight < 540.dp * 0.52f)
    }

    @Test
    fun `enabled single row lowers landscape rows to fit a single landscape row`() {
        val landscapeCardHeight = 118.3.dp
        val viewportHeight = modernRowsViewportHeight(
            screenHeight = 540.dp,
            defaultFraction = 0.49f,
            singleRowEnabled = true,
            rowTitleHeight = 24.dp,
            catalogCardHeight = landscapeCardHeight,
            continueWatchingCardHeight = landscapeCardHeight,
            hasContinueWatchingRow = true
        )

        val expected = 24.dp +
            MODERN_ROW_TITLE_BOTTOM_SPACING +
            landscapeCardHeight +
            (MODERN_ROW_CARD_VERTICAL_PADDING * 2) +
            MODERN_SINGLE_ROW_BOTTOM_INSET

        assertEquals(expected.value, viewportHeight.value, 0.01f)
        assertTrue(viewportHeight < 540.dp * 0.49f)
    }

    @Test
    fun `enabled single row prevents clipping when visible continue watching cards are taller than catalog`() {
        val landscapeCardHeight = 118.3.dp
        val cwPosterHeight = 171.5.dp

        val withCw = modernRowsViewportHeight(
            screenHeight = 540.dp,
            defaultFraction = 0.49f,
            singleRowEnabled = true,
            rowTitleHeight = 24.dp,
            catalogCardHeight = landscapeCardHeight,
            continueWatchingCardHeight = cwPosterHeight,
            hasContinueWatchingRow = true
        )
        val withoutCw = modernRowsViewportHeight(
            screenHeight = 540.dp,
            defaultFraction = 0.49f,
            singleRowEnabled = true,
            rowTitleHeight = 24.dp,
            catalogCardHeight = landscapeCardHeight,
            continueWatchingCardHeight = cwPosterHeight,
            hasContinueWatchingRow = false
        )

        val expectedWithCw = 24.dp +
            MODERN_ROW_TITLE_BOTTOM_SPACING +
            cwPosterHeight +
            (MODERN_ROW_CARD_VERTICAL_PADDING * 2) +
            MODERN_SINGLE_ROW_BOTTOM_INSET
        val expectedWithoutCw = 24.dp +
            MODERN_ROW_TITLE_BOTTOM_SPACING +
            landscapeCardHeight +
            (MODERN_ROW_CARD_VERTICAL_PADDING * 2) +
            MODERN_SINGLE_ROW_BOTTOM_INSET

        assertEquals(expectedWithCw.value, withCw.value, 0.01f)
        assertEquals(expectedWithoutCw.value, withoutCw.value, 0.01f)
    }

    @Test
    fun `enabled single row accounts for optional labels and never exceeds screen height`() {
        val withLabels = modernRowsViewportHeight(
            screenHeight = 540.dp,
            defaultFraction = 0.52f,
            singleRowEnabled = true,
            rowTitleHeight = 24.dp,
            catalogCardHeight = 171.5.dp,
            labelHeight = 26.dp
        )
        val expectedWithLabels = 24.dp +
            MODERN_ROW_TITLE_BOTTOM_SPACING +
            171.5.dp +
            26.dp +
            (MODERN_ROW_CARD_VERTICAL_PADDING * 2) +
            MODERN_SINGLE_ROW_BOTTOM_INSET
        assertEquals(expectedWithLabels.value, withLabels.value, 0.01f)

        val clamped = modernRowsViewportHeight(
            screenHeight = 180.dp,
            defaultFraction = 0.52f,
            singleRowEnabled = true,
            rowTitleHeight = 24.dp,
            catalogCardHeight = 190.dp
        )
        assertEquals(180f, clamped.value, 0.01f)
    }
}
