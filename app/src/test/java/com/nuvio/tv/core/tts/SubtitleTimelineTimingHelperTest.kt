package com.nuvio.tv.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleTimelineTimingHelperTest {

    @Test
    fun `createTimingContext calculates correct playback context`() {
        val context = SubtitleTimelineTimingHelper.createTimingContext(
            rawText = "Hello world",
            startMs = 1000L,
            endMs = 3000L,
            playbackSpeed = 1.0f
        )
        assertEquals("Hello world", context.currentCue.rawText)
        assertEquals(1000L, context.currentCue.startMs)
        assertEquals(3000L, context.currentCue.endMs)
        assertEquals(1.0f, context.playbackSpeed, 0.001f)
    }

    @Test
    fun `fromMpvProperties handles null or invalid seconds gracefully`() {
        assertNull(SubtitleTimelineTimingHelper.fromMpvProperties("test", null, 5.0))
        assertNull(SubtitleTimelineTimingHelper.fromMpvProperties("test", 2.0, null))
        assertNull(SubtitleTimelineTimingHelper.fromMpvProperties("test", 5.0, 2.0))
        assertNull(SubtitleTimelineTimingHelper.fromMpvProperties("test", Double.NaN, 2.0))
    }

    @Test
    fun `fromMpvProperties applies sub-delay and speed correctly`() {
        val context = SubtitleTimelineTimingHelper.fromMpvProperties(
            rawText = "Subtitle from MPV",
            subStartSec = 10.5,
            subEndSec = 14.5,
            subDelaySec = 0.5,
            speed = 2.0
        )
        assertNotNull(context)
        assertEquals("Subtitle from MPV", context!!.currentCue.rawText)
        assertEquals(11000L, context.currentCue.startMs)
        assertEquals(15000L, context.currentCue.endMs)
        assertEquals(2.0f, context.playbackSpeed, 0.001f)
    }
}
