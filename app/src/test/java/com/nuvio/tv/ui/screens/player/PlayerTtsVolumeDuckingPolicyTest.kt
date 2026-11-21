package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTtsVolumeDuckingPolicyTest {

    @Test
    fun `when not speaking returns unity volume scale`() {
        val scale = PlayerTtsVolumeDuckingPolicy.resolveDuckingVolumeScale(
            isSpeaking = false,
            isDirectPassthroughActive = false,
            configuredMovieVolume = 0.2f
        )
        assertEquals(1.0f, scale, 0.001f)
    }

    @Test
    fun `when speaking with direct passthrough returns unity volume scale`() {
        val scale = PlayerTtsVolumeDuckingPolicy.resolveDuckingVolumeScale(
            isSpeaking = true,
            isDirectPassthroughActive = true,
            configuredMovieVolume = 0.2f
        )
        assertEquals(1.0f, scale, 0.001f)
    }

    @Test
    fun `when speaking without passthrough returns configured volume scale`() {
        val scale = PlayerTtsVolumeDuckingPolicy.resolveDuckingVolumeScale(
            isSpeaking = true,
            isDirectPassthroughActive = false,
            configuredMovieVolume = 0.35f
        )
        assertEquals(0.35f, scale, 0.001f)
    }

    @Test
    fun `clamps configured volume scale to safe boundaries`() {
        val lowScale = PlayerTtsVolumeDuckingPolicy.resolveDuckingVolumeScale(
            isSpeaking = true,
            isDirectPassthroughActive = false,
            configuredMovieVolume = -0.1f
        )
        assertEquals(PlayerTtsVolumeDuckingPolicy.MIN_DUCKING_VOLUME_SCALE, lowScale, 0.001f)

        val highScale = PlayerTtsVolumeDuckingPolicy.resolveDuckingVolumeScale(
            isSpeaking = true,
            isDirectPassthroughActive = false,
            configuredMovieVolume = 1.8f
        )
        assertEquals(PlayerTtsVolumeDuckingPolicy.MAX_DUCKING_VOLUME_SCALE, highScale, 0.001f)
    }
}
