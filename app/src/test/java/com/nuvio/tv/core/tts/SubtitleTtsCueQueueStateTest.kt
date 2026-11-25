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
        val first = queue.onCueArrived("Câu một", cue1, waitForCueCompletion = true) as CueDispatchDecision.SpeakNow
        val gen1 = first.playbackGen
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
        assertTrue(advance.playbackGen > gen1)
        assertFalse(queue.hasPendingCue())
        assertTrue(queue.hasActiveSpeech())
    }

    @Test
    fun dropsStaleQueuedCueBeyondMaxQueueStaleMs() {
        var nowMs = 0L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        val first = queue.onCueArrived("Câu đầu", null, waitForCueCompletion = true) as CueDispatchDecision.SpeakNow
        val gen1 = first.playbackGen

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

        val first = queue.onCueArrived("Câu dài", null, waitForCueCompletion = true) as CueDispatchDecision.SpeakNow
        val gen1 = first.playbackGen

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

        val first = queue.onCueArrived("Câu một", null, waitForCueCompletion = true) as CueDispatchDecision.SpeakNow
        val gen1 = first.playbackGen

        nowMs = 200L
        assertTrue(queue.onCueArrived("Câu hai", null, waitForCueCompletion = true) is CueDispatchDecision.QueuedPending)
        assertTrue(queue.hasPendingCue())

        queue.resetActivePlayback()
        assertFalse(queue.hasPendingCue())
        assertFalse(queue.hasActiveSpeech())
        assertEquals(CompletionAdvanceDecision.Idle, queue.onPlaybackCompleted(gen1))
    }

    @Test
    fun overlappingCuesSpeakOnlyNewLinesWithoutRepeatingAOrB() {
        var nowMs = 10_000L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        val ctxA = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Anh đi đâu đấy?", startMs = 10_000L, endMs = 14_000L),
        )
        val first = queue.onCueArrived("Anh đi đâu đấy?", ctxA, waitForCueCompletion = false) as CueDispatchDecision.SpeakNow
        assertEquals("Anh đi đâu đấy?", first.cleanText)

        nowMs = 12_000L
        val ctxAB = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Anh đi đâu đấy?\nTôi đi ra ngoài.", startMs = 10_000L, endMs = 16_000L),
            positionMs = 12_000L,
        )
        val second = queue.onCueArrived(
            "Anh đi đâu đấy?\nTôi đi ra ngoài.",
            ctxAB,
            waitForCueCompletion = false,
        ) as CueDispatchDecision.SpeakNow
        assertEquals("Tôi đi ra ngoài.", second.cleanText)

        nowMs = 14_000L
        val ctxB = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Tôi đi ra ngoài.", startMs = 12_000L, endMs = 16_000L),
            positionMs = 14_000L,
        )
        assertEquals(
            CueDispatchDecision.Ignored,
            queue.onCueArrived("Tôi đi ra ngoài.", ctxB, waitForCueCompletion = false),
        )
    }

    @Test
    fun persistentSignWithDialogueLinesReadsSignOnlyOnceWithAndWithoutTiming() {
        var nowMs = 0L
        val queueWithTiming = SubtitleTtsCueQueueState(nowMs = { nowMs })
        val signCtx = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Phòng thí nghiệm", startMs = 10_000L, endMs = 25_000L),
        )

        assertEquals(
            "Phòng thí nghiệm",
            (queueWithTiming.onCueArrived("Phòng thí nghiệm", signCtx, false) as CueDispatchDecision.SpeakNow).cleanText,
        )
        nowMs = 1_000L
        assertEquals(
            "Xin chào",
            (queueWithTiming.onCueArrived("Phòng thí nghiệm\nXin chào", signCtx.copy(positionMs = 11_000L), false) as CueDispatchDecision.SpeakNow).cleanText,
        )
        nowMs = 3_000L
        assertEquals(
            CueDispatchDecision.Ignored,
            queueWithTiming.onCueArrived("Phòng thí nghiệm", signCtx.copy(positionMs = 13_000L), false),
        )
        nowMs = 5_000L
        assertEquals(
            "Bạn khỏe không?",
            (queueWithTiming.onCueArrived("Phòng thí nghiệm\nBạn khỏe không?", signCtx.copy(positionMs = 15_000L), false) as CueDispatchDecision.SpeakNow).cleanText,
        )
        nowMs = 7_000L
        assertEquals(
            CueDispatchDecision.Ignored,
            queueWithTiming.onCueArrived("Phòng thí nghiệm", signCtx.copy(positionMs = 17_000L), false),
        )

        // Same behavior when timingContext is unavailable (ExoPlayer built-in track)
        val queueNoTiming = SubtitleTtsCueQueueState(nowMs = { nowMs })
        assertEquals(
            "Phòng thí nghiệm",
            (queueNoTiming.onCueArrived("Phòng thí nghiệm", null, false) as CueDispatchDecision.SpeakNow).cleanText,
        )
        assertEquals(
            "Xin chào",
            (queueNoTiming.onCueArrived("Phòng thí nghiệm\nXin chào", null, false) as CueDispatchDecision.SpeakNow).cleanText,
        )
        assertEquals(CueDispatchDecision.Ignored, queueNoTiming.onCueArrived("Phòng thí nghiệm", null, false))
        assertEquals(
            "Bạn khỏe không?",
            (queueNoTiming.onCueArrived("Phòng thí nghiệm\nBạn khỏe không?", null, false) as CueDispatchDecision.SpeakNow).cleanText,
        )
        assertEquals(CueDispatchDecision.Ignored, queueNoTiming.onCueArrived("Phòng thí nghiệm", null, false))
    }

    @Test
    fun rollingCaptionsSpeakOnlyAppendedSuffixAndPreserveNonBoundaryWords() {
        var nowMs = 0L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        val first = queue.onCueArrived("Tôi đang", null, false) as CueDispatchDecision.SpeakNow
        assertEquals("Tôi đang", first.cleanText)

        nowMs = 400L
        val second = queue.onCueArrived("Tôi đang đi ra ngoài", null, false) as CueDispatchDecision.SpeakNow
        assertEquals("đi ra ngoài", second.cleanText)

        nowMs = 700L
        assertEquals(CueDispatchDecision.Ignored, queue.onCueArrived("Tôi đang đi ra ngoài.", null, false))

        nowMs = 3_500L
        val nameFirst = queue.onCueArrived("An", null, false) as CueDispatchDecision.SpeakNow
        assertEquals("An", nameFirst.cleanText)

        nowMs = 4_000L
        val nameSecond = queue.onCueArrived("Anh ấy đi rồi", null, false) as CueDispatchDecision.SpeakNow
        assertEquals("Anh ấy đi rồi", nameSecond.cleanText)
    }

    @Test
    fun deduplicatesWhenSdhNeighborEndsAndEarliestStartMsShifts() {
        var nowMs = 11_000L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        val withSdhCtx = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("[tiếng cửa]\nXin chào", startMs = 10_000L, endMs = 14_000L),
            positionMs = 11_000L,
        )
        val first = queue.onCueArrived("[tiếng cửa]\nXin chào", withSdhCtx, false) as CueDispatchDecision.SpeakNow
        assertEquals("Xin chào", first.cleanText)

        nowMs = 12_000L
        val afterSdhEndsCtx = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Xin chào", startMs = 11_000L, endMs = 14_000L),
            positionMs = 12_000L,
        )
        assertEquals(
            CueDispatchDecision.Ignored,
            queue.onCueArrived("Xin chào", afterSdhEndsCtx, false),
        )
    }

    @Test
    fun deduplicatesAssFrameSplitCuesAcrossConsecutiveFrames() {
        var nowMs = 0L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        var spokenCount = 0
        for (frame in 0 until 50) {
            val startMs = 10_000L + frame * 40L
            val endMs = startMs + 40L
            nowMs = frame * 40L
            val ctx = SubtitleCuePlaybackContext(
                currentCue = TimedSpeechCue("Tiêu đề phim", startMs = startMs, endMs = endMs),
            )
            if (queue.onCueArrived("Tiêu đề phim", ctx, false) is CueDispatchDecision.SpeakNow) {
                spokenCount++
            }
        }
        assertEquals(1, spokenCount)
    }

    @Test
    fun deduplicatesTimingContextTransitionNullToNonNullAndAutoSyncShift() {
        var nowMs = 100L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        assertTrue(queue.onCueArrived("Xin chào", null, false) is CueDispatchDecision.SpeakNow)

        nowMs = 300L
        val ctx = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Xin chào", startMs = 44_000L, endMs = 46_000L),
        )
        assertEquals(CueDispatchDecision.Ignored, queue.onCueArrived("Xin chào", ctx, false))

        // AutoSync shifts timeline by +500ms with a brief blank gap
        nowMs = 600L
        assertEquals(CueDispatchDecision.Ignored, queue.onCueArrived("", null, false))

        nowMs = 800L
        val shiftedCtx = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Xin chào", startMs = 44_500L, endMs = 46_500L),
        )
        assertEquals(CueDispatchDecision.Ignored, queue.onCueArrived("Xin chào", shiftedCtx, false))
    }

    @Test
    fun pauseAndResumePreservesSpokenLinesAndAllowsUnspokenPendingLine() {
        var nowMs = 1_000L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        val first = queue.onCueArrived("Câu một", null, waitForCueCompletion = true) as CueDispatchDecision.SpeakNow
        assertEquals("Câu một", first.cleanText)

        nowMs = 1_500L
        assertTrue(
            queue.onCueArrived("Câu một\nCâu hai", null, waitForCueCompletion = true) is CueDispatchDecision.QueuedPending,
        )

        // Pause before "Câu hai" got to speak
        queue.resetActivePlayback()

        // Resume long after pause: "Câu một" must NOT repeat, while un-spoken "Câu hai" MUST speak
        nowMs = 15_000L
        val (activeRaw, activeCtx) = queue.snapshotActiveCue()
        val onResume = queue.onCueArrived(activeRaw, activeCtx, waitForCueCompletion = true) as CueDispatchDecision.SpeakNow
        assertEquals("Câu hai", onResume.cleanText)

        // Pausing & resuming again after both were spoken must stay silent
        queue.resetActivePlayback()
        nowMs = 30_000L
        val (rawAfterBoth, ctxAfterBoth) = queue.snapshotActiveCue()
        assertEquals(
            CueDispatchDecision.Ignored,
            queue.onCueArrived(rawAfterBoth, ctxAfterBoth, waitForCueCompletion = true),
        )
    }

    @Test
    fun clearBufferedActiveCueDropsPreSeekCueWhilePreservingRecentSpokenHistory() {
        var nowMs = 1_000L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        val ctx = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Câu vừa đọc", startMs = 20_000L, endMs = 23_000L),
        )
        assertTrue(queue.onCueArrived("Câu vừa đọc", ctx, false) is CueDispatchDecision.SpeakNow)

        // In-place rebuild at same position clears buffered cue snapshot but retains recent spoken history
        queue.clearBufferedActiveCue()
        assertEquals("", queue.snapshotActiveCue().first)

        nowMs = 2_000L
        assertEquals(
            CueDispatchDecision.Ignored,
            queue.onCueArrived("Câu vừa đọc", ctx.copy(positionMs = 20_500L), false),
        )
    }
}
