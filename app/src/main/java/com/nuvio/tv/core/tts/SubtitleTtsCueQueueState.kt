package com.nuvio.tv.core.tts

import kotlin.math.abs

internal const val MAX_CARRY_OVER_MS = 1500L
internal const val MAX_QUEUE_STALE_MS = 2500L

private const val NANOS_PER_MILLI = 1_000_000L
private const val MAX_RECENT_SPOKEN_LINES = 12
private const val CONTINUOUS_CUE_GAP_MS = 350L
private const val TIMING_JITTER_TOLERANCE_MS = 1_200L
private const val NULL_TIMING_DEDUP_WINDOW_MS = 3_000L
private const val NO_TIMING_RECENT_WINDOW_MS = 2_000L

private val monotonicStartNanos: Long = System.nanoTime()

internal fun defaultMonotonicNowMs(): Long =
    (System.nanoTime() - monotonicStartNanos) / NANOS_PER_MILLI

internal data class PendingCueRequest(
    val rawText: String,
    val cleanText: String,
    val cleanLines: List<String>,
    val timingContext: SubtitleCuePlaybackContext?,
    val arrivedAtMs: Long,
)

internal sealed interface CueDispatchDecision {
    data class SpeakNow(
        val playbackGen: Long,
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
        val playbackGen: Long,
        val request: PendingCueRequest,
        val adjustedBudgetMs: Long?,
    ) : CompletionAdvanceDecision

    data object DroppedStale : CompletionAdvanceDecision
    data object Idle : CompletionAdvanceDecision
}

/**
 * Thread-safe cue queue state machine coordinating line-level delta extraction for overlapping
 * cues, recent-window duplicate filtering across timing jitter / ASS frame splits / AutoSync
 * shifts, single-slot carry-over queuing, and atomic generation-guarded playback transitions.
 */
internal class SubtitleTtsCueQueueState(
    private val nowMs: () -> Long = ::defaultMonotonicNowMs,
    private val maxQueueStaleMs: Long = MAX_QUEUE_STALE_MS,
) {
    private data class ActiveScreenLine(
        val key: String,
        val cleanLine: String,
        var cueStartMs: Long?,
        var cueEndMs: Long?,
    )

    private data class SpokenLineRecord(
        val key: String,
        val cleanLine: String,
        var cueStartMs: Long?,
        var cueEndMs: Long?,
        var spokenAtMs: Long,
    )

    private val lock = Any()

    private var lastActiveRawText: String = ""
    private var lastActiveTimingContext: SubtitleCuePlaybackContext? = null
    private val activeScreenLines = LinkedHashMap<String, ActiveScreenLine>()
    private val recentSpokenLines = ArrayDeque<SpokenLineRecord>(MAX_RECENT_SPOKEN_LINES + 1)
    private var lastSpokenKey: String = ""
    private var isSpeechActive: Boolean = false
    private var activePlaybackGen: Long = 0L
    private var pendingCue: PendingCueRequest? = null

    fun recordRawCueWhenDisabled(rawText: String, timingContext: SubtitleCuePlaybackContext?) {
        synchronized(lock) {
            if (rawText.isBlank()) {
                lastActiveRawText = ""
                lastActiveTimingContext = null
                activeScreenLines.clear()
            } else {
                lastActiveRawText = rawText
                lastActiveTimingContext = timingContext
            }
        }
    }

    fun onCueArrived(
        rawText: String,
        timingContext: SubtitleCuePlaybackContext?,
        waitForCueCompletion: Boolean,
    ): CueDispatchDecision = synchronized(lock) {
        lastActiveRawText = rawText
        lastActiveTimingContext = timingContext

        val cleanedLines = SubtitleTtsTextCleaner.cleanLines(rawText)
        if (cleanedLines.isEmpty()) {
            lastActiveRawText = ""
            lastActiveTimingContext = null
            activeScreenLines.clear()
            return@synchronized CueDispatchDecision.Ignored
        }

        val now = nowMs()
        val cueStartMs = timingContext?.currentCue?.startMs
        val cueEndMs = timingContext?.currentCue?.endMs

        val novelLines = ArrayList<String>(cleanedLines.size)
        val nextActiveLines = LinkedHashMap<String, ActiveScreenLine>(cleanedLines.size)
        val previousActiveSnapshot = activeScreenLines.values.toList()

        for (line in cleanedLines) {
            val lineKey = SubtitleTtsTextCleaner.normalizeLineKey(line)
            val existingActive = activeScreenLines[lineKey]
            val stillActiveOnScreen = existingActive != null &&
                isContinuousActiveLine(existingActive, cueStartMs)

            nextActiveLines[lineKey] = ActiveScreenLine(
                key = lineKey,
                cleanLine = line,
                cueStartMs = if (stillActiveOnScreen) {
                    existingActive.cueStartMs ?: cueStartMs
                } else {
                    cueStartMs
                },
                cueEndMs = mergeEndMs(if (stillActiveOnScreen) existingActive.cueEndMs else null, cueEndMs),
            )

            if (stillActiveOnScreen) {
                extendMatchingRecentRecordLocked(lineKey, cueStartMs, cueEndMs, now)
                continue
            }

            val rollingResolution = resolveRollingExtension(previousActiveSnapshot, line, cueStartMs)
            when (rollingResolution) {
                SubtitleTtsTextCleaner.PrefixExtensionResult.PunctuationOnly -> {
                    extendMatchingRecentRecordLocked(lineKey, cueStartMs, cueEndMs, now)
                    continue
                }
                is SubtitleTtsTextCleaner.PrefixExtensionResult.Appended -> {
                    val appended = rollingResolution.appendedText
                    val appendedKey = SubtitleTtsTextCleaner.normalizeLineKey(appended)
                    if (!isDuplicateOfRecentLocked(appendedKey, cueStartMs, now)) {
                        novelLines.add(appended)
                    }
                    continue
                }
                SubtitleTtsTextCleaner.PrefixExtensionResult.NotExtension -> Unit
            }

            if (isDuplicateOfRecentLocked(lineKey, cueStartMs, now)) {
                extendMatchingRecentRecordLocked(lineKey, cueStartMs, cueEndMs, now)
                continue
            }

            novelLines.add(line)
        }

        activeScreenLines.clear()
        activeScreenLines.putAll(nextActiveLines)

        if (novelLines.isEmpty()) {
            return@synchronized CueDispatchDecision.Ignored
        }

        val effectiveCleanText = novelLines.joinToString(" ")
        val effectiveKey = SubtitleTtsTextCleaner.normalizeLineKey(effectiveCleanText)

        if (isDuplicateOfPendingLocked(effectiveKey, cueStartMs, now)) {
            return@synchronized CueDispatchDecision.Ignored
        }

        if (waitForCueCompletion && isSpeechActive) {
            val hadPending = pendingCue != null
            pendingCue = PendingCueRequest(
                rawText = rawText,
                cleanText = effectiveCleanText,
                cleanLines = novelLines,
                timingContext = timingContext,
                arrivedAtMs = now,
            )
            return@synchronized CueDispatchDecision.QueuedPending(
                expectedActiveGen = activePlaybackGen,
                startCarryOverTimer = !hadPending,
            )
        }

        pendingCue = null
        recordSpokenLinesLocked(novelLines, cleanedLines, effectiveKey, cueStartMs, cueEndMs, now)
        isSpeechActive = true
        val gen = ++activePlaybackGen

        CueDispatchDecision.SpeakNow(
            playbackGen = gen,
            cleanText = effectiveCleanText,
            speechText = SubtitleTtsTextCleaner.ensureSentenceEndingPunctuation(effectiveCleanText),
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

        val now = nowMs()
        val overdueMs = (now - next.arrivedAtMs).coerceAtLeast(0L)
        if (overdueMs > maxQueueStaleMs) {
            return CompletionAdvanceDecision.DroppedStale
        }

        val originalBudgetMs = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(next.timingContext)
        val adjustedBudgetMs = originalBudgetMs?.let { budget ->
            (budget - overdueMs).coerceAtLeast(SubtitleTtsTimingCalculator.MIN_SPEECH_BUDGET_MS)
        }

        val effectiveKey = SubtitleTtsTextCleaner.normalizeLineKey(next.cleanText)
        val cueStartMs = next.timingContext?.currentCue?.startMs
        val cueEndMs = next.timingContext?.currentCue?.endMs
        recordSpokenLinesLocked(next.cleanLines, next.cleanLines, effectiveKey, cueStartMs, cueEndMs, now)

        isSpeechActive = true
        val gen = ++activePlaybackGen

        return CompletionAdvanceDecision.SpeakQueued(
            playbackGen = gen,
            request = next,
            adjustedBudgetMs = adjustedBudgetMs,
        )
    }

    fun clearPendingQueue() {
        synchronized(lock) {
            pendingCue = null
        }
    }

    /**
     * Stops active speech and discards any pending cue while preserving already-spoken history
     * and already-spoken on-screen lines (removing only un-spoken pending lines so resuming
     * can speak a cue that never got to play without repeating spoken lines).
     */
    fun resetActivePlayback(): Long = synchronized(lock) {
        pendingCue?.cleanLines?.forEach { pendingLine ->
            activeScreenLines.remove(SubtitleTtsTextCleaner.normalizeLineKey(pendingLine))
        }
        pendingCue = null
        isSpeechActive = false
        ++activePlaybackGen
    }

    /** Clears any buffered startup/pre-seek cue snapshot and stops active speech while preserving recent spoken history. */
    fun clearBufferedActiveCue(): Long = synchronized(lock) {
        lastActiveRawText = ""
        lastActiveTimingContext = null
        pendingCue = null
        activeScreenLines.clear()
        isSpeechActive = false
        ++activePlaybackGen
    }

    /** Clears all spoken history, active screen lines, and pending cues (for user seek / track change / new stream). */
    fun resetAllHistory(): Long = synchronized(lock) {
        lastActiveRawText = ""
        lastActiveTimingContext = null
        activeScreenLines.clear()
        recentSpokenLines.clear()
        lastSpokenKey = ""
        pendingCue = null
        isSpeechActive = false
        ++activePlaybackGen
    }

    /** Returns the latest active cue snapshot without wiping spoken history (for playback resume / first-frame start). */
    fun snapshotActiveCue(): Pair<String, SubtitleCuePlaybackContext?> = synchronized(lock) {
        lastActiveRawText to lastActiveTimingContext
    }

    /** Clears spoken history and returns the latest active cue snapshot (used when toggling TTS OFF -> ON). */
    fun clearSpokenHistoryAndSnapshotActive(): Pair<String, SubtitleCuePlaybackContext?> = synchronized(lock) {
        activeScreenLines.clear()
        recentSpokenLines.clear()
        lastSpokenKey = ""
        lastActiveRawText to lastActiveTimingContext
    }

    private fun isContinuousActiveLine(
        active: ActiveScreenLine,
        newCueStartMs: Long?,
    ): Boolean {
        val prevEndMs = active.cueEndMs
        if (prevEndMs != null && newCueStartMs != null) {
            return newCueStartMs <= prevEndMs + CONTINUOUS_CUE_GAP_MS
        }
        val prevStartMs = active.cueStartMs
        if (prevStartMs != null && newCueStartMs != null) {
            return abs(newCueStartMs - prevStartMs) <= TIMING_JITTER_TOLERANCE_MS
        }
        return true
    }

    private fun resolveRollingExtension(
        previousActiveLines: List<ActiveScreenLine>,
        currentLine: String,
        newCueStartMs: Long?,
    ): SubtitleTtsTextCleaner.PrefixExtensionResult {
        for (prev in previousActiveLines) {
            if (!isContinuousActiveLine(prev, newCueStartMs)) continue
            val result = SubtitleTtsTextCleaner.extractAppendedSuffix(prev.cleanLine, currentLine)
            if (result !is SubtitleTtsTextCleaner.PrefixExtensionResult.NotExtension) {
                return result
            }
        }
        return SubtitleTtsTextCleaner.PrefixExtensionResult.NotExtension
    }

    private fun isDuplicateOfRecentLocked(
        lineKey: String,
        newCueStartMs: Long?,
        now: Long,
    ): Boolean {
        for (i in recentSpokenLines.lastIndex downTo 0) {
            val record = recentSpokenLines[i]
            if (record.key != lineKey) continue

            val prevStartMs = record.cueStartMs
            val prevEndMs = record.cueEndMs

            if (newCueStartMs != null && prevStartMs != null) {
                if (newCueStartMs == prevStartMs) return true
                val lowerBound = prevStartMs - TIMING_JITTER_TOLERANCE_MS
                val upperBound = if (prevEndMs != null) {
                    maxOf(prevStartMs + TIMING_JITTER_TOLERANCE_MS, prevEndMs + CONTINUOUS_CUE_GAP_MS)
                } else {
                    prevStartMs + TIMING_JITTER_TOLERANCE_MS
                }
                if (newCueStartMs in lowerBound..upperBound) return true
                continue
            }

            if (newCueStartMs != null || prevStartMs != null) {
                // One side had timingContext == null (e.g., TTS enabled mid-cue or MPV sub-start lag).
                if ((now - record.spokenAtMs).coerceAtLeast(0L) <= NULL_TIMING_DEDUP_WINDOW_MS) {
                    return true
                }
                continue
            }

            // Both sides have timingContext == null (e.g., ExoPlayer built-in track).
            if (lineKey == lastSpokenKey) return true
            if ((now - record.spokenAtMs).coerceAtLeast(0L) <= NO_TIMING_RECENT_WINDOW_MS) {
                return true
            }
        }
        return false
    }

    private fun extendMatchingRecentRecordLocked(
        lineKey: String,
        newCueStartMs: Long?,
        newCueEndMs: Long?,
        now: Long,
    ) {
        for (i in recentSpokenLines.lastIndex downTo 0) {
            val record = recentSpokenLines[i]
            if (record.key != lineKey) continue
            if (record.cueStartMs == null && newCueStartMs != null) {
                record.cueStartMs = newCueStartMs
            }
            record.cueEndMs = mergeEndMs(record.cueEndMs, newCueEndMs)
            record.spokenAtMs = now
            return
        }
    }

    private fun isDuplicateOfPendingLocked(
        effectiveKey: String,
        cueStartMs: Long?,
        now: Long,
    ): Boolean {
        val currentPending = pendingCue ?: return false
        val pendingKey = SubtitleTtsTextCleaner.normalizeLineKey(currentPending.cleanText)
        if (effectiveKey != pendingKey) return false
        val pendingStartMs = currentPending.timingContext?.currentCue?.startMs
        val pendingEndMs = currentPending.timingContext?.currentCue?.endMs
        return when {
            cueStartMs == null || pendingStartMs == null ->
                (now - currentPending.arrivedAtMs).coerceAtLeast(0L) <= NULL_TIMING_DEDUP_WINDOW_MS
            cueStartMs == pendingStartMs -> true
            pendingEndMs != null ->
                cueStartMs in (pendingStartMs - TIMING_JITTER_TOLERANCE_MS)..(pendingEndMs + CONTINUOUS_CUE_GAP_MS)
            else -> abs(cueStartMs - pendingStartMs) <= TIMING_JITTER_TOLERANCE_MS
        }
    }

    private fun recordSpokenLinesLocked(
        novelLines: List<String>,
        allCurrentLines: List<String>,
        effectiveKey: String,
        cueStartMs: Long?,
        cueEndMs: Long?,
        now: Long,
    ) {
        lastSpokenKey = effectiveKey
        for (line in novelLines) {
            appendRecentRecordLocked(line, cueStartMs, cueEndMs, now)
        }
        // Also remember full rolling-caption lines so subsequent frames don't re-trigger full text.
        for (line in allCurrentLines) {
            val key = SubtitleTtsTextCleaner.normalizeLineKey(line)
            if (recentSpokenLines.none { it.key == key && it.cueStartMs == cueStartMs && it.spokenAtMs == now }) {
                appendRecentRecordLocked(line, cueStartMs, cueEndMs, now)
            }
        }
    }

    private fun appendRecentRecordLocked(
        cleanLine: String,
        cueStartMs: Long?,
        cueEndMs: Long?,
        now: Long,
    ) {
        val key = SubtitleTtsTextCleaner.normalizeLineKey(cleanLine)
        recentSpokenLines.addLast(
            SpokenLineRecord(
                key = key,
                cleanLine = cleanLine,
                cueStartMs = cueStartMs,
                cueEndMs = cueEndMs,
                spokenAtMs = now,
            ),
        )
        while (recentSpokenLines.size > MAX_RECENT_SPOKEN_LINES) {
            recentSpokenLines.removeFirst()
        }
    }

    private fun mergeEndMs(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }
}
