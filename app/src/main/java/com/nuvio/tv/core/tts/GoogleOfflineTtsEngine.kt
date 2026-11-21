package com.nuvio.tv.core.tts

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android `TextToSpeech` wrapper dedicated to Google TTS Offline (`com.google.android.tts`).
 *
 * Uses media/speech [AudioAttributes] without requesting exclusive audio focus so ExoPlayer/MPV
 * playback does not pause while subtitle narration speaks.
 */
@Singleton
class GoogleOfflineTtsEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
        private const val ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
        private const val UTTERANCE_ID_PREFIX = "nuvio_tv_cue_"
        private const val TAG = "GoogleOfflineTtsEngine"

        private val SPEECH_AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    private data class PendingUtterance(
        val text: String,
        val voiceName: String?,
        val pitch: Float,
        val speed: Float,
        val volume: Float,
        val allowNetworkVoiceFallback: Boolean,
        val preferredLanguageCode: String,
        val onCompleted: (() -> Unit)?,
    )

    private val ttsLock = Any()
    private val utteranceSeq = AtomicLong(0L)
    private val activeUtteranceId = AtomicReference<String?>(null)
    private val ttsExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NuvioTtsWorker").apply { isDaemon = true }
    }

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var isReady: Boolean = false
    @Volatile private var initFailed: Boolean = false
    @Volatile private var initAttempted: Boolean = false
    @Volatile private var allowNetworkVoiceFallbackSnapshot: Boolean = false
    @Volatile private var preferredLanguageCodeSnapshot: String = GoogleOfflineTtsVoiceSelector.DEFAULT_LANGUAGE
    @Volatile private var pendingUtterance: PendingUtterance? = null
    @Volatile private var cachedSystemVoices: List<Voice> = emptyList()
    @Volatile private var appliedVoiceName: String? = null
    @Volatile private var appliedLanguageTag: String? = null
    @Volatile private var activeUtteranceStarted: Boolean = false
    private var activeOnCompleted: (() -> Unit)? = null

    private val _availability = MutableStateFlow(GoogleTtsAvailability.INITIALIZING)
    val availability: StateFlow<GoogleTtsAvailability> = _availability.asStateFlow()

    private val _voices = MutableStateFlow<List<GoogleTtsVoiceInfo>>(emptyList())
    val voices: StateFlow<List<GoogleTtsVoiceInfo>> = _voices.asStateFlow()

    private val _activeVoiceName = MutableStateFlow<String?>(null)
    val activeVoiceName: StateFlow<String?> = _activeVoiceName.asStateFlow()

    private val _isGoogleEngineActive = MutableStateFlow(false)
    val isGoogleEngineActive: StateFlow<Boolean> = _isGoogleEngineActive.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /** Ensures Google TTS initialization has been started with the given network fallback mode and language filter. */
    fun ensureInitialized(
        allowNetworkVoiceFallback: Boolean = allowNetworkVoiceFallbackSnapshot,
        preferredLanguageCode: String = preferredLanguageCodeSnapshot,
    ) {
        synchronized(ttsLock) {
            allowNetworkVoiceFallbackSnapshot = allowNetworkVoiceFallback
            preferredLanguageCodeSnapshot = preferredLanguageCode
            if (tts != null && !initFailed) {
                return
            }
            if (initAttempted && _availability.value == GoogleTtsAvailability.ENGINE_MISSING) {
                return
            }
            initializeLocked()
        }
    }

    /**
     * Re-evaluates available voices (and initializes Google TTS if needed) for [allowNetworkFallback]
     * filtered to [preferredLanguageCode].
     */
    fun refreshVoices(
        allowNetworkFallback: Boolean,
        preferredLanguageCode: String = preferredLanguageCodeSnapshot,
    ) {
        val readyInstance = synchronized(ttsLock) {
            allowNetworkVoiceFallbackSnapshot = allowNetworkFallback
            preferredLanguageCodeSnapshot = preferredLanguageCode
            if (tts == null || initFailed || _availability.value == GoogleTtsAvailability.ENGINE_MISSING) {
                initializeLocked()
                null
            } else if (isReady) {
                tts
            } else {
                null
            }
        }
        if (readyInstance != null) {
            ttsExecutor.execute {
                updateVoicesFromInstance(readyInstance, allowNetworkFallback, preferredLanguageCode)
            }
        }
    }

    /**
     * Speaks [text] through Google TTS using only allowed voices (strictly offline when
     * [allowNetworkVoiceFallback] is `false`) matching [preferredLanguageCode].
     */
    fun speak(
        text: String,
        voiceName: String?,
        pitch: Float,
        speed: Float,
        volume: Float,
        allowNetworkVoiceFallback: Boolean,
        preferredLanguageCode: String = preferredLanguageCodeSnapshot,
        onCompleted: (() -> Unit)? = null,
    ) {
        if (text.isBlank()) return

        val utterance = PendingUtterance(
            text = text,
            voiceName = voiceName,
            pitch = SubtitleTtsTimingCalculator.clampPitch(pitch),
            speed = SubtitleTtsTimingCalculator.clampFinite(
                value = speed,
                fallback = SubtitleTtsTimingCalculator.DEFAULT_BASE_SPEED,
                min = SubtitleTtsTimingCalculator.MIN_BASE_SPEED,
                max = SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED,
            ),
            volume = SubtitleTtsTimingCalculator.clampVolume(volume),
            allowNetworkVoiceFallback = allowNetworkVoiceFallback,
            preferredLanguageCode = preferredLanguageCode,
            onCompleted = onCompleted,
        )

        var utteranceIdToSpeak: String? = null
        var immediateFailureCallback: (() -> Unit)? = null

        val readyInstance: TextToSpeech? = synchronized(ttsLock) {
            allowNetworkVoiceFallbackSnapshot = allowNetworkVoiceFallback
            preferredLanguageCodeSnapshot = preferredLanguageCode
            activeUtteranceId.set(null)
            activeOnCompleted = null

            when {
                initAttempted && _availability.value == GoogleTtsAvailability.ENGINE_MISSING -> {
                    immediateFailureCallback = onCompleted
                    null
                }
                tts == null || initFailed -> {
                    pendingUtterance = utterance
                    initializeLocked()
                    null
                }
                !isReady -> {
                    pendingUtterance = utterance
                    null
                }
                else -> {
                    pendingUtterance = null
                    val id = "$UTTERANCE_ID_PREFIX${utteranceSeq.incrementAndGet()}"
                    activeUtteranceId.set(id)
                    activeOnCompleted = onCompleted
                    utteranceIdToSpeak = id
                    tts
                }
            }
        }

        immediateFailureCallback?.let { callback ->
            callback.invoke()
            return
        }

        val targetId = utteranceIdToSpeak
        if (readyInstance != null && targetId != null) {
            activeUtteranceStarted = false
            ttsExecutor.execute {
                speakInternal(readyInstance, utterance, targetId)
            }
        }
    }

    /** Returns `true` once Google TTS has signalled `onStart` for the currently active utterance. */
    fun hasActiveUtteranceStarted(): Boolean = activeUtteranceStarted

    /** Queries the underlying Android `TextToSpeech` instance for live playback activity. */
    fun isEngineSpeakingNow(): Boolean {
        val instance = synchronized(ttsLock) { if (isReady) tts else null } ?: return false
        return runCatching { instance.isSpeaking }.getOrDefault(false)
    }

    /** Immediately stops current speech and clears pending/in-flight utterance callbacks. */
    fun stop() {
        val readyInstance = synchronized(ttsLock) {
            _isSpeaking.value = false
            activeUtteranceStarted = false
            pendingUtterance = null
            activeUtteranceId.set(null)
            activeOnCompleted = null
            if (isReady) tts else null
        }
        if (readyInstance != null) {
            ttsExecutor.execute {
                runCatching { readyInstance.stop() }
            }
        }
    }

    /** Stops and shuts down the underlying `TextToSpeech` instance, resetting engine state. */
    fun shutdown() {
        synchronized(ttsLock) {
            releaseInternalLocked(clearPending = true)
            initFailed = false
            initAttempted = false
            _availability.value = GoogleTtsAvailability.INITIALIZING
            _voices.value = emptyList()
            _activeVoiceName.value = null
            _isGoogleEngineActive.value = false
        }
    }

    /**
     * Launches Android's TTS voice data installer or TTS settings screen so the user can install
     * Google Speech Services or download an offline voice pack. Returns `false` if no handler exists.
     */
    fun openOfflineVoiceDataInstaller(callerContext: Context = context): Boolean {
        val intents = listOf(
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(GOOGLE_TTS_PACKAGE),
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent(ACTION_TTS_SETTINGS),
        )
        for (intent in intents) {
            if (tryStartActivity(callerContext, intent)) {
                return true
            }
        }
        return false
    }

    private fun tryStartActivity(callerContext: Context, intent: Intent): Boolean {
        if (callerContext !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            callerContext.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed launching TTS settings intent action=${intent.action}", e)
            false
        }
    }

    private fun initializeLocked() {
        releaseInternalLocked(clearPending = false)
        initAttempted = true
        isReady = false
        initFailed = false
        _isGoogleEngineActive.value = false
        _availability.value = GoogleTtsAvailability.INITIALIZING

        if (!isGoogleTtsServicePresent()) {
            initFailed = true
            _voices.value = emptyList()
            _activeVoiceName.value = null
            _availability.value = GoogleTtsAvailability.ENGINE_MISSING
            val dropped = pendingUtterance
            pendingUtterance = null
            dropped?.onCompleted?.let { callback ->
                // Notify caller outside init failure so queue state never stays stuck active.
                runCatching(callback)
            }
            return
        }

        var createdTts: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            onTtsInit(createdTts, status)
        }
        createdTts = runCatching {
            TextToSpeech(context, listener, GOOGLE_TTS_PACKAGE)
        }.onFailure { e ->
            Log.e(TAG, "Failed constructing TextToSpeech for $GOOGLE_TTS_PACKAGE", e)
            initFailed = true
            _availability.value = GoogleTtsAvailability.ERROR
        }.getOrNull()
        tts = createdTts

        if (createdTts == null) {
            val dropped = pendingUtterance
            pendingUtterance = null
            dropped?.onCompleted?.let { callback -> runCatching(callback) }
        }
    }

    private fun isGoogleTtsServicePresent(): Boolean {
        return runCatching {
            val queryIntent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
                .setPackage(GOOGLE_TTS_PACKAGE)
            @Suppress("DEPRECATION")
            val services = context.packageManager.queryIntentServices(queryIntent, 0)
            services.isNotEmpty()
        }.getOrDefault(true)
    }

    private fun onTtsInit(instance: TextToSpeech?, status: Int) {
        var readyInstance: TextToSpeech? = null
        var pendingToSpeak: PendingUtterance? = null
        var pendingUtteranceId: String? = null
        var failedPending: PendingUtterance? = null
        var allowNetworkFallback = allowNetworkVoiceFallbackSnapshot
        var preferredLanguageCode = preferredLanguageCodeSnapshot

        synchronized(ttsLock) {
            if (instance != null && tts != null && tts !== instance) {
                return
            }
            val active = instance ?: tts ?: return
            if (status != TextToSpeech.SUCCESS) {
                isReady = false
                initFailed = true
                _isGoogleEngineActive.value = false
                _voices.value = emptyList()
                _activeVoiceName.value = null
                _availability.value = if (isGoogleTtsServicePresent()) {
                    GoogleTtsAvailability.ERROR
                } else {
                    GoogleTtsAvailability.ENGINE_MISSING
                }
                failedPending = pendingUtterance
                pendingUtterance = null
                activeUtteranceId.set(null)
                activeOnCompleted = null
            } else {
                val reportedEngines = runCatching { active.engines }.getOrDefault(emptyList())
                val googleEngineMissing = reportedEngines.isNotEmpty() &&
                    reportedEngines.none { it.name == GOOGLE_TTS_PACKAGE }
                if (googleEngineMissing) {
                    isReady = false
                    initFailed = true
                    _isGoogleEngineActive.value = false
                    _voices.value = emptyList()
                    _activeVoiceName.value = null
                    _availability.value = GoogleTtsAvailability.ENGINE_MISSING
                    failedPending = pendingUtterance
                    pendingUtterance = null
                    activeUtteranceId.set(null)
                    activeOnCompleted = null
                } else {
                    isReady = true
                    initFailed = false
                    _isGoogleEngineActive.value = true
                    readyInstance = active
                    pendingToSpeak = pendingUtterance?.also { pending ->
                        allowNetworkFallback = pending.allowNetworkVoiceFallback
                        preferredLanguageCode = pending.preferredLanguageCode
                        val id = "$UTTERANCE_ID_PREFIX${utteranceSeq.incrementAndGet()}"
                        activeUtteranceId.set(id)
                        activeOnCompleted = pending.onCompleted
                        pendingUtteranceId = id
                    }
                    pendingUtterance = null
                }
            }
        }

        failedPending?.onCompleted?.invoke()

        readyInstance?.let { active ->
            ttsExecutor.execute {
                runCatching { active.setAudioAttributes(SPEECH_AUDIO_ATTRIBUTES) }
                    .onFailure { e -> Log.w(TAG, "Failed setting TTS AudioAttributes", e) }
                attachProgressListener(active)
                updateVoicesFromInstance(active, allowNetworkFallback, preferredLanguageCode)
                val pending = pendingToSpeak
                val utteranceId = pendingUtteranceId
                if (pending != null && utteranceId != null) {
                    activeUtteranceStarted = false
                    speakInternal(active, pending, utteranceId)
                }
            }
        }
    }

    private fun attachProgressListener(instance: TextToSpeech) {
        runCatching {
            instance.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId != null && activeUtteranceId.get() == utteranceId) {
                            activeUtteranceStarted = true
                            _isSpeaking.value = true
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        consumeCompletionCallback(utteranceId)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        consumeCompletionCallback(utteranceId)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        consumeCompletionCallback(utteranceId)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        clearCompletionIfMatches(utteranceId)
                    }
                },
            )
        }.onFailure { e ->
            Log.w(TAG, "Failed attaching UtteranceProgressListener", e)
        }
    }

    private fun updateVoicesFromInstance(
        currentTts: TextToSpeech,
        allowNetworkFallback: Boolean,
        preferredLanguageCode: String = preferredLanguageCodeSnapshot,
    ): List<GoogleTtsVoiceInfo> {
        val systemVoices = snapshotSystemVoices(currentTts)
        cachedSystemVoices = systemVoices
        val rawVoices = systemVoices.toRawVoices()
        val selected = GoogleOfflineTtsVoiceSelector.selectAvailableVoices(
            rawVoices = rawVoices,
            allowNetworkFallback = allowNetworkFallback,
            preferredLanguageCode = preferredLanguageCode,
        )
        _voices.value = selected
        _availability.value = if (selected.isEmpty()) {
            GoogleTtsAvailability.NO_OFFLINE_VOICE
        } else {
            GoogleTtsAvailability.READY
        }
        return selected
    }

    private fun speakInternal(
        currentTts: TextToSpeech,
        utterance: PendingUtterance,
        utteranceId: String,
    ) {
        runCatching {
            if (activeUtteranceId.get() != utteranceId) return

            val systemVoices = cachedSystemVoices.ifEmpty {
                snapshotSystemVoices(currentTts).also { cachedSystemVoices = it }
            }
            val allowedVoices = GoogleOfflineTtsVoiceSelector.selectAvailableVoices(
                rawVoices = systemVoices.toRawVoices(),
                allowNetworkFallback = utterance.allowNetworkVoiceFallback,
                preferredLanguageCode = utterance.preferredLanguageCode,
            )
            if (_voices.value != allowedVoices) {
                _voices.value = allowedVoices
            }
            if (allowedVoices.isEmpty()) {
                _availability.value = GoogleTtsAvailability.NO_OFFLINE_VOICE
                _activeVoiceName.value = null
                consumeCompletionCallback(utteranceId)
                return
            }
            _availability.value = GoogleTtsAvailability.READY

            val pickedVoiceInfo = GoogleOfflineTtsVoiceSelector.pickBestVoice(
                voices = allowedVoices,
                preferredVoiceName = utterance.voiceName,
                preferredLanguageCode = utterance.preferredLanguageCode,
            )
            val matchedSystemVoice = pickedVoiceInfo?.let { picked ->
                systemVoices.firstOrNull { voice ->
                    voice.name == picked.name &&
                        (utterance.allowNetworkVoiceFallback || !voice.isNetworkConnectionRequired)
                }
            }

            if (matchedSystemVoice != null) {
                if (appliedVoiceName != matchedSystemVoice.name) {
                    currentTts.voice = matchedSystemVoice
                    appliedVoiceName = matchedSystemVoice.name
                    appliedLanguageTag = null
                }
                _activeVoiceName.value = matchedSystemVoice.name
            } else if (utterance.allowNetworkVoiceFallback) {
                val fallbackTag = utterance.preferredLanguageCode
                if (appliedVoiceName != null || appliedLanguageTag != fallbackTag) {
                    currentTts.language = Locale.forLanguageTag(fallbackTag)
                    appliedVoiceName = null
                    appliedLanguageTag = fallbackTag
                }
                _activeVoiceName.value = null
            } else {
                _availability.value = GoogleTtsAvailability.NO_OFFLINE_VOICE
                _activeVoiceName.value = null
                consumeCompletionCallback(utteranceId)
                return
            }

            currentTts.setPitch(utterance.pitch)
            currentTts.setSpeechRate(utterance.speed)

            if (activeUtteranceId.get() != utteranceId) return

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, utterance.volume)
            }
            val result = currentTts.speak(
                utterance.text,
                TextToSpeech.QUEUE_FLUSH,
                params,
                utteranceId,
            )
            if (result == TextToSpeech.ERROR) {
                Log.w(TAG, "TextToSpeech.speak returned ERROR")
                consumeCompletionCallback(utteranceId)
            } else if (activeUtteranceId.get() == utteranceId) {
                _isSpeaking.value = true
            }
        }.onFailure { e ->
            Log.e(TAG, "GoogleOfflineTtsEngine speak failed", e)
            consumeCompletionCallback(utteranceId)
        }
    }

    private fun snapshotSystemVoices(currentTts: TextToSpeech): List<Voice> =
        runCatching { currentTts.voices?.toList().orEmpty() }.getOrDefault(emptyList())

    private fun List<Voice>.toRawVoices(): List<RawSystemVoice> = map { voice ->
        val locale = voice.locale
        RawSystemVoice(
            name = voice.name.orEmpty(),
            localeTag = locale?.toLanguageTag().orEmpty(),
            language = locale?.language.orEmpty(),
            country = locale?.country.orEmpty(),
            requiresNetwork = voice.isNetworkConnectionRequired,
        )
    }

    private fun consumeCompletionCallback(utteranceId: String?) {
        if (utteranceId == null) return
        val callback = synchronized(ttsLock) {
            if (activeUtteranceId.compareAndSet(utteranceId, null)) {
                _isSpeaking.value = false
                activeUtteranceStarted = false
                activeOnCompleted.also { activeOnCompleted = null }
            } else {
                null
            }
        }
        callback?.invoke()
    }

    private fun clearCompletionIfMatches(utteranceId: String?) {
        if (utteranceId == null) return
        synchronized(ttsLock) {
            if (activeUtteranceId.compareAndSet(utteranceId, null)) {
                _isSpeaking.value = false
                activeUtteranceStarted = false
                activeOnCompleted = null
            }
        }
    }

    private fun releaseInternalLocked(clearPending: Boolean) {
        _isSpeaking.value = false
        activeUtteranceStarted = false
        appliedVoiceName = null
        appliedLanguageTag = null
        cachedSystemVoices = emptyList()
        activeUtteranceId.set(null)
        activeOnCompleted = null
        if (clearPending) {
            pendingUtterance = null
        }
        val existing = tts
        val wasReady = isReady
        tts = null
        isReady = false
        if (existing != null) {
            if (wasReady) {
                runCatching { existing.stop() }
            }
            runCatching { existing.shutdown() }
        }
    }
}
