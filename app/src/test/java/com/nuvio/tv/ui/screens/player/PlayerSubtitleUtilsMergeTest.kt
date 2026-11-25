package com.nuvio.tv.ui.screens.player

import androidx.media3.common.text.Cue
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSubtitleUtilsMergeTest {

    private val dialogue = Cue.Builder().setText("Dialogue").build()
    private val bottomLeftSign = Cue.Builder().setText("Sign")
        .setPosition(0.08f).setPositionAnchor(Cue.ANCHOR_TYPE_START).build()

    @Test
    fun `a side cue does not pull the dialogue merged with it to the side`() {
        val merged = PlayerSubtitleUtils.mergeOverlappingCues(listOf(bottomLeftSign, dialogue)).single()

        assertEquals("Sign\nDialogue", merged.text.toString())
        assertEquals(Cue.DIMEN_UNSET, merged.position)
    }

    @Test
    fun `merged cues without a centered one are centered`() {
        val bottomRightSign = Cue.Builder().setText("Other sign")
            .setPosition(0.92f).setPositionAnchor(Cue.ANCHOR_TYPE_END).build()

        val merged = PlayerSubtitleUtils.mergeOverlappingCues(listOf(bottomLeftSign, bottomRightSign)).single()

        assertEquals(Cue.DIMEN_UNSET, merged.position)
        assertEquals(Cue.TYPE_UNSET, merged.positionAnchor)
    }

    @Test
    fun `cues sharing a position keep it`() {
        val secondLeft = Cue.Builder().setText("Second")
            .setPosition(0.08f).setPositionAnchor(Cue.ANCHOR_TYPE_START).build()

        val merged = PlayerSubtitleUtils.mergeOverlappingCues(listOf(bottomLeftSign, secondLeft)).single()

        assertEquals(0.08f, merged.position, 0f)
        assertEquals(Cue.ANCHOR_TYPE_START, merged.positionAnchor)
    }

    @Test
    fun `a side cue shown alone keeps its side`() {
        assertEquals(listOf(bottomLeftSign), PlayerSubtitleUtils.mergeOverlappingCues(listOf(bottomLeftSign)))
    }

    @Test
    fun `identical overlapping spanned cues are deduplicated into a single line`() {
        fun fakeSpanned(value: String): android.text.Spanned = object : android.text.Spanned, CharSequence by value {
            override fun <T : Any?> getSpans(start: Int, end: Int, type: Class<T>?): Array<T> =
                @Suppress("UNCHECKED_CAST") (emptyArray<Any>() as Array<T>)
            override fun getSpanStart(tag: Any?): Int = -1
            override fun getSpanEnd(tag: Any?): Int = -1
            override fun getSpanFlags(tag: Any?): Int = 0
            override fun nextSpanTransition(start: Int, limit: Int, type: Class<*>?): Int = limit
            override fun toString(): String = value
        }

        val merged = PlayerSubtitleUtils.mergeOverlappingCueTexts(
            listOf(fakeSpanned("Xin chào"), fakeSpanned("Xin chào")),
        )
        assertEquals("Xin chào", merged.toString())
    }
}
