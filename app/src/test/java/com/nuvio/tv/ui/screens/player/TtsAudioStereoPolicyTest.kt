package com.nuvio.tv.ui.screens.player

import androidx.media3.common.MimeTypes
import com.nuvio.tv.core.player.SurroundFormatResolver
import com.nuvio.tv.core.tts.toSubtitleTtsConfig
import com.nuvio.tv.data.local.AudioOutputChannels
import com.nuvio.tv.data.local.PlayerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsAudioStereoPolicyTest {

    @Test
    fun `tts off preserves original multichannel and optical passthrough settings`() {
        val settings = PlayerSettings(
            downmixEnabled = false,
            audioOutputChannels = AudioOutputChannels.CHANNELS_5_1,
            forceOpticalPassthrough = true,
            decoderPriority = 2,
            ttsEnabled = false,
            ttsAutoStereoDownmix = true
        )

        val config = TtsAudioStereoPolicy.resolveEffectiveOutput(
            isBluetooth = false,
            ttsEnabled = false,
            ttsAutoStereoDownmix = true,
            settings = settings
        )

        assertFalse(config.forceStereoForTts)
        assertFalse(config.forceStereoPcm)
        assertTrue(config.forceOpticalPassthrough)
        assertFalse(config.downmixEnabled)
        assertEquals(AudioOutputChannels.CHANNELS_5_1, config.audioOutputChannels)
        assertFalse(config.ffmpegDownmixEnabled)
        assertEquals(AudioOutputChannels.CHANNELS_5_1, config.ffmpegAudioOutputChannels)
        assertFalse(config.disableTunneling)
        assertFalse(config.requiresRebuildForSoftwareDecoder)
    }

    @Test
    fun `tts on with autoStereoDownmix enabled forces 2_0 stereo and pauses optical and tunneling`() {
        val settings = PlayerSettings(
            downmixEnabled = false,
            audioOutputChannels = AudioOutputChannels.CHANNELS_7_1,
            forceOpticalPassthrough = true,
            decoderPriority = 2,
            ttsEnabled = true,
            ttsAutoStereoDownmix = true
        )

        val config = TtsAudioStereoPolicy.resolveEffectiveOutput(
            isBluetooth = false,
            ttsEnabled = true,
            ttsAutoStereoDownmix = true,
            settings = settings
        )

        assertTrue(config.forceStereoForTts)
        assertTrue(config.forceStereoPcm)
        assertFalse(config.forceOpticalPassthrough)
        assertTrue(config.downmixEnabled)
        assertEquals(AudioOutputChannels.CHANNELS_2_0, config.audioOutputChannels)
        // Non-BT keeps FFmpeg layout intact so disabling TTS restores native 7.1 PCM cleanly.
        assertFalse(config.ffmpegDownmixEnabled)
        assertEquals(AudioOutputChannels.CHANNELS_7_1, config.ffmpegAudioOutputChannels)
        assertTrue(config.deniedTranscodeMimes.isEmpty())
        assertTrue(config.disableTunneling)
        assertFalse(config.requiresRebuildForSoftwareDecoder)
    }

    @Test
    fun `tts on with autoStereoDownmix disabled respects user preference and keeps original output`() {
        val settings = PlayerSettings(
            downmixEnabled = false,
            audioOutputChannels = AudioOutputChannels.CHANNELS_5_1,
            forceOpticalPassthrough = true,
            decoderPriority = 2
        )

        val config = TtsAudioStereoPolicy.resolveEffectiveOutput(
            isBluetooth = false,
            ttsEnabled = true,
            ttsAutoStereoDownmix = false,
            settings = settings
        )

        assertFalse(config.forceStereoForTts)
        assertFalse(config.forceStereoPcm)
        assertTrue(config.forceOpticalPassthrough)
        assertFalse(config.downmixEnabled)
        assertEquals(AudioOutputChannels.CHANNELS_5_1, config.audioOutputChannels)
        assertFalse(config.disableTunneling)
    }

    @Test
    fun `disabling tts restores lastSurround resolution and deniedTranscodeMimes`() {
        val settings = PlayerSettings(
            downmixEnabled = false,
            audioOutputChannels = AudioOutputChannels.CHANNELS_7_1,
            forceOpticalPassthrough = false,
            decoderPriority = 1
        )
        val lastSurround = SurroundResolveResult(
            resolution = SurroundFormatResolver.Resolution.INERT,
            targetChannels = 6,
            downmixEnabled = true,
            audioOutputChannels = AudioOutputChannels.CHANNELS_5_1,
            deniedTranscodeMimes = setOf(MimeTypes.AUDIO_DTS)
        )

        val activeConfig = TtsAudioStereoPolicy.resolveEffectiveOutput(
            isBluetooth = false,
            ttsEnabled = true,
            ttsAutoStereoDownmix = true,
            settings = settings,
            lastSurround = lastSurround
        )
        assertTrue(activeConfig.forceStereoForTts)
        assertEquals(AudioOutputChannels.CHANNELS_2_0, activeConfig.audioOutputChannels)
        assertTrue(activeConfig.deniedTranscodeMimes.isEmpty())

        val restoredConfig = TtsAudioStereoPolicy.resolveEffectiveOutput(
            isBluetooth = false,
            ttsEnabled = false,
            ttsAutoStereoDownmix = true,
            settings = settings,
            lastSurround = lastSurround
        )
        assertFalse(restoredConfig.forceStereoForTts)
        assertFalse(restoredConfig.forceStereoPcm)
        assertTrue(restoredConfig.downmixEnabled)
        assertEquals(AudioOutputChannels.CHANNELS_5_1, restoredConfig.audioOutputChannels)
        assertEquals(setOf(MimeTypes.AUDIO_DTS), restoredConfig.deniedTranscodeMimes)
    }

    @Test
    fun `bluetooth output stays 2_0 stereo PCM even after tts is turned off`() {
        val settings = PlayerSettings(
            downmixEnabled = false,
            audioOutputChannels = AudioOutputChannels.CHANNELS_5_1,
            forceOpticalPassthrough = true,
            decoderPriority = 2
        )

        val config = TtsAudioStereoPolicy.resolveEffectiveOutput(
            isBluetooth = true,
            ttsEnabled = false,
            ttsAutoStereoDownmix = true,
            settings = settings
        )

        assertFalse(config.forceStereoForTts)
        assertTrue(config.forceStereoPcm)
        assertFalse(config.forceOpticalPassthrough)
        assertTrue(config.downmixEnabled)
        assertEquals(AudioOutputChannels.CHANNELS_2_0, config.audioOutputChannels)
        assertTrue(config.ffmpegDownmixEnabled)
        assertEquals(AudioOutputChannels.CHANNELS_2_0, config.ffmpegAudioOutputChannels)
    }

    @Test
    fun `decoderPriority zero with TrueHD or DTS requires software decoder rebuild only when tts forces stereo`() {
        val settings = PlayerSettings(decoderPriority = 0)

        val trueHdRebuild = TtsAudioStereoPolicy.resolveEffectiveOutput(
            isBluetooth = false,
            ttsEnabled = true,
            ttsAutoStereoDownmix = true,
            settings = settings,
            cachedDecoderPriority = 0,
            activeAudioMimeType = MimeTypes.AUDIO_TRUEHD
        )
        assertTrue(trueHdRebuild.requiresRebuildForSoftwareDecoder)

        val dtsRebuild = TtsAudioStereoPolicy.resolveEffectiveOutput(
            isBluetooth = false,
            ttsEnabled = true,
            ttsAutoStereoDownmix = true,
            settings = settings,
            cachedDecoderPriority = 0,
            activeAudioMimeType = MimeTypes.AUDIO_DTS_HD
        )
        assertTrue(dtsRebuild.requiresRebuildForSoftwareDecoder)

        val ac3NoRebuild = TtsAudioStereoPolicy.resolveEffectiveOutput(
            isBluetooth = false,
            ttsEnabled = true,
            ttsAutoStereoDownmix = true,
            settings = settings,
            cachedDecoderPriority = 0,
            activeAudioMimeType = MimeTypes.AUDIO_AC3
        )
        assertFalse(ac3NoRebuild.requiresRebuildForSoftwareDecoder)

        val ffmpegPresentNoRebuild = TtsAudioStereoPolicy.resolveEffectiveOutput(
            isBluetooth = false,
            ttsEnabled = true,
            ttsAutoStereoDownmix = true,
            settings = settings,
            cachedDecoderPriority = 1,
            activeAudioMimeType = MimeTypes.AUDIO_TRUEHD
        )
        assertFalse(ffmpegPresentNoRebuild.requiresRebuildForSoftwareDecoder)
    }

    @Test
    fun `player settings maps ttsAutoStereoDownmix to SubtitleTtsConfig with default enabled`() {
        val defaultConfig = PlayerSettings().toSubtitleTtsConfig()
        assertTrue(defaultConfig.autoStereoDownmix)
        assertFalse(defaultConfig.shouldForceStereoDownmix)

        val activeConfig = PlayerSettings(ttsEnabled = true).toSubtitleTtsConfig()
        assertTrue(activeConfig.shouldForceStereoDownmix)

        val optedOutConfig = PlayerSettings(
            ttsEnabled = true,
            ttsAutoStereoDownmix = false
        ).toSubtitleTtsConfig()
        assertFalse(optedOutConfig.autoStereoDownmix)
        assertFalse(optedOutConfig.shouldForceStereoDownmix)
    }
}
