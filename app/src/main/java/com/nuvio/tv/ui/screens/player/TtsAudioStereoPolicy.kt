package com.nuvio.tv.ui.screens.player

import androidx.media3.common.MimeTypes
import com.nuvio.tv.data.local.AudioOutputChannels
import com.nuvio.tv.data.local.PlayerSettings

internal data class EffectiveAudioStereoOutputConfig(
    val forceStereoForTts: Boolean,
    val forceStereoPcm: Boolean,
    val forceOpticalPassthrough: Boolean,
    val downmixEnabled: Boolean,
    val audioOutputChannels: AudioOutputChannels,
    val ffmpegDownmixEnabled: Boolean,
    val ffmpegAudioOutputChannels: AudioOutputChannels,
    val deniedTranscodeMimes: Set<String>,
    val disableTunneling: Boolean,
    val requiresRebuildForSoftwareDecoder: Boolean
)

/**
 * Pure policy resolving the effective audio output route when subtitle TTS auto-stereo
 * downmix and/or Bluetooth audio routing are active, and cleanly restoring the user's
 * original multichannel/passthrough settings once deactivated.
 */
internal object TtsAudioStereoPolicy {

    private val SOFTWARE_ONLY_SURROUND_MIMES: Set<String> = setOf(
        MimeTypes.AUDIO_TRUEHD,
        MimeTypes.AUDIO_DTS,
        MimeTypes.AUDIO_DTS_HD,
        MimeTypes.AUDIO_DTS_EXPRESS,
        MimeTypes.AUDIO_DTS_X,
        "audio/mlp",
        "audio/vnd.dts.hd",
        "audio/vnd.dts.uhd"
    )

    fun shouldForceStereoForTts(
        ttsEnabled: Boolean,
        ttsAutoStereoDownmix: Boolean
    ): Boolean = ttsEnabled && ttsAutoStereoDownmix

    fun shouldForceStereoPcm(
        isBluetooth: Boolean,
        ttsEnabled: Boolean,
        ttsAutoStereoDownmix: Boolean
    ): Boolean = isBluetooth || shouldForceStereoForTts(ttsEnabled, ttsAutoStereoDownmix)

    fun isSoftwareOnlySurroundMime(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return false
        return mimeType in SOFTWARE_ONLY_SURROUND_MIMES
    }

    fun resolveEffectiveOutput(
        isBluetooth: Boolean,
        ttsEnabled: Boolean,
        ttsAutoStereoDownmix: Boolean,
        settings: PlayerSettings,
        lastSurround: SurroundResolveResult? = null,
        cachedDecoderPriority: Int = settings.decoderPriority,
        activeAudioMimeType: String? = null
    ): EffectiveAudioStereoOutputConfig {
        val forceStereoForTts = shouldForceStereoForTts(ttsEnabled, ttsAutoStereoDownmix)
        val forceStereoPcm = isBluetooth || forceStereoForTts
        val forceOptical = !forceStereoPcm &&
            settings.forceOpticalPassthrough &&
            settings.decoderPriority != 0

        val downmixEnabled = when {
            forceStereoPcm -> true
            lastSurround != null -> lastSurround.downmixEnabled
            else -> settings.effectiveDownmixEnabled
        }
        val audioOutputChannels = when {
            forceStereoPcm -> AudioOutputChannels.CHANNELS_2_0
            lastSurround != null -> lastSurround.audioOutputChannels
            else -> settings.audioOutputChannels
        }
        // Keep FFmpeg decoder's own channel layout intact when only TTS (not Bluetooth) is
        // forcing stereo: TtsStereoDownmixAudioProcessor downmixes multichannel PCM to 2.0
        // in DefaultAudioSink so disabling TTS restores native 5.1/7.1 PCM immediately.
        val ffmpegDownmixEnabled = when {
            isBluetooth -> true
            lastSurround != null -> lastSurround.downmixEnabled
            else -> settings.effectiveDownmixEnabled
        }
        val ffmpegAudioOutputChannels = when {
            isBluetooth -> AudioOutputChannels.CHANNELS_2_0
            lastSurround != null -> lastSurround.audioOutputChannels
            else -> settings.audioOutputChannels
        }
        val deniedTranscodeMimes = if (forceStereoPcm) {
            emptySet()
        } else {
            lastSurround?.deniedTranscodeMimes ?: emptySet()
        }

        // If ExoPlayer was built with decoderPriority == 0 (no FFmpeg audio renderer),
        // switching a TrueHD/DTS bitstream track to stereo PCM requires rebuilding with
        // the software audio renderer enabled so the track remains audible.
        val requiresRebuild = forceStereoForTts &&
            cachedDecoderPriority == 0 &&
            isSoftwareOnlySurroundMime(activeAudioMimeType)

        return EffectiveAudioStereoOutputConfig(
            forceStereoForTts = forceStereoForTts,
            forceStereoPcm = forceStereoPcm,
            forceOpticalPassthrough = forceOptical,
            downmixEnabled = downmixEnabled,
            audioOutputChannels = audioOutputChannels,
            ffmpegDownmixEnabled = ffmpegDownmixEnabled,
            ffmpegAudioOutputChannels = ffmpegAudioOutputChannels,
            deniedTranscodeMimes = deniedTranscodeMimes,
            disableTunneling = forceStereoForTts,
            requiresRebuildForSoftwareDecoder = requiresRebuild
        )
    }
}
