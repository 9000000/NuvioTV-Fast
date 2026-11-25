package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Universal PCM stereo (2.0) downmix [AudioProcessor] placed at the head of ExoPlayer's
 * [androidx.media3.exoplayer.audio.DefaultAudioSink] chain.
 *
 * Guarantees multichannel PCM decoded by either platform MediaCodec or bundled FFmpeg
 * (3.0, 4.0, 5.0, 5.1, 6.1, 7.1+) is downmixed to 2.0 Stereo PCM whenever subtitle TTS
 * auto-stereo mode is active, so Android's AudioFlinger can reliably mix Google TTS speech
 * and apply movie ducking without restarting video playback.
 *
 * When disabled or when input audio is already mono/stereo (<= 2 channels), [onConfigure]
 * returns [AudioProcessor.AudioFormat.NOT_SET] for zero runtime overhead.
 */
internal class TtsStereoDownmixAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var enabled: Boolean = false

    fun setEnabled(enabled: Boolean): Boolean {
        if (this.enabled == enabled) return false
        this.enabled = enabled
        return true
    }

    fun isEnabled(): Boolean = enabled

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!enabled) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount <= STEREO_CHANNELS) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT -> AudioProcessor.AudioFormat(
                inputAudioFormat.sampleRate,
                STEREO_CHANNELS,
                inputAudioFormat.encoding
            )
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val inChannels = inputAudioFormat.channelCount
        val bytesPerSample = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        val frameSizeBytes = inChannels * bytesPerSample
        if (frameSizeBytes <= 0) return

        val frameCount = inputBuffer.remaining() / frameSizeBytes
        if (frameCount <= 0) return

        val outputSize = frameCount * STEREO_CHANNELS * bytesPerSample
        val outputBuffer = replaceOutputBuffer(outputSize)
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> processPcm16(inputBuffer, outputBuffer, inChannels, frameCount)
            C.ENCODING_PCM_FLOAT -> processPcmFloat(inputBuffer, outputBuffer, inChannels, frameCount)
        }

        outputBuffer.flip()
    }

    private fun processPcm16(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        inChannels: Int,
        frameCount: Int
    ) {
        val norm = normalizationScaleFor(inChannels)
        for (frame in 0 until frameCount) {
            var left = 0f
            var right = 0f
            when (inChannels) {
                3 -> {
                    val fl = inputBuffer.short.toFloat()
                    val fr = inputBuffer.short.toFloat()
                    val fc = inputBuffer.short.toFloat()
                    left = (fl + CENTER_GAIN * fc) * norm
                    right = (fr + CENTER_GAIN * fc) * norm
                }
                4 -> {
                    val fl = inputBuffer.short.toFloat()
                    val fr = inputBuffer.short.toFloat()
                    val bl = inputBuffer.short.toFloat()
                    val br = inputBuffer.short.toFloat()
                    left = (fl + SURROUND_GAIN * bl) * norm
                    right = (fr + SURROUND_GAIN * br) * norm
                }
                5 -> {
                    val fl = inputBuffer.short.toFloat()
                    val fr = inputBuffer.short.toFloat()
                    val fc = inputBuffer.short.toFloat()
                    val bl = inputBuffer.short.toFloat()
                    val br = inputBuffer.short.toFloat()
                    left = (fl + CENTER_GAIN * fc + SURROUND_GAIN * bl) * norm
                    right = (fr + CENTER_GAIN * fc + SURROUND_GAIN * br) * norm
                }
                6 -> {
                    // ITU-R BS.775 5.1 layout: FL, FR, FC, LFE (omitted for clean narration), SL/BL, SR/BR
                    val fl = inputBuffer.short.toFloat()
                    val fr = inputBuffer.short.toFloat()
                    val fc = inputBuffer.short.toFloat()
                    inputBuffer.short // skip LFE
                    val sl = inputBuffer.short.toFloat()
                    val sr = inputBuffer.short.toFloat()
                    left = (fl + CENTER_GAIN * fc + SURROUND_GAIN * sl) * norm
                    right = (fr + CENTER_GAIN * fc + SURROUND_GAIN * sr) * norm
                }
                7 -> {
                    // 6.1 layout: FL, FR, FC, LFE, SL, SR, BC
                    val fl = inputBuffer.short.toFloat()
                    val fr = inputBuffer.short.toFloat()
                    val fc = inputBuffer.short.toFloat()
                    inputBuffer.short // skip LFE
                    val sl = inputBuffer.short.toFloat()
                    val sr = inputBuffer.short.toFloat()
                    val bc = inputBuffer.short.toFloat()
                    left = (fl + CENTER_GAIN * fc + SURROUND_GAIN * sl + BACK_SURROUND_GAIN * bc) * norm
                    right = (fr + CENTER_GAIN * fc + SURROUND_GAIN * sr + BACK_SURROUND_GAIN * bc) * norm
                }
                8 -> {
                    // 7.1 layout: FL, FR, FC, LFE, BL, BR, SL, SR
                    val fl = inputBuffer.short.toFloat()
                    val fr = inputBuffer.short.toFloat()
                    val fc = inputBuffer.short.toFloat()
                    inputBuffer.short // skip LFE
                    val bl = inputBuffer.short.toFloat()
                    val br = inputBuffer.short.toFloat()
                    val sl = inputBuffer.short.toFloat()
                    val sr = inputBuffer.short.toFloat()
                    left = (fl + CENTER_GAIN * fc + SURROUND_GAIN * sl + BACK_SURROUND_GAIN * bl) * norm
                    right = (fr + CENTER_GAIN * fc + SURROUND_GAIN * sr + BACK_SURROUND_GAIN * br) * norm
                }
                else -> {
                    val fl = inputBuffer.short.toFloat()
                    val fr = inputBuffer.short.toFloat()
                    val fc = inputBuffer.short.toFloat()
                    left = fl + CENTER_GAIN * fc
                    right = fr + CENTER_GAIN * fc
                    for (ch in 3 until inChannels) {
                        val sample = inputBuffer.short.toFloat()
                        if (ch == 3) continue // omit LFE
                        if (ch % 2 == 0) {
                            left += BACK_SURROUND_GAIN * sample
                        } else {
                            right += BACK_SURROUND_GAIN * sample
                        }
                    }
                    left *= norm
                    right *= norm
                }
            }
            outputBuffer.putShort(left.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            outputBuffer.putShort(right.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
    }

    private fun processPcmFloat(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        inChannels: Int,
        frameCount: Int
    ) {
        val norm = normalizationScaleFor(inChannels)
        for (frame in 0 until frameCount) {
            var left = 0f
            var right = 0f
            when (inChannels) {
                3 -> {
                    val fl = inputBuffer.float
                    val fr = inputBuffer.float
                    val fc = inputBuffer.float
                    left = (fl + CENTER_GAIN * fc) * norm
                    right = (fr + CENTER_GAIN * fc) * norm
                }
                4 -> {
                    val fl = inputBuffer.float
                    val fr = inputBuffer.float
                    val bl = inputBuffer.float
                    val br = inputBuffer.float
                    left = (fl + SURROUND_GAIN * bl) * norm
                    right = (fr + SURROUND_GAIN * br) * norm
                }
                5 -> {
                    val fl = inputBuffer.float
                    val fr = inputBuffer.float
                    val fc = inputBuffer.float
                    val bl = inputBuffer.float
                    val br = inputBuffer.float
                    left = (fl + CENTER_GAIN * fc + SURROUND_GAIN * bl) * norm
                    right = (fr + CENTER_GAIN * fc + SURROUND_GAIN * br) * norm
                }
                6 -> {
                    val fl = inputBuffer.float
                    val fr = inputBuffer.float
                    val fc = inputBuffer.float
                    inputBuffer.float // skip LFE
                    val sl = inputBuffer.float
                    val sr = inputBuffer.float
                    left = (fl + CENTER_GAIN * fc + SURROUND_GAIN * sl) * norm
                    right = (fr + CENTER_GAIN * fc + SURROUND_GAIN * sr) * norm
                }
                7 -> {
                    val fl = inputBuffer.float
                    val fr = inputBuffer.float
                    val fc = inputBuffer.float
                    inputBuffer.float // skip LFE
                    val sl = inputBuffer.float
                    val sr = inputBuffer.float
                    val bc = inputBuffer.float
                    left = (fl + CENTER_GAIN * fc + SURROUND_GAIN * sl + BACK_SURROUND_GAIN * bc) * norm
                    right = (fr + CENTER_GAIN * fc + SURROUND_GAIN * sr + BACK_SURROUND_GAIN * bc) * norm
                }
                8 -> {
                    val fl = inputBuffer.float
                    val fr = inputBuffer.float
                    val fc = inputBuffer.float
                    inputBuffer.float // skip LFE
                    val bl = inputBuffer.float
                    val br = inputBuffer.float
                    val sl = inputBuffer.float
                    val sr = inputBuffer.float
                    left = (fl + CENTER_GAIN * fc + SURROUND_GAIN * sl + BACK_SURROUND_GAIN * bl) * norm
                    right = (fr + CENTER_GAIN * fc + SURROUND_GAIN * sr + BACK_SURROUND_GAIN * br) * norm
                }
                else -> {
                    val fl = inputBuffer.float
                    val fr = inputBuffer.float
                    val fc = inputBuffer.float
                    left = fl + CENTER_GAIN * fc
                    right = fr + CENTER_GAIN * fc
                    for (ch in 3 until inChannels) {
                        val sample = inputBuffer.float
                        if (ch == 3) continue // omit LFE
                        if (ch % 2 == 0) {
                            left += BACK_SURROUND_GAIN * sample
                        } else {
                            right += BACK_SURROUND_GAIN * sample
                        }
                    }
                    left *= norm
                    right *= norm
                }
            }
            outputBuffer.putFloat(left.coerceIn(-1f, 1f))
            outputBuffer.putFloat(right.coerceIn(-1f, 1f))
        }
    }

    companion object {
        internal const val STEREO_CHANNELS = 2
        internal const val CENTER_GAIN = 0.70710677f // -3 dB (1 / sqrt(2))
        internal const val SURROUND_GAIN = 0.70710677f // -3 dB
        internal const val BACK_SURROUND_GAIN = 0.5f // -6 dB

        internal fun normalizationScaleFor(inChannels: Int): Float = when (inChannels) {
            3, 4 -> 1f / (1f + CENTER_GAIN)
            5, 6 -> 1f / (1f + CENTER_GAIN + SURROUND_GAIN)
            7, 8 -> 1f / (1f + CENTER_GAIN + SURROUND_GAIN + BACK_SURROUND_GAIN)
            else -> 1f / (1f + CENTER_GAIN + (inChannels - 3).coerceAtLeast(1) * 0.25f)
        }
    }
}
