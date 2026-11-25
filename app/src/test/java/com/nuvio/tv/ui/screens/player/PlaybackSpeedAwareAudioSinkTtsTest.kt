package com.nuvio.tv.ui.screens.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class PlaybackSpeedAwareAudioSinkTtsTest {

    @Test
    fun `toggling tts force stereo pcm rejects passthrough and restores original bitstream support`() {
        val delegate = RecordingSink()
        val sink = PlaybackSpeedAwareAudioSink(sink = delegate)

        val bitstreamMimes = listOf(
            MimeTypes.AUDIO_AC3,
            MimeTypes.AUDIO_E_AC3,
            MimeTypes.AUDIO_E_AC3_JOC,
            MimeTypes.AUDIO_TRUEHD,
            MimeTypes.AUDIO_DTS,
            MimeTypes.AUDIO_DTS_HD
        )

        for (mime in bitstreamMimes) {
            val fmt = surroundFormat(mime)
            assertFalse(mime, sink.shouldForcePcmForFormat(fmt))
            assertEquals(mime, AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, sink.getFormatSupport(fmt))
            assertTrue(mime, sink.getFormatOffloadSupport(fmt).isFormatSupported)
        }

        assertTrue(sink.setTtsForceStereoPcm(true))
        assertTrue(sink.isTtsForceStereoPcm())

        for (mime in bitstreamMimes) {
            val fmt = surroundFormat(mime)
            assertTrue(mime, sink.shouldForcePcmForFormat(fmt))
            assertEquals(mime, AudioSink.SINK_FORMAT_UNSUPPORTED, sink.getFormatSupport(fmt))
            assertEquals(mime, AudioOffloadSupport.DEFAULT_UNSUPPORTED, sink.getFormatOffloadSupport(fmt))
        }

        // Disable TTS: original passthrough capabilities must come back immediately.
        assertTrue(sink.setTtsForceStereoPcm(false))
        assertFalse(sink.isTtsForceStereoPcm())

        for (mime in bitstreamMimes) {
            val fmt = surroundFormat(mime)
            assertFalse(mime, sink.shouldForcePcmForFormat(fmt))
            assertEquals(mime, AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, sink.getFormatSupport(fmt))
            assertTrue(mime, sink.getFormatOffloadSupport(fmt).isFormatSupported)
        }
    }

    @Test
    fun `starting playback with forcePcmForTts true still restores passthrough when tts turns off`() {
        val delegate = RecordingSink()
        val sink = PlaybackSpeedAwareAudioSink(
            sink = delegate,
            initialForcePcm = false,
            forcePcmForBluetooth = false,
            forcePcmForTts = true
        )
        val eac3 = surroundFormat(MimeTypes.AUDIO_E_AC3)

        assertTrue(sink.shouldForcePcmForFormat(eac3))
        sink.configure(eac3, 0, null)
        assertFalse(sink.isDirectPlaybackActive())

        assertTrue(sink.setTtsForceStereoPcm(false))
        assertFalse(sink.shouldForcePcmForFormat(eac3))
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, sink.getFormatSupport(eac3))
    }

    @Test
    fun `bluetooth force pcm remains active when tts turns off`() {
        val delegate = RecordingSink()
        val sink = PlaybackSpeedAwareAudioSink(
            sink = delegate,
            forcePcmForBluetooth = true,
            forcePcmForTts = true
        )
        val ac3 = surroundFormat(MimeTypes.AUDIO_AC3)

        assertTrue(sink.shouldForcePcmForFormat(ac3))
        assertTrue(sink.setTtsForceStereoPcm(false))
        assertTrue(sink.shouldForcePcmForFormat(ac3))
        assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED, sink.getFormatSupport(ac3))
    }

    @Test
    fun `returning speed to 1x keeps pcm forced while tts stereo is active`() {
        val delegate = RecordingSink()
        val sink = PlaybackSpeedAwareAudioSink(sink = delegate)
        val ac3 = surroundFormat(MimeTypes.AUDIO_AC3)

        sink.setTtsForceStereoPcm(true)
        sink.setPlaybackParameters(PlaybackParameters(1.25f))
        assertTrue(sink.shouldForcePcmForFormat(ac3))

        sink.setPlaybackParameters(PlaybackParameters(1.0f))
        assertTrue(sink.shouldForcePcmForFormat(ac3))

        sink.setTtsForceStereoPcm(false)
        assertFalse(sink.shouldForcePcmForFormat(ac3))
    }

    @Test
    fun `requestPcmReconfigureForAudioProcessorChange reconfigures active multichannel pcm once`() {
        val delegate = RecordingSink()
        val sink = PlaybackSpeedAwareAudioSink(sink = delegate)
        val pcm51 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setChannelCount(6)
            .setSampleRate(48_000)
            .build()

        sink.configure(pcm51, 4096, null)
        sink.play()
        assertEquals(1, delegate.configureCount)
        assertEquals(0, delegate.flushCount)
        assertEquals(1, delegate.playCount)

        sink.requestPcmReconfigureForAudioProcessorChange()
        sink.handleBuffer(ByteBuffer.allocate(64), 1_000L, 1)

        assertEquals(2, delegate.configureCount)
        assertEquals(1, delegate.flushCount)
        assertEquals(2, delegate.playCount)

        // Subsequent buffers must not trigger redundant reconfigures.
        sink.handleBuffer(ByteBuffer.allocate(64), 2_000L, 1)
        assertEquals(2, delegate.configureCount)
        assertEquals(1, delegate.flushCount)
    }

    @Test
    fun `requestPcmReconfigureForAudioProcessorChange ignores stereo pcm`() {
        val delegate = RecordingSink()
        val sink = PlaybackSpeedAwareAudioSink(sink = delegate)
        val pcm20 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setChannelCount(2)
            .setSampleRate(48_000)
            .build()

        sink.configure(pcm20, 4096, null)
        sink.play()
        sink.requestPcmReconfigureForAudioProcessorChange()
        sink.handleBuffer(ByteBuffer.allocate(64), 1_000L, 1)

        assertEquals(1, delegate.configureCount)
        assertEquals(0, delegate.flushCount)
    }

    private fun surroundFormat(mime: String): Format = Format.Builder()
        .setSampleMimeType(mime)
        .setChannelCount(6)
        .setSampleRate(48_000)
        .build()

    private class RecordingSink : AudioSink {
        var configureCount = 0
        var flushCount = 0
        var playCount = 0

        override fun setListener(listener: AudioSink.Listener) = Unit
        override fun supportsFormat(format: Format): Boolean = true
        override fun getFormatSupport(format: Format): Int = AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport =
            AudioOffloadSupport.Builder().setIsFormatSupported(true).build()
        override fun getCurrentPositionUs(sourceEnded: Boolean): Long = 0L
        override fun getAudioTrackBufferSizeUs(): Long = C.TIME_UNSET
        override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
            configureCount++
        }
        override fun play() {
            playCount++
        }
        override fun handleDiscontinuity() = Unit
        override fun handleBuffer(
            buffer: ByteBuffer,
            presentationTimeUs: Long,
            encodedAccessUnitCount: Int
        ): Boolean {
            buffer.position(buffer.limit())
            return true
        }
        override fun playToEndOfStream() = Unit
        override fun isEnded(): Boolean = false
        override fun hasPendingData(): Boolean = false
        override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit
        override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
        override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = Unit
        override fun getSkipSilenceEnabled(): Boolean = false
        override fun setAudioAttributes(audioAttributes: AudioAttributes) = Unit
        override fun getAudioAttributes(): AudioAttributes? = null
        override fun setAudioSessionId(audioSessionId: Int) = Unit
        override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = Unit
        override fun enableTunnelingV21() = Unit
        override fun disableTunneling() = Unit
        override fun setVolume(volume: Float) = Unit
        override fun pause() = Unit
        override fun flush() {
            flushCount++
        }
        override fun reset() = Unit
        override fun release() = Unit
    }
}
