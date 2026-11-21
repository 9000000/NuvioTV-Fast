package com.nuvio.tv.ui.screens.player

/**
 * Pure policy calculator for background movie audio ducking while subtitle TTS is speaking.
 *
 * Rules:
 * 1. When TTS is not speaking, volume scale is always 1.0f (no attenuation).
 * 2. When TTS is speaking and direct bitstream passthrough (IEC/optical) is active,
 *    software PCM ducking is bypassed (scale 1.0f) to avoid audio corruption or renderer crashes.
 * 3. When TTS is speaking and audio is decoded locally, the movie volume scale is clamped
 *    between [MIN_DUCKING_VOLUME_SCALE, MAX_DUCKING_VOLUME_SCALE].
 */
object PlayerTtsVolumeDuckingPolicy {
    const val DEFAULT_DUCKING_VOLUME_SCALE = 0.30f
    const val MIN_DUCKING_VOLUME_SCALE = 0.05f
    const val MAX_DUCKING_VOLUME_SCALE = 1.00f

    fun resolveDuckingVolumeScale(
        isSpeaking: Boolean,
        isDirectPassthroughActive: Boolean,
        configuredMovieVolume: Float
    ): Float {
        if (!isSpeaking) return 1.0f
        if (isDirectPassthroughActive) return 1.0f
        return configuredMovieVolume.coerceIn(MIN_DUCKING_VOLUME_SCALE, MAX_DUCKING_VOLUME_SCALE)
    }
}
