package com.nuvio.tv.ui.screens.player

import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.extractor.Ac3Util
import com.nuvio.tv.core.player.AudioPassthroughPolicy
import com.nuvio.tv.ui.screens.player.iec.IecPassthroughAudioSink
import java.nio.ByteBuffer

/**
 * Audio sink wrapper that forces a decode-to-PCM path when:
 * - Playback speed != 1x for bitstream formats that cannot be tempo-adjusted in passthrough, or
 * - Bluetooth media output is active (Media3 policy: Bluetooth only supports PCM).
 *
 * Bluetooth cannot carry TrueHD / Atmos / DTS-HD passthrough. Forcing PCM lets MediaCodec/FFmpeg
 * decode to the format the BT stack actually accepts; the system then encodes to SBC/AAC/aptX/LDAC.
 */
internal class PlaybackSpeedAwareAudioSink(
    sink: AudioSink,
    initialForcePcm: Boolean = false,
    forcePcmForBluetooth: Boolean = false,
    forcePcmForTts: Boolean = false,
    passthroughPolicy: AudioPassthroughPolicy = AudioPassthroughPolicy.ALLOW_ALL,
    private val onDiagnosticEvent: ((String) -> Unit)? = null,
    // Claims AC-3 up to 5.1 whatever the platform reports, for Force AC-3 and for
    // denied formats that are transcoded.
    private val forceAc3Support: Boolean = false,
    // Test harness: refuses the first passthrough buffer of this MIME as a failed open.
    private val faultInjectRejectMime: String? = null,
    reducedStartThresholdFrames: Int = 0,
    platformSink: DefaultAudioSink? = null,
    // Encodings the platform sink was pinned to when built; null when it follows the output live.
    val pinnedOutputEncodings: Set<Int>? = null
) : ForwardingAudioSink(sink) {

    // Set when the sink is built with forcePcm (error recovery). Don't clear on speed reset.
    private val startedWithForcedPcm: Boolean = initialForcePcm

    @Volatile
    private var playbackSpeed: Float = 1f

    @Volatile
    private var forcePcmForCurrentSession: Boolean = initialForcePcm

    @Volatile
    private var bluetoothForcePcm: Boolean = forcePcmForBluetooth

    @Volatile
    private var ttsForceStereoPcm: Boolean = forcePcmForTts

    @Volatile
    private var passthroughPolicy: AudioPassthroughPolicy = passthroughPolicy

    @Volatile
    private var currentInputFormat: Format? = null

    val activeInputFormat: Format?
        get() = currentInputFormat

    @Volatile
    var currentTunnelAudioClass: String? = null
        private set

    @Volatile
    private var listener: AudioSink.Listener? = null

    private val passthroughPacer = PassthroughWaterLevelPacer(onDiagnosticEvent)
    private val iecSink: IecPassthroughAudioSink? = sink as? IecPassthroughAudioSink
    private val monitor = PlaybackAudioSinkMonitor(platformSink, iecSink, reducedStartThresholdFrames)

    @Volatile
    private var isCurrentlyPassthrough: Boolean = false

    @Volatile
    private var pendingPcmReconfigure: Boolean = false

    @Volatile
    private var sinkPlaying: Boolean = false

    private var lastConfiguredInputFormat: Format? = null
    private var lastConfiguredBufferSize: Int = 0
    private var lastConfiguredOutputChannels: IntArray? = null

    private var faultInjectFiredForCurrentConfig: Boolean = false

    private var forwardAnchorPending: Boolean = false
    private var forwardUnsyncedChunks: Int = 0
    private var forwardFirstPtsUs: Long = C.TIME_UNSET
    private var forwardLastEvaluatedPtsUs: Long = C.TIME_UNSET
    private var forwardForceResync: Boolean = false
    private var forwardArmedBy: String = "configure"

    fun setInitialPlaybackSpeed(speed: Float) {
        playbackSpeed = normalizeSpeed(speed)
        markPcmFallbackIfNeeded(currentInputFormat, playbackSpeed)
    }

    /**
     * Update Bluetooth policy without rebuilding the player.
     * Call [notifyAudioProcessingRequirementChanged] after a change so Media3 reselects
     * decode-to-PCM vs passthrough on the live renderer.
     *
     * @return true when the effective PCM/passthrough policy changed.
     */
    fun setBluetoothForcePcm(enabled: Boolean): Boolean {
        val wasBluetoothForce = bluetoothForcePcm
        val wasSessionForce = forcePcmForCurrentSession
        bluetoothForcePcm = enabled
        if (enabled) {
            forcePcmForCurrentSession = true
        } else if (!startedWithForcedPcm && !ttsForceStereoPcm && playbackSpeed == 1f) {
            // Session was not built as PCM-only; leaving Bluetooth can restore passthrough.
            forcePcmForCurrentSession = false
        }
        return wasBluetoothForce != bluetoothForcePcm || wasSessionForce != forcePcmForCurrentSession
    }

    fun isBluetoothForcePcm(): Boolean = bluetoothForcePcm

    /**
     * Update subtitle TTS auto-stereo PCM policy without rebuilding the player.
     * Call [notifyAudioProcessingRequirementChanged] after a change so Media3 reselects
     * decode-to-PCM vs passthrough on the live renderer.
     *
     * @return true when the effective PCM/passthrough policy changed.
     */
    fun setTtsForceStereoPcm(enabled: Boolean): Boolean {
        val wasTtsForce = ttsForceStereoPcm
        val wasSessionForce = forcePcmForCurrentSession
        ttsForceStereoPcm = enabled
        if (enabled) {
            forcePcmForCurrentSession = true
        } else if (!startedWithForcedPcm && !bluetoothForcePcm && playbackSpeed == 1f) {
            // Session was not built as PCM-only; disabling TTS can restore original passthrough.
            forcePcmForCurrentSession = false
        }
        return wasTtsForce != ttsForceStereoPcm || wasSessionForce != forcePcmForCurrentSession
    }

    fun isTtsForceStereoPcm(): Boolean = ttsForceStereoPcm

    fun setPassthroughPolicy(policy: AudioPassthroughPolicy): Boolean {
        if (policy == passthroughPolicy) return false
        passthroughPolicy = policy
        return true
    }

    fun isIecHbrActive(): Boolean = iecSink?.isIecActive == true

    fun isDirectPlaybackActive(): Boolean = isCurrentlyPassthrough

    fun setPlaybackActive(active: Boolean) = monitor.setPlaybackActive(active)

    fun audioClockJitter(): AudioClockJitter? = monitor.audioClockJitter()

    fun nativeAudioTrackUnderrunCount(): Int? = monitor.nativeAudioTrackUnderrunCount()

    fun sampleAudioRoute(): AudioRouteSnapshot? = monitor.sampleAudioRoute()

    fun platformOutputTimestamp(): AudioOutputTimestamp? = monitor.platformOutputTimestamp()

    fun demandsNonTunnelledVideo(format: Format): Boolean = iecSink?.claimsHbr(format) == true

    fun hbrDemandsNonTunnelledVideo(format: Format): Boolean =
        IecPassthroughAudioSink.isHbrPassthrough(format)

    fun tunnelAudioClass(format: Format): String {
        val mime = format.sampleMimeType ?: return TUNNEL_AUDIO_CLASS_PCM
        if (mime == MimeTypes.AUDIO_RAW) return TUNNEL_AUDIO_CLASS_PCM
        return if (getFormatSupport(format) == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY) {
            mime
        } else {
            TUNNEL_AUDIO_CLASS_PCM
        }
    }

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
        super.setListener(listener)
    }

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        currentInputFormat = inputFormat
        lastConfiguredInputFormat = inputFormat
        lastConfiguredBufferSize = specifiedBufferSize
        lastConfiguredOutputChannels = outputChannels?.copyOf()
        pendingPcmReconfigure = false
        passthroughPacer.onFormat(inputFormat, nowMs())
        markPcmFallbackIfNeeded(inputFormat, playbackSpeed)
        currentTunnelAudioClass = tunnelAudioClass(inputFormat)
        isCurrentlyPassthrough =
            isEncodedPassthroughCandidate(inputFormat) && !shouldRejectDirectPlayback(inputFormat)
        faultInjectFiredForCurrentConfig = false
        monitor.onTrackReset()
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
        passthroughPacer.setIecPacked(iecSink?.isIecActive == true)
        armForwardAnchor(armedBy = "configure")
    }

    override fun play() {
        sinkPlaying = true
        passthroughPacer.onPlay(nowMs())
        super.play()
    }

    override fun pause() {
        sinkPlaying = false
        passthroughPacer.onPause(nowMs())
        super.pause()
    }

    override fun flush() {
        passthroughPacer.onTimelineReset(nowMs())
        monitor.onTrackReset()
        super.flush()
        armForwardAnchor(armedBy = "flush")
    }

    override fun reset() {
        sinkPlaying = false
        pendingPcmReconfigure = false
        lastConfiguredInputFormat = null
        lastConfiguredOutputChannels = null
        passthroughPacer.onReset()
        super.reset()
    }

    override fun playToEndOfStream() {
        val iecWasActive = iecSink?.isIecActive == true
        super.playToEndOfStream()
        noteIecFallbackIfFlipped(iecWasActive)
    }

    override fun handleDiscontinuity() {
        passthroughPacer.onTimelineReset(nowMs())
        super.handleDiscontinuity()
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        if (pendingPcmReconfigure) {
            pendingPcmReconfigure = false
            val fmt = lastConfiguredInputFormat
            if (fmt != null &&
                !isCurrentlyPassthrough &&
                fmt.sampleMimeType == MimeTypes.AUDIO_RAW &&
                fmt.channelCount > TtsStereoDownmixAudioProcessor.STEREO_CHANNELS
            ) {
                runCatching {
                    super.flush()
                    super.configure(fmt, lastConfiguredBufferSize, lastConfiguredOutputChannels)
                    if (sinkPlaying) {
                        super.play()
                    }
                }.onFailure {
                    Log.w(TAG, "Failed to reconfigure PCM AudioSink in place for TTS stereo toggle: ${it.message}")
                }
            }
        }
        if (isCurrentlyPassthrough) {
            monitor.maybeApplyStartThreshold()
            maybeInjectOpenFailure()
        }
        if (forwardAnchorPending && presentationTimeUs != forwardLastEvaluatedPtsUs) {
            forwardLastEvaluatedPtsUs = presentationTimeUs
            evaluateForwardAnchor(buffer, presentationTimeUs)
        }
        val passthrough = passthroughPacer.appliesTo(currentInputFormat)
        if (passthrough &&
            !passthroughPacer.shouldAcceptBuffer(presentationTimeUs, nowMs(), playbackSpeed)
        ) {
            return false
        }
        val encodedBytes = if (currentInputFormat?.sampleMimeType != MimeTypes.AUDIO_RAW) {
            buffer.remaining()
        } else {
            0
        }
        val iecWasActive = iecSink?.isIecActive == true
        val handled = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        noteIecFallbackIfFlipped(iecWasActive)
        if (handled && passthrough) {
            passthroughPacer.onBufferAccepted(presentationTimeUs)
        }
        if (handled && encodedBytes > 0) {
            PlayerAudioBitrateMeter.record(encodedBytes, presentationTimeUs)
        }
        return handled
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val sinkPositionUs = super.getCurrentPositionUs(sourceEnded)
        monitor.onPositionRead(sinkPositionUs, playbackSpeed)
        if (!passthroughPacer.appliesTo(currentInputFormat)) {
            return sinkPositionUs
        }
        return passthroughPacer.clampPositionUs(sinkPositionUs, nowMs(), playbackSpeed)
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        playbackSpeed = normalizeSpeed(playbackParameters.speed)
        var shouldNotify = markPcmFallbackIfNeeded(currentInputFormat, playbackSpeed)
        // Going above 1x latches forcePcm for the session. Clear it when back at 1.0x
        // so passthrough can recover (unless recovery, Bluetooth, or TTS stereo requires PCM).
        if (playbackSpeed == 1f &&
            forcePcmForCurrentSession &&
            !startedWithForcedPcm &&
            !bluetoothForcePcm &&
            !ttsForceStereoPcm
        ) {
            forcePcmForCurrentSession = false
            shouldNotify = true
        }
        currentInputFormat?.let { currentTunnelAudioClass = tunnelAudioClass(it) }
        super.setPlaybackParameters(playbackParameters)
        if (shouldNotify) {
            listener?.onAudioCapabilitiesChanged()
        }
    }

    fun notifyAudioProcessingRequirementChanged() {
        listener?.onAudioCapabilitiesChanged()
    }

    /**
     * Marks an already-configured multichannel PCM sink for in-place reconfiguration on the next
     * audio buffer, so [TtsStereoDownmixAudioProcessor] toggles take effect on non-passthrough
     * 5.1/7.1 tracks (e.g., AAC 5.1, FLAC 5.1, FFmpeg PCM) without restarting video playback.
     */
    fun requestPcmReconfigureForAudioProcessorChange() {
        pendingPcmReconfigure = true
    }

    override fun getFormatSupport(format: Format): Int {
        if (shouldRejectDirectPlayback(format)) {
            return AudioSink.SINK_FORMAT_UNSUPPORTED
        }
        val support = super.getFormatSupport(format)
        if (forceAc3Support &&
            support == AudioSink.SINK_FORMAT_UNSUPPORTED &&
            format.sampleMimeType == MimeTypes.AUDIO_AC3 &&
            format.channelCount <= 6
        ) {
            return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        }
        return support
    }

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport {
        if (shouldRejectDirectPlayback(format)) {
            return AudioOffloadSupport.DEFAULT_UNSUPPORTED
        }
        return super.getFormatOffloadSupport(format)
    }

    fun shouldForcePcmForFormat(format: Format): Boolean {
        return shouldRejectDirectPlayback(format)
    }

    private fun shouldRejectDirectPlayback(format: Format): Boolean {
        if (!isEncodedPassthroughCandidate(format)) {
            return false
        }
        // Bluetooth and subtitle TTS auto-stereo: always decode to PCM so AudioFlinger can mix.
        if (bluetoothForcePcm || ttsForceStereoPcm || forcePcmForCurrentSession) {
            return true
        }
        // Non-1x speed cannot be applied to bitstream passthrough tracks.
        if (playbackSpeed != 1f) {
            return true
        }
        return isPolicyDeniedPassthrough(format)
    }

    fun isPolicyDeniedPassthrough(format: Format): Boolean {
        return passthroughPolicy.deniesPassthrough(format.sampleMimeType)
    }

    private fun markPcmFallbackIfNeeded(format: Format?, speed: Float): Boolean {
        if (format == null || !isEncodedPassthroughCandidate(format)) {
            return false
        }
        if (bluetoothForcePcm || ttsForceStereoPcm) {
            val wasForcingPcm = forcePcmForCurrentSession
            forcePcmForCurrentSession = true
            return !wasForcingPcm
        }
        if (speed == 1f) {
            return false
        }
        val wasForcingPcm = forcePcmForCurrentSession
        forcePcmForCurrentSession = true
        return !wasForcingPcm
    }

    private fun normalizeSpeed(speed: Float): Float {
        return speed.takeIf { it > 0f } ?: 1f
    }

    private fun nowMs(): Long = SystemClock.elapsedRealtime()

    /**
     * Formats that devices may try to play via passthrough/offload and that Bluetooth cannot carry.
     * Matches Media3 surround encodings that need decode-to-PCM on A2DP/LE Audio.
     */
    private fun isEncodedPassthroughCandidate(format: Format): Boolean {
        if (PassthroughWaterLevelPacer.isPassthroughMime(format.sampleMimeType)) return true
        val codecs = format.codecs
        if (codecs != null) {
            return codecs.contains("ac-3", ignoreCase = true) ||
                codecs.contains("ac-4", ignoreCase = true) ||
                codecs.contains("ec-3", ignoreCase = true) ||
                codecs.contains("dts", ignoreCase = true) ||
                codecs.contains("truehd", ignoreCase = true) ||
                codecs.contains("dtshd", ignoreCase = true)
        }
        return false
    }

    private fun maybeInjectOpenFailure() {
        val rejectMime = faultInjectRejectMime ?: return
        val format = currentInputFormat ?: return
        if (faultInjectFiredForCurrentConfig || format.sampleMimeType != rejectMime) return
        faultInjectFiredForCurrentConfig = true
        Log.w(TAG, "FAULT_INJECT: refusing passthrough AudioTrack for $rejectMime (simulated HAL rejection)")
        throw AudioSink.InitializationException(
            "AudioTrack init failed ${AudioTrack.STATE_UNINITIALIZED} fault-injected $rejectMime",
            AudioTrack.STATE_UNINITIALIZED,
            format,
            true,
            null
        )
    }

    private fun noteIecFallbackIfFlipped(iecWasActive: Boolean) {
        if (iecWasActive && iecSink?.isIecActive == false) {
            passthroughPacer.setIecPacked(false)
            armForwardAnchor(armedBy = "fallback", forceResync = true)
        }
    }

    private fun armForwardAnchor(armedBy: String, forceResync: Boolean = false) {
        forwardAnchorPending =
            currentInputFormat?.sampleMimeType == MimeTypes.AUDIO_TRUEHD &&
                iecSink?.isIecActive != true
        forwardUnsyncedChunks = 0
        forwardFirstPtsUs = C.TIME_UNSET
        forwardLastEvaluatedPtsUs = C.TIME_UNSET
        forwardForceResync = forwardAnchorPending && forceResync
        forwardArmedBy = armedBy
    }

    private fun evaluateForwardAnchor(buffer: ByteBuffer, presentationTimeUs: Long) {
        if (presentationTimeUs == C.TIME_UNSET) return
        if (forwardFirstPtsUs == C.TIME_UNSET) forwardFirstPtsUs = presentationTimeUs
        if (Ac3Util.findTrueHdSyncframeOffset(buffer) == C.INDEX_UNSET) {
            forwardUnsyncedChunks++
            return
        }
        val deltaUs = presentationTimeUs - forwardFirstPtsUs
        val resynced = forwardUnsyncedChunks > 0 || forwardForceResync
        if (resynced) {
            handleDiscontinuity()
        }
        val line = "forward_anchor mime=true-hd droppedChunks=$forwardUnsyncedChunks " +
            "deltaUs=$deltaUs resynced=$resynced armedBy=$forwardArmedBy"
        onDiagnosticEvent?.invoke(line)
        Log.i(TAG, line)
        forwardAnchorPending = false
        forwardForceResync = false
    }

    companion object {
        const val TUNNEL_AUDIO_CLASS_PCM = "pcm"
        private const val TAG = "PassthroughSink"
    }
}
