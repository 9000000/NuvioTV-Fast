package com.nuvio.tv.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TrailerVideoPolicyTest {

    @Test
    fun `4k trailers default to off so all devices target 1080p`() {
        assertFalse(TrailerVideoPolicy.default4kTrailers(null))
    }

    @Test
    fun `max height and width lock to 1920x1080 when 4k is off`() {
        val maxHeight = TrailerVideoPolicy.maxTrailerVideoHeight(allow4k = false)
        assertEquals(1080, maxHeight)
        assertEquals(1920, TrailerVideoPolicy.maxTrailerVideoWidth(maxHeight))
    }

    @Test
    fun `max height and width are uncapped when 4k is enabled`() {
        val maxHeight = TrailerVideoPolicy.maxTrailerVideoHeight(allow4k = true)
        assertEquals(Int.MAX_VALUE, maxHeight)
        assertEquals(Int.MAX_VALUE, TrailerVideoPolicy.maxTrailerVideoWidth(maxHeight))
    }
}
