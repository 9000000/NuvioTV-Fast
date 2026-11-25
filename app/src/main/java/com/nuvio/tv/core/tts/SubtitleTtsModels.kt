package com.nuvio.tv.core.tts

import com.nuvio.tv.data.local.PlayerSettings

/**
 * Metadata describing a Google TTS voice reported by Android's `TextToSpeech` engine.
 */
data class GoogleTtsVoiceInfo(
    val name: String,
    val displayName: String,
    val localeTag: String,
    val isOffline: Boolean,
)

/**
 * Availability state of the Google Offline TTS engine (`com.google.android.tts`).
 */
enum class GoogleTtsAvailability {
    INITIALIZING,
    READY,
    ENGINE_MISSING,
    NO_OFFLINE_VOICE,
    ERROR,
}

/**
 * Subtitle cue timing metadata used to align TTS speech pacing with subtitle slot boundaries.
 */
data class TimedSpeechCue(
    val rawText: String,
    val startMs: Long,
    val endMs: Long? = null,
    val nextCueStartMs: Long? = null,
)

/**
 * Runtime playback timing context for the active subtitle cue and upcoming lookahead cues.
 */
data class SubtitleCuePlaybackContext(
    val currentCue: TimedSpeechCue,
    val upcomingCues: List<TimedSpeechCue> = emptyList(),
    val playbackSpeed: Float = SubtitleTtsTimingCalculator.DEFAULT_PLAYBACK_SPEED,
    val positionMs: Long = currentCue.startMs,
)

/**
 * Normalized runtime configuration for Google Offline subtitle TTS.
 */
data class SubtitleTtsConfig(
    val enabled: Boolean = false,
    val voiceName: String? = null,
    val allowNetworkVoiceFallback: Boolean = false,
    val movieVolume: Float = SubtitleTtsTimingCalculator.DEFAULT_MOVIE_VOLUME,
    val voiceVolume: Float = SubtitleTtsTimingCalculator.DEFAULT_VOICE_VOLUME,
    val baseSpeed: Float = SubtitleTtsTimingCalculator.DEFAULT_BASE_SPEED,
    val pitch: Float = SubtitleTtsTimingCalculator.DEFAULT_PITCH,
    val autoSpeedEnabled: Boolean = true,
    val maxAutoSpeed: Float = SubtitleTtsTimingCalculator.DEFAULT_MAX_AUTO_SPEED,
    val waitForCueCompletion: Boolean = false,
    val hideSubtitlesWhenEnabled: Boolean = false,
    val autoStereoDownmix: Boolean = true,
    val transliterateEnglishNames: Boolean = true,
    val preferredLanguageCode: String = GoogleOfflineTtsVoiceSelector.DEFAULT_LANGUAGE,
) {
    /** Whether visual subtitles should be suppressed while subtitle TTS is reading. */
    val shouldHideVisualSubtitles: Boolean
        get() = enabled && hideSubtitlesWhenEnabled

    /** Whether playback audio should be temporarily forced to 2.0 Stereo PCM while TTS is active. */
    val shouldForceStereoDownmix: Boolean
        get() = enabled && autoStereoDownmix
}

/**
 * Projects persisted [PlayerSettings] into a clamped [SubtitleTtsConfig].
 */
fun PlayerSettings.toSubtitleTtsConfig(): SubtitleTtsConfig = SubtitleTtsConfig(
    enabled = ttsEnabled,
    voiceName = ttsVoiceName?.takeIf { it.isNotBlank() },
    allowNetworkVoiceFallback = ttsAllowNetworkVoiceFallback,
    movieVolume = SubtitleTtsTimingCalculator.clampVolume(
        value = ttsMovieVolume,
        fallback = SubtitleTtsTimingCalculator.DEFAULT_MOVIE_VOLUME,
    ),
    voiceVolume = SubtitleTtsTimingCalculator.clampVolume(
        value = ttsVoiceVolume,
        fallback = SubtitleTtsTimingCalculator.DEFAULT_VOICE_VOLUME,
    ),
    baseSpeed = SubtitleTtsTimingCalculator.clampBaseSpeed(ttsBaseSpeed),
    pitch = SubtitleTtsTimingCalculator.clampPitch(ttsPitch),
    autoSpeedEnabled = ttsAutoSpeedEnabled,
    maxAutoSpeed = SubtitleTtsTimingCalculator.clampMaxAutoSpeed(ttsMaxAutoSpeed),
    waitForCueCompletion = ttsWaitForCueCompletion,
    hideSubtitlesWhenEnabled = ttsHideSubtitlesWhenEnabled,
    autoStereoDownmix = ttsAutoStereoDownmix,
    transliterateEnglishNames = ttsTransliterateEnglishNames,
    preferredLanguageCode = GoogleOfflineTtsVoiceSelector.resolvePreferredLanguageCode(
        preferredSubtitleLanguage = subtitleStyle.preferredLanguage,
        secondarySubtitleLanguage = subtitleStyle.secondaryPreferredLanguage,
    ),
)
