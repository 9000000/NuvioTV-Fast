package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

class TtsStereoDownmixAudioProcessorTest {

    @Test
    fun `disabled processor bypasses multichannel input`() {
        val processor = TtsStereoDownmixAudioProcessor()
        val format51 = AudioProcessor.AudioFormat(48_000, 6, C.ENCODING_PCM_16BIT)

        val outputFormat = processor.configure(format51)
        processor.flush()

        assertEquals(AudioProcessor.AudioFormat.NOT_SET, outputFormat)
        assertFalse(processor.isActive)
    }

    @Test
    fun `enabled processor bypasses mono and stereo input`() {
        val processor = TtsStereoDownmixAudioProcessor()
        assertTrue(processor.setEnabled(true))
        assertFalse(processor.setEnabled(true))

        for (channels in listOf(1, 2)) {
            val format = AudioProcessor.AudioFormat(48_000, channels, C.ENCODING_PCM_16BIT)
            val outputFormat = processor.configure(format)
            processor.flush()
            assertEquals("channels=$channels", AudioProcessor.AudioFormat.NOT_SET, outputFormat)
            assertFalse("channels=$channels", processor.isActive)
        }
    }

    @Test
    fun `enabled processor downmixes 5_1 pcm16 to stereo preserving center and omitting lfe`() {
        val processor = TtsStereoDownmixAudioProcessor()
        processor.setEnabled(true)

        val format51 = AudioProcessor.AudioFormat(48_000, 6, C.ENCODING_PCM_16BIT)
        val outputFormat = processor.configure(format51)
        processor.flush()

        assertTrue(processor.isActive)
        assertEquals(2, outputFormat.channelCount)
        assertEquals(C.ENCODING_PCM_16BIT, outputFormat.encoding)
        assertEquals(48_000, outputFormat.sampleRate)

        val norm = TtsStereoDownmixAudioProcessor.normalizationScaleFor(6)
        // Frame 0: center-only dialogue (FL=0, FR=0, FC=20000, LFE=30000, SL=0, SR=0)
        // Frame 1: left-only + right-surround (FL=16000, FR=0, FC=0, LFE=0, SL=0, SR=10000)
        val inputSamples = shortArrayOf(
            0, 0, 20_000, 30_000, 0, 0,
            16_000, 0, 0, 0, 0, 10_000
        )
        val input = ByteBuffer.allocateDirect(inputSamples.size * 2).order(ByteOrder.nativeOrder())
        inputSamples.forEach { input.putShort(it) }
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(2 * 2 * 2, output.remaining())

        val f0Left = output.short.toInt()
        val f0Right = output.short.toInt()
        val expectedCenter = (20_000f * TtsStereoDownmixAudioProcessor.CENTER_GAIN * norm).roundToInt()
        assertEquals(expectedCenter, f0Left)
        assertEquals(expectedCenter, f0Right)

        val f1Left = output.short.toInt()
        val f1Right = output.short.toInt()
        assertEquals((16_000f * norm).roundToInt(), f1Left)
        assertEquals((10_000f * TtsStereoDownmixAudioProcessor.SURROUND_GAIN * norm).roundToInt(), f1Right)
    }

    @Test
    fun `enabled processor downmixes full scale 5_1 pcm16 without wraparound clipping`() {
        val processor = TtsStereoDownmixAudioProcessor()
        processor.setEnabled(true)
        processor.configure(AudioProcessor.AudioFormat(48_000, 6, C.ENCODING_PCM_16BIT))
        processor.flush()

        val input = ByteBuffer.allocateDirect(6 * 2).order(ByteOrder.nativeOrder())
        repeat(6) { input.putShort(Short.MAX_VALUE) }
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        val left = output.short.toInt()
        val right = output.short.toInt()

        assertTrue("left should be positive and saturated safely: $left", left in 32_000..Short.MAX_VALUE.toInt())
        assertTrue("right should be positive and saturated safely: $right", right in 32_000..Short.MAX_VALUE.toInt())
    }

    @Test
    fun `enabled processor downmixes 7_1 pcmFloat to stereo within valid range`() {
        val processor = TtsStereoDownmixAudioProcessor()
        processor.setEnabled(true)

        val format71 = AudioProcessor.AudioFormat(48_000, 8, C.ENCODING_PCM_FLOAT)
        val outputFormat = processor.configure(format71)
        processor.flush()

        assertTrue(processor.isActive)
        assertEquals(2, outputFormat.channelCount)
        assertEquals(C.ENCODING_PCM_FLOAT, outputFormat.encoding)

        val norm = TtsStereoDownmixAudioProcessor.normalizationScaleFor(8)
        // FL, FR, FC, LFE, BL, BR, SL, SR
        val frame = floatArrayOf(0.8f, 0.2f, 0.6f, 1.0f, 0.4f, 0.0f, 0.5f, 0.7f)
        val input = ByteBuffer.allocateDirect(frame.size * 4).order(ByteOrder.nativeOrder())
        frame.forEach { input.putFloat(it) }
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(2 * 4, output.remaining())

        val left = output.float
        val right = output.float
        val expectedLeft = (0.8f +
            TtsStereoDownmixAudioProcessor.CENTER_GAIN * 0.6f +
            TtsStereoDownmixAudioProcessor.SURROUND_GAIN * 0.5f +
            TtsStereoDownmixAudioProcessor.BACK_SURROUND_GAIN * 0.4f) * norm
        val expectedRight = (0.2f +
            TtsStereoDownmixAudioProcessor.CENTER_GAIN * 0.6f +
            TtsStereoDownmixAudioProcessor.SURROUND_GAIN * 0.7f +
            TtsStereoDownmixAudioProcessor.BACK_SURROUND_GAIN * 0.0f) * norm

        assertEquals(expectedLeft, left, 0.0005f)
        assertEquals(expectedRight, right, 0.0005f)
        assertTrue(abs(left) <= 1f)
        assertTrue(abs(right) <= 1f)
    }

    @Test
    fun `disabling processor after active stereo mode restores bypass for 5_1`() {
        val processor = TtsStereoDownmixAudioProcessor()
        val format51 = AudioProcessor.AudioFormat(48_000, 6, C.ENCODING_PCM_16BIT)

        processor.setEnabled(true)
        assertEquals(2, processor.configure(format51).channelCount)
        processor.flush()
        assertTrue(processor.isActive)

        assertTrue(processor.setEnabled(false))
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, processor.configure(format51))
        processor.flush()
        assertFalse(processor.isActive)
    }

    @Test
    fun `supports 3ch 4ch 5ch 7ch layouts and rejects unsupported encodings`() {
        val processor = TtsStereoDownmixAudioProcessor()
        processor.setEnabled(true)

        for (channels in listOf(3, 4, 5, 7)) {
            val out = processor.configure(AudioProcessor.AudioFormat(48_000, channels, C.ENCODING_PCM_16BIT))
            assertEquals("channels=$channels", 2, out.channelCount)
        }

        val unsupported = processor.configure(AudioProcessor.AudioFormat(48_000, 6, C.ENCODING_PCM_8BIT))
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, unsupported)
    }
}
