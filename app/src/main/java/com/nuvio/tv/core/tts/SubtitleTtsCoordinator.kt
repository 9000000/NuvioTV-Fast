package com.nuvio.tv.core.tts

import android.content.Context
import android.util.Log
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates subtitle cue synchronization, carry-over queueing, playback lifecycle hooks, and
 * voice previewing against [GoogleOfflineTtsEngine] and profile-scoped [SubtitleTtsConfig].
 */
@Singleton
class SubtitleTtsCoordinator internal constructor(
    private val engine: GoogleOfflineTtsEngine,
    configFlow: Flow<SubtitleTtsConfig>,
    mainDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        engine: GoogleOfflineTtsEngine,
        playerSettingsDataStore: PlayerSettingsDataStore,
    ) : this(
        engine = engine,
        configFlow = playerSettingsDataStore.playerSettings
            .map { it.toSubtitleTtsConfig() }
            .distinctUntilChanged(),
        mainDispatcher = Dispatchers.Main.immediate,
    )

    companion object {
        const val DEFAULT_SAMPLE_TEXT =
            "Chào mừng bạn đến với Nuvio TV, đây là giọng đọc phụ đề tiếng Việt."
        private const val MIN_PREVIEW_VOLUME = 0.05f
        private const val FALLBACK_PREVIEW_VOLUME = 0.6f
        private const val TAG = "SubtitleTtsCoordinator"
    }

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val queueState = SubtitleTtsCueQueueState()
    private val timerLock = Any()
    private val sessionSeq = AtomicLong(0L)

    private var carryOverTimeoutJob: Job? = null
    private var speechWatchdogJob: Job? = null

    @Volatile private var currentSessionToken: Long = 0L
    @Volatile private var isSessionActive: Boolean = false
    @Volatile private var isPlaybackActive: Boolean = false
    @Volatile private var activeSpeechGen: Long = 0L
    @Volatile private var activeSpeechStartedAtMs: Long = 0L
    @Volatile private var activeSpeechEstimatedMs: Long = 0L

    private val _config = MutableStateFlow(SubtitleTtsConfig())
    val config: StateFlow<SubtitleTtsConfig> = _config.asStateFlow()

    val availability: StateFlow<GoogleTtsAvailability>
        get() = engine.availability

    val voices: StateFlow<List<GoogleTtsVoiceInfo>>
        get() = engine.voices

    val activeVoiceName: StateFlow<String?>
        get() = engine.activeVoiceName

    val isGoogleEngineActive: StateFlow<Boolean>
        get() = engine.isGoogleEngineActive

    val isSpeaking: StateFlow<Boolean>
        get() = engine.isSpeaking

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        scope.launch {
            configFlow
                .catch { error ->
                    Log.w(TAG, "Error observing player TTS settings", error)
                }
                .collect { updated ->
                    applyConfigUpdate(updated)
                }
        }
    }

    /** Starts an active playback TTS session and returns its ownership token. */
    fun startSession(): Long {
        val token = sessionSeq.incrementAndGet()
        currentSessionToken = token
        isSessionActive = true
        isPlaybackActive = true
        cancelCarryOverTimer()
        queueState.resetAllHistory()
        val currentConfig = _config.value
        if (currentConfig.enabled) {
            engine.ensureInitialized(
                allowNetworkVoiceFallback = currentConfig.allowNetworkVoiceFallback,
                preferredLanguageCode = currentConfig.preferredLanguageCode,
            )
        }
        return token
    }

    /** Stops the session matching [token], cancelling pending timers and stopping speech. */
    fun stopSession(token: Long) {
        if (token != currentSessionToken || !isSessionActive) return
        releaseSessionInternal()
    }

    /** Unconditionally releases whichever playback TTS session is currently active. */
    fun releaseSession() {
        releaseSessionInternal()
    }

    /** Updates whether media playback is actively advancing (pauses speech immediately when `false`). */
    fun setPlaybackActive(active: Boolean) {
        if (isPlaybackActive == active) return
        isPlaybackActive = active
        if (!active) {
            stopSpeaking()
        }
    }

    /** Pauses active TTS narration when media playback pauses. */
    fun onPlaybackPaused() {
        setPlaybackActive(false)
    }

    /** Resumes eligibility for TTS narration when media playback resumes. */
    fun onPlaybackResumed() {
        setPlaybackActive(true)
    }

    /** Clears cue history and stops current speech when the user seeks to a new position. */
    fun onSeek() {
        cancelCarryOverTimer()
        queueState.resetAllHistory()
        engine.stop()
    }

    /** Clears cue history and stops current speech when the subtitle track is changed or disabled. */
    fun onSubtitleTrackChanged() {
        cancelCarryOverTimer()
        queueState.resetAllHistory()
        engine.stop()
    }

    /** Stops active speech and clears any pending carry-over cue without wiping active cue snapshot. */
    fun stopSpeaking() {
        cancelCarryOverTimer()
        queueState.resetActivePlayback()
        engine.stop()
    }

    /** Convenience overload when only raw subtitle text is available without timing metadata. */
    fun onSubtitleCueTextChanged(rawText: String) {
        onSubtitleCueChanged(rawText, timingContext = null)
    }

    /** Dispatches a subtitle cue update using [timingContext] for timing-aware speed control. */
    fun onSubtitleCueChanged(
        rawText: String,
        timingContext: SubtitleCuePlaybackContext? = null,
    ) {
        val currentConfig = _config.value
        if (!isSessionActive || !isPlaybackActive || !currentConfig.enabled) {
            queueState.recordRawCueWhenDisabled(rawText, timingContext)
            return
        }

        engine.ensureInitialized(
            allowNetworkVoiceFallback = currentConfig.allowNetworkVoiceFallback,
            preferredLanguageCode = currentConfig.preferredLanguageCode,
        )

        when (val decision = queueState.onCueArrived(rawText, timingContext, currentConfig.waitForCueCompletion)) {
            CueDispatchDecision.Ignored -> return
            is CueDispatchDecision.QueuedPending -> {
                if (isActiveSpeechAlreadyQuiet(decision.expectedActiveGen)) {
                    handlePlaybackCompleted(decision.expectedActiveGen)
                } else {
                    val pendingBudgetMs = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(timingContext)
                    if (decision.startCarryOverTimer || carryOverTimeoutJob == null) {
                        startCarryOverTimer(
                            expectedActiveGen = decision.expectedActiveGen,
                            pendingCueBudgetMs = pendingBudgetMs,
                        )
                    }
                }
            }
            is CueDispatchDecision.SpeakNow -> {
                cancelCarryOverTimer()
                startSpeakingCue(
                    cleanText = decision.cleanText,
                    speechText = decision.speechText,
                    budgetMs = decision.budgetMs,
                    config = currentConfig,
                )
            }
        }
    }

    /** Speaks a preview sample even when subtitle TTS is disabled or outside a playback session. */
    fun previewVoice(
        sampleText: String = DEFAULT_SAMPLE_TEXT,
        overrideConfig: SubtitleTtsConfig? = null,
    ) {
        stopSpeaking()

        val effectiveConfig = overrideConfig ?: _config.value
        engine.ensureInitialized(
            allowNetworkVoiceFallback = effectiveConfig.allowNetworkVoiceFallback,
            preferredLanguageCode = effectiveConfig.preferredLanguageCode,
        )

        val clampedVolume = SubtitleTtsTimingCalculator.clampVolume(
            value = effectiveConfig.voiceVolume,
            fallback = SubtitleTtsTimingCalculator.DEFAULT_VOICE_VOLUME,
        )
        val previewVolume = if (clampedVolume <= MIN_PREVIEW_VOLUME) {
            FALLBACK_PREVIEW_VOLUME
        } else {
            clampedVolume
        }
        val cleanText = SubtitleTtsTextCleaner.clean(sampleText).ifBlank { sampleText.trim() }
        val speechText = SubtitleTtsTextCleaner.ensureSentenceEndingPunctuation(cleanText)
        if (speechText.isBlank()) return

        _statusMessage.value = null
        val playbackGen = queueState.beginPlayback()
        engine.speak(
            text = speechText,
            voiceName = effectiveConfig.voiceName,
            pitch = SubtitleTtsTimingCalculator.clampPitch(effectiveConfig.pitch),
            speed = SubtitleTtsTimingCalculator.clampBaseSpeed(effectiveConfig.baseSpeed),
            volume = previewVolume,
            allowNetworkVoiceFallback = effectiveConfig.allowNetworkVoiceFallback,
            preferredLanguageCode = effectiveConfig.preferredLanguageCode,
            onCompleted = { dispatchPlaybackCompleted(playbackGen) },
        )
    }

    /** Refreshes available Google TTS voices for [allowNetworkFallback] and [preferredLanguageCode]. */
    fun refreshVoices(
        allowNetworkFallback: Boolean = _config.value.allowNetworkVoiceFallback,
        preferredLanguageCode: String = _config.value.preferredLanguageCode,
    ) {
        engine.refreshVoices(
            allowNetworkFallback = allowNetworkFallback,
            preferredLanguageCode = preferredLanguageCode,
        )
    }

    /** Opens Google TTS voice data installation or Android TTS settings. */
    fun openOfflineVoiceDataInstaller(context: Context): Boolean =
        engine.openOfflineVoiceDataInstaller(context)

    /** Clears any transient coordinator status message. */
    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun applyConfigUpdate(updated: SubtitleTtsConfig) {
        val previous = _config.value
        if (updated == previous) return
        _config.value = updated

        if (previous.allowNetworkVoiceFallback != updated.allowNetworkVoiceFallback ||
            previous.preferredLanguageCode != updated.preferredLanguageCode
        ) {
            engine.refreshVoices(
                allowNetworkFallback = updated.allowNetworkVoiceFallback,
                preferredLanguageCode = updated.preferredLanguageCode,
            )
        } else if (updated.enabled && !previous.enabled) {
            engine.ensureInitialized(
                allowNetworkVoiceFallback = updated.allowNetworkVoiceFallback,
                preferredLanguageCode = updated.preferredLanguageCode,
            )
        }

        if (previous.waitForCueCompletion && !updated.waitForCueCompletion) {
            cancelCarryOverTimer()
            queueState.clearPendingQueue()
        }

        if (!updated.enabled && previous.enabled) {
            stopSpeaking()
        } else if (updated.enabled && !previous.enabled && isSessionActive && isPlaybackActive) {
            val (activeRaw, activeContext) = queueState.clearSpokenHistoryAndSnapshotActive()
            if (activeRaw.isNotBlank()) {
                onSubtitleCueChanged(activeRaw, activeContext)
            }
        }
    }

    private fun releaseSessionInternal() {
        isSessionActive = false
        isPlaybackActive = false
        cancelCarryOverTimer()
        queueState.resetAllHistory()
        engine.stop()
    }

    private fun startSpeakingCue(
        cleanText: String,
        speechText: String,
        budgetMs: Long?,
        config: SubtitleTtsConfig,
    ) {
        val playbackGen = queueState.beginPlayback()
        val targetSpeed = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = cleanText,
            budgetMs = budgetMs,
            baseSpeed = config.baseSpeed,
            autoSpeedEnabled = config.autoSpeedEnabled,
            maxAutoSpeed = config.maxAutoSpeed,
        )
        val estimatedSpeechMs = SubtitleTtsTimingCalculator.estimateSpokenDurationMs(
            cleanText = cleanText,
            speechSpeed = targetSpeed,
        )
        activeSpeechGen = playbackGen
        activeSpeechStartedAtMs = defaultMonotonicNowMs()
        activeSpeechEstimatedMs = estimatedSpeechMs
        startSpeechCompletionWatchdog(playbackGen, estimatedSpeechMs)
        engine.speak(
            text = speechText,
            voiceName = config.voiceName,
            pitch = config.pitch,
            speed = targetSpeed,
            volume = config.voiceVolume,
            allowNetworkVoiceFallback = config.allowNetworkVoiceFallback,
            preferredLanguageCode = config.preferredLanguageCode,
            onCompleted = { dispatchPlaybackCompleted(playbackGen) },
        )
    }

    private fun isActiveSpeechAlreadyQuiet(expectedGen: Long): Boolean {
        if (expectedGen != activeSpeechGen) return false
        val elapsedMs = (defaultMonotonicNowMs() - activeSpeechStartedAtMs).coerceAtLeast(0L)
        if (engine.hasActiveUtteranceStarted() && !engine.isEngineSpeakingNow()) {
            return true
        }
        val overdueSilenceFloorMs = (activeSpeechEstimatedMs + 450L).coerceAtLeast(700L)
        return elapsedMs >= overdueSilenceFloorMs && !engine.isEngineSpeakingNow()
    }

    private fun startSpeechCompletionWatchdog(expectedGen: Long, estimatedSpeechMs: Long) {
        synchronized(timerLock) {
            speechWatchdogJob?.cancel()
            lateinit var job: Job
            job = scope.launch(start = CoroutineStart.LAZY) {
                val initialDelayMs = (estimatedSpeechMs * 0.70).toLong().coerceIn(280L, 2_500L)
                delay(initialDelayMs)
                val hardDeadlineAtMs = activeSpeechStartedAtMs + (estimatedSpeechMs + 1_200L).coerceAtLeast(1_500L)
                while (queueState.currentGen() == expectedGen && queueState.hasActiveSpeech()) {
                    val now = defaultMonotonicNowMs()
                    val finishedQuietly = engine.hasActiveUtteranceStarted() && !engine.isEngineSpeakingNow()
                    val exceededHardDeadline = now >= hardDeadlineAtMs && !engine.isEngineSpeakingNow()
                    if (finishedQuietly || exceededHardDeadline) {
                        handlePlaybackCompleted(expectedGen)
                        break
                    }
                    if (now >= hardDeadlineAtMs + 1_500L) {
                        handlePlaybackCompleted(expectedGen)
                        break
                    }
                    delay(70L)
                }
                synchronized(timerLock) {
                    if (speechWatchdogJob === job) {
                        speechWatchdogJob = null
                    }
                }
            }
            speechWatchdogJob = job
            job.start()
        }
    }

    private fun dispatchPlaybackCompleted(gen: Long) {
        scope.launch {
            handlePlaybackCompleted(gen)
        }
    }

    private fun handlePlaybackCompleted(gen: Long) {
        when (val decision = queueState.onPlaybackCompleted(gen)) {
            CompletionAdvanceDecision.Idle -> {
                if (!queueState.hasActiveSpeech()) {
                    cancelSpeechWatchdog()
                }
            }
            CompletionAdvanceDecision.DroppedStale -> {
                cancelCarryOverTimer()
            }
            is CompletionAdvanceDecision.SpeakQueued -> {
                cancelCarryOverTimer()
                val currentConfig = _config.value
                if (!currentConfig.enabled || !isSessionActive || !isPlaybackActive) return
                startSpeakingCue(
                    cleanText = decision.request.cleanText,
                    speechText = SubtitleTtsTextCleaner.ensureSentenceEndingPunctuation(decision.request.cleanText),
                    budgetMs = decision.adjustedBudgetMs,
                    config = currentConfig,
                )
            }
        }
    }

    private fun startCarryOverTimer(
        expectedActiveGen: Long,
        pendingCueBudgetMs: Long? = null,
    ) {
        val elapsedMs = if (expectedActiveGen == activeSpeechGen) {
            (defaultMonotonicNowMs() - activeSpeechStartedAtMs).coerceAtLeast(0L)
        } else {
            0L
        }
        val waitLimitMs = SubtitleTtsTimingCalculator.computeDynamicCarryOverMs(
            elapsedCurrentSpeechMs = elapsedMs,
            estimatedCurrentSpeechMs = activeSpeechEstimatedMs,
            pendingCueBudgetMs = pendingCueBudgetMs,
        )
        synchronized(timerLock) {
            carryOverTimeoutJob?.cancel()
            lateinit var job: Job
            job = scope.launch(start = CoroutineStart.LAZY) {
                val deadlineAtMs = defaultMonotonicNowMs() + waitLimitMs
                while (defaultMonotonicNowMs() < deadlineAtMs) {
                    if (queueState.currentGen() != expectedActiveGen || !queueState.hasActiveSpeech()) {
                        synchronized(timerLock) {
                            if (carryOverTimeoutJob === job) {
                                carryOverTimeoutJob = null
                            }
                        }
                        return@launch
                    }
                    if (isActiveSpeechAlreadyQuiet(expectedActiveGen)) {
                        synchronized(timerLock) {
                            if (carryOverTimeoutJob === job) {
                                carryOverTimeoutJob = null
                            }
                        }
                        handlePlaybackCompleted(expectedActiveGen)
                        return@launch
                    }
                    val remaining = (deadlineAtMs - defaultMonotonicNowMs()).coerceAtLeast(1L)
                    delay(minOf(60L, remaining))
                }
                synchronized(timerLock) {
                    if (carryOverTimeoutJob === job) {
                        carryOverTimeoutJob = null
                    }
                }
                when (val decision = queueState.onCarryOverTimeout(expectedActiveGen)) {
                    CompletionAdvanceDecision.Idle -> Unit
                    CompletionAdvanceDecision.DroppedStale -> {
                        cancelSpeechWatchdog()
                        engine.stop()
                    }
                    is CompletionAdvanceDecision.SpeakQueued -> {
                        cancelSpeechWatchdog()
                        val currentConfig = _config.value
                        if (!currentConfig.enabled || !isSessionActive || !isPlaybackActive) {
                            engine.stop()
                            return@launch
                        }
                        startSpeakingCue(
                            cleanText = decision.request.cleanText,
                            speechText = SubtitleTtsTextCleaner.ensureSentenceEndingPunctuation(decision.request.cleanText),
                            budgetMs = decision.adjustedBudgetMs,
                            config = currentConfig,
                        )
                    }
                }
            }
            carryOverTimeoutJob = job
            job.start()
        }
    }

    private fun cancelSpeechWatchdog() {
        synchronized(timerLock) {
            speechWatchdogJob?.cancel()
            speechWatchdogJob = null
        }
    }

    private fun cancelCarryOverTimer() {
        synchronized(timerLock) {
            carryOverTimeoutJob?.cancel()
            carryOverTimeoutJob = null
            speechWatchdogJob?.cancel()
            speechWatchdogJob = null
        }
    }
}
