package com.nuvio.tv.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTtsCueQueueStateTest {

    @Test
    fun immediateModeDeduplicatesSameCueAndAllowsRepeatedTextAtNewStartMs() {
        var nowMs = 0L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        val ctx1 = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Gì cơ?", startMs = 1_000L, endMs = 2_000L, nextCueStartMs = 2_500L),
        )
        val firstDecision = queue.onCueArrived("Gì cơ?", ctx1, waitForCueCompletion = false)
        assertTrue(firstDecision is CueDispatchDecision.SpeakNow)
        queue.beginPlayback()

        assertEquals(
            CueDispatchDecision.Ignored,
            queue.onCueArrived("Gì cơ?", ctx1.copy(positionMs = 1_200L), waitForCueCompletion = false),
        )

        val ctx2 = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Gì cơ?", startMs = 5_000L, endMs = 6_000L, nextCueStartMs = 6_500L),
        )
        val secondDecision = queue.onCueArrived("Gì cơ?", ctx2, waitForCueCompletion = false)
        assertTrue(secondDecision is CueDispatchDecision.SpeakNow)

        queue.resetAllHistory()
        val afterReset = queue.onCueArrived("Gì cơ?", ctx2, waitForCueCompletion = false)
        assertTrue(afterReset is CueDispatchDecision.SpeakNow)
    }

    @Test
    fun waitForCompletionOverwritesSinglePendingSlotAndAdjustsBudgetByOverdueMs() {
        var nowMs = 1_000L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        val cue1 = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Câu một", startMs = 1_000L, endMs = 2_000L, nextCueStartMs = 2_100L),
        )
        assertTrue(queue.onCueArrived("Câu một", cue1, waitForCueCompletion = true) is CueDispatchDecision.SpeakNow)
        val gen1 = queue.beginPlayback()
        assertTrue(queue.hasActiveSpeech())

        nowMs = 1_500L
        val cue2 = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Câu hai", startMs = 2_100L, endMs = 3_000L, nextCueStartMs = 3_200L),
        )
        val q2 = queue.onCueArrived("Câu hai", cue2, waitForCueCompletion = true) as CueDispatchDecision.QueuedPending
        assertEquals(gen1, q2.expectedActiveGen)
        assertTrue(q2.startCarryOverTimer)

        nowMs = 1_650L
        assertEquals(CueDispatchDecision.Ignored, queue.onCueArrived("Câu hai", cue2, waitForCueCompletion = true))
        assertEquals(CueDispatchDecision.Ignored, queue.onCueArrived("Câu một", cue1, waitForCueCompletion = true))

        nowMs = 1_800L
        val cue3 = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Câu ba", startMs = 3_200L, endMs = 5_000L, nextCueStartMs = 5_200L),
        )
        val q3 = queue.onCueArrived("Câu ba", cue3, waitForCueCompletion = true) as CueDispatchDecision.QueuedPending
        assertFalse(q3.startCarryOverTimer)

        assertEquals(CompletionAdvanceDecision.Idle, queue.onPlaybackCompleted(gen1 - 1))
        assertTrue(queue.hasPendingCue())

        nowMs = 2_300L
        val advance = queue.onPlaybackCompleted(gen1) as CompletionAdvanceDecision.SpeakQueued
        assertEquals("Câu ba", advance.request.cleanText)
        assertEquals(1_420L, advance.adjustedBudgetMs)
        assertFalse(queue.hasPendingCue())
        assertFalse(queue.hasActiveSpeech())
    }

    @Test
    fun dropsStaleQueuedCueBeyondMaxQueueStaleMs() {
        var nowMs = 0L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        assertTrue(queue.onCueArrived("Câu đầu", null, waitForCueCompletion = true) is CueDispatchDecision.SpeakNow)
        val gen1 = queue.beginPlayback()

        nowMs = 100L
        assertTrue(queue.onCueArrived("Câu trễ", null, waitForCueCompletion = true) is CueDispatchDecision.QueuedPending)

        nowMs = 100L + MAX_QUEUE_STALE_MS + 1L
        assertEquals(CompletionAdvanceDecision.DroppedStale, queue.onPlaybackCompleted(gen1))
        assertFalse(queue.hasPendingCue())
        assertFalse(queue.hasActiveSpeech())
    }

    @Test
    fun carryOverTimeoutAdvancesPendingAndInvalidatesStaleCompletionCallback() {
        var nowMs = 0L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        assertTrue(queue.onCueArrived("Câu dài", null, waitForCueCompletion = true) is CueDispatchDecision.SpeakNow)
        val gen1 = queue.beginPlayback()

        nowMs = 100L
        val pendingCtx = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Câu tiếp", startMs = 2_000L, endMs = 2_300L, nextCueStartMs = 2_400L),
        )
        assertTrue(queue.onCueArrived("Câu tiếp", pendingCtx, waitForCueCompletion = true) is CueDispatchDecision.QueuedPending)

        nowMs = 100L + MAX_CARRY_OVER_MS
        val timeoutAdvance = queue.onCarryOverTimeout(gen1) as CompletionAdvanceDecision.SpeakQueued
        assertEquals("Câu tiếp", timeoutAdvance.request.cleanText)
        assertEquals(SubtitleTtsTimingCalculator.MIN_SPEECH_BUDGET_MS, timeoutAdvance.adjustedBudgetMs)

        assertEquals(CompletionAdvanceDecision.Idle, queue.onPlaybackCompleted(gen1))
    }

    @Test
    fun resetClearsPendingAndInvalidatesActiveGeneration() {
        var nowMs = 0L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        assertTrue(queue.onCueArrived("Câu một", null, waitForCueCompletion = true) is CueDispatchDecision.SpeakNow)
        val gen1 = queue.beginPlayback()

        nowMs = 200L
        assertTrue(queue.onCueArrived("Câu hai", null, waitForCueCompletion = true) is CueDispatchDecision.QueuedPending)
        assertTrue(queue.hasPendingCue())

        queue.resetActivePlayback()
        assertFalse(queue.hasPendingCue())
        assertFalse(queue.hasActiveSpeech())
        assertEquals(CompletionAdvanceDecision.Idle, queue.onPlaybackCompleted(gen1))
    }
}
