package com.nuvio.tv.core.tts

internal const val MAX_CARRY_OVER_MS = 1500L
internal const val MAX_QUEUE_STALE_MS = 2500L

private const val NANOS_PER_MILLI = 1_000_000L
private val monotonicStartNanos: Long = System.nanoTime()

internal fun defaultMonotonicNowMs(): Long =
    (System.nanoTime() - monotonicStartNanos) / NANOS_PER_MILLI

internal data class PendingCueRequest(
    val rawText: String,
    val cleanText: String,
    val timingContext: SubtitleCuePlaybackContext?,
    val arrivedAtMs: Long,
)

internal sealed interface CueDispatchDecision {
    data class SpeakNow(
        val cleanText: String,
        val speechText: String,
        val timingContext: SubtitleCuePlaybackContext?,
        val budgetMs: Long?,
    ) : CueDispatchDecision

    data class QueuedPending(
        val expectedActiveGen: Long,
        val startCarryOverTimer: Boolean,
    ) : CueDispatchDecision

    data object Ignored : CueDispatchDecision
}

internal sealed interface CompletionAdvanceDecision {
    data class SpeakQueued(
        val request: PendingCueRequest,
        val adjustedBudgetMs: Long?,
    ) : CompletionAdvanceDecision

    data object DroppedStale : CompletionAdvanceDecision
    data object Idle : CompletionAdvanceDecision
}

/**
 * Thread-safe cue queue state machine coordinating duplicate filtering, single-slot carry-over
 * queuing, and generation-guarded completion advances for subtitle TTS playback.
 */
internal class SubtitleTtsCueQueueState(
    private val nowMs: () -> Long = ::defaultMonotonicNowMs,
    private val maxQueueStaleMs: Long = MAX_QUEUE_STALE_MS,
) {
    private val lock = Any()

    private var lastActiveRawText: String = ""
    private var lastActiveTimingContext: SubtitleCuePlaybackContext? = null
    private var lastSpokenText: String = ""
    private var lastSpokenCueStartMs: Long? = null
    private var isSpeechActive: Boolean = false
    private var activePlaybackGen: Long = 0L
    private var pendingCue: PendingCueRequest? = null

    fun recordRawCueWhenDisabled(rawText: String, timingContext: SubtitleCuePlaybackContext?) {
        synchronized(lock) {
            lastActiveRawText = rawText
            lastActiveTimingContext = timingContext
        }
    }

    fun onCueArrived(
        rawText: String,
        timingContext: SubtitleCuePlaybackContext?,
        waitForCueCompletion: Boolean,
    ): CueDispatchDecision = synchronized(lock) {
        lastActiveRawText = rawText
        lastActiveTimingContext = timingContext

        val cleanText = SubtitleTtsTextCleaner.clean(rawText)
        if (cleanText.isBlank()) {
            lastActiveRawText = ""
            lastActiveTimingContext = null
            return@synchronized CueDispatchDecision.Ignored
        }

        val cueStartMs = timingContext?.currentCue?.startMs
        if (cleanText == lastSpokenText && (timingContext == null || cueStartMs == lastSpokenCueStartMs)) {
            return@synchronized CueDispatchDecision.Ignored
        }

        val currentPending = pendingCue
        if (currentPending != null &&
            cleanText == currentPending.cleanText &&
            (timingContext == null || cueStartMs == currentPending.timingContext?.currentCue?.startMs)
        ) {
            return@synchronized CueDispatchDecision.Ignored
        }

        if (waitForCueCompletion && isSpeechActive) {
            val hadPending = pendingCue != null
            pendingCue = PendingCueRequest(
                rawText = rawText,
                cleanText = cleanText,
                timingContext = timingContext,
                arrivedAtMs = nowMs(),
            )
            return@synchronized CueDispatchDecision.QueuedPending(
                expectedActiveGen = activePlaybackGen,
                startCarryOverTimer = !hadPending,
            )
        }

        pendingCue = null
        lastSpokenText = cleanText
        lastSpokenCueStartMs = cueStartMs

        CueDispatchDecision.SpeakNow(
            cleanText = cleanText,
            speechText = SubtitleTtsTextCleaner.ensureSentenceEndingPunctuation(cleanText),
            timingContext = timingContext,
            budgetMs = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(timingContext),
        )
    }

    fun beginPlayback(): Long = synchronized(lock) {
        isSpeechActive = true
        ++activePlaybackGen
    }

    fun currentGen(): Long = synchronized(lock) {
        activePlaybackGen
    }

    fun hasActiveSpeech(): Boolean = synchronized(lock) {
        isSpeechActive
    }

    fun hasPendingCue(): Boolean = synchronized(lock) {
        pendingCue != null
    }

    fun clearActiveIfGen(gen: Long) {
        synchronized(lock) {
            if (activePlaybackGen == gen) {
                isSpeechActive = false
            }
        }
    }

    fun onPlaybackCompleted(gen: Long): CompletionAdvanceDecision = synchronized(lock) {
        if (gen != activePlaybackGen || !isSpeechActive) {
            return@synchronized CompletionAdvanceDecision.Idle
        }
        isSpeechActive = false
        consumePendingCueLocked()
    }

    fun onCarryOverTimeout(expectedGen: Long): CompletionAdvanceDecision = synchronized(lock) {
        if (expectedGen != activePlaybackGen || !isSpeechActive) {
            return@synchronized CompletionAdvanceDecision.Idle
        }
        isSpeechActive = false
        activePlaybackGen++
        consumePendingCueLocked()
    }

    private fun consumePendingCueLocked(): CompletionAdvanceDecision {
        val next = pendingCue ?: return CompletionAdvanceDecision.Idle
        pendingCue = null

        val overdueMs = (nowMs() - next.arrivedAtMs).coerceAtLeast(0L)
        if (overdueMs > maxQueueStaleMs) {
            return CompletionAdvanceDecision.DroppedStale
        }

        val originalBudgetMs = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(next.timingContext)
        val adjustedBudgetMs = originalBudgetMs?.let { budget ->
            (budget - overdueMs).coerceAtLeast(SubtitleTtsTimingCalculator.MIN_SPEECH_BUDGET_MS)
        }

        lastSpokenText = next.cleanText
        lastSpokenCueStartMs = next.timingContext?.currentCue?.startMs

        return CompletionAdvanceDecision.SpeakQueued(
            request = next,
            adjustedBudgetMs = adjustedBudgetMs,
        )
    }

    fun clearPendingQueue() {
        synchronized(lock) {
            pendingCue = null
        }
    }

    fun resetActivePlayback(): Long = synchronized(lock) {
        pendingCue = null
        isSpeechActive = false
        ++activePlaybackGen
    }

    fun resetAllHistory(): Long = synchronized(lock) {
        lastActiveRawText = ""
        lastActiveTimingContext = null
        lastSpokenText = ""
        lastSpokenCueStartMs = null
        pendingCue = null
        isSpeechActive = false
        ++activePlaybackGen
    }

    fun clearSpokenHistoryAndSnapshotActive(): Pair<String, SubtitleCuePlaybackContext?> = synchronized(lock) {
        lastSpokenText = ""
        lastSpokenCueStartMs = null
        lastActiveRawText to lastActiveTimingContext
    }
}
