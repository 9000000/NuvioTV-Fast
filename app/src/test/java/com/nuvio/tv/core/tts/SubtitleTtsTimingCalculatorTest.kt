package com.nuvio.tv.core.tts

import com.nuvio.tv.data.local.PlayerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTtsTimingCalculatorTest {

    @Test
    fun configConstantsAndPlayerSettingsDefaultsMatchSpec() {
        assertEquals(1.4f, SubtitleTtsTimingCalculator.DEFAULT_BASE_SPEED, 0.001f)
        assertEquals(2.0f, SubtitleTtsTimingCalculator.MAX_BASE_SPEED, 0.001f)
        assertEquals(2.5f, SubtitleTtsTimingCalculator.DEFAULT_MAX_AUTO_SPEED, 0.001f)
        assertEquals(3.0f, SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED, 0.001f)
        assertEquals(0.8f, SubtitleTtsTimingCalculator.MIN_UI_BASE_SPEED, 0.001f)
        assertEquals(1.4f, SubtitleTtsTimingCalculator.MIN_MAX_AUTO_SPEED, 0.001f)

        val settings = PlayerSettings()
        assertFalse(settings.ttsEnabled)
        assertNull(settings.ttsVoiceName)
        assertFalse(settings.ttsAllowNetworkVoiceFallback)
        assertEquals(0.25f, settings.ttsMovieVolume, 0.001f)
        assertEquals(1.0f, settings.ttsVoiceVolume, 0.001f)
        assertEquals(1.4f, settings.ttsBaseSpeed, 0.001f)
        assertEquals(1.0f, settings.ttsPitch, 0.001f)
        assertTrue(settings.ttsAutoSpeedEnabled)
        assertEquals(2.5f, settings.ttsMaxAutoSpeed, 0.001f)
        assertFalse(settings.ttsWaitForCueCompletion)
        assertFalse(settings.ttsHideSubtitlesWhenEnabled)

        val config = settings.toSubtitleTtsConfig()
        assertFalse(config.shouldHideVisualSubtitles)
        assertTrue(
            settings.copy(ttsEnabled = true, ttsHideSubtitlesWhenEnabled = true)
                .toSubtitleTtsConfig()
                .shouldHideVisualSubtitles,
        )

        val cue = TimedSpeechCue(
            rawText = "Xin chào",
            startMs = 1_500L,
            endMs = 3_000L,
            nextCueStartMs = 3_500L,
        )
        val context = SubtitleCuePlaybackContext(currentCue = cue)
        assertEquals(1_500L, context.positionMs)
        assertEquals(1.0f, context.playbackSpeed, 0.001f)
        assertTrue(context.upcomingCues.isEmpty())
    }

    @Test
    fun resolveSpeechBudgetMsReturnsNullWhenContextOrSlotEndMissing() {
        assertNull(SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(null))

        val liveCue = TimedSpeechCue(rawText = "Live caption", startMs = 1_000L, endMs = null, nextCueStartMs = null)
        assertNull(
            SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
                SubtitleCuePlaybackContext(currentCue = liveCue),
            ),
        )

        val expiredCue = TimedSpeechCue(rawText = "Expired", startMs = 1_000L, endMs = 2_000L, nextCueStartMs = 2_500L)
        assertNull(
            SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
                SubtitleCuePlaybackContext(currentCue = expiredCue, positionMs = 2_500L),
            ),
        )
    }

    @Test
    fun resolveSpeechBudgetMsPrioritizesNextCueStartOverEndMsAndFallsBackToEndMs() {
        val cueWithGap = TimedSpeechCue(
            rawText = "Câu ngắn nhưng có khoảng nghỉ dài",
            startMs = 1_000L,
            endMs = 2_200L,
            nextCueStartMs = 5_000L,
        )
        val budgetFromNextCue = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
            SubtitleCuePlaybackContext(currentCue = cueWithGap, positionMs = 1_000L),
        )
        assertEquals(3_920L, budgetFromNextCue)

        val lastCueInTrack = TimedSpeechCue(
            rawText = "Câu cuối cùng",
            startMs = 10_000L,
            endMs = 12_500L,
            nextCueStartMs = null,
        )
        val budgetFromEndMs = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
            SubtitleCuePlaybackContext(currentCue = lastCueInTrack, positionMs = 10_000L),
        )
        assertEquals(2_420L, budgetFromEndMs)
    }

    @Test
    fun resolveSpeechBudgetMsAccountsForLateEntryPlaybackSpeedAndMinimumFloor() {
        val cue = TimedSpeechCue(
            rawText = "Chúng ta phải đi ngay.",
            startMs = 2_000L,
            endMs = 3_600L,
            nextCueStartMs = 4_000L,
        )

        val lateBudget = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
            SubtitleCuePlaybackContext(
                currentCue = cue,
                playbackSpeed = 1.25f,
                positionMs = 2_400L,
            ),
        )
        assertEquals(1_200L, lateBudget)

        val tinyCue = TimedSpeechCue(
            rawText = "Ồ",
            startMs = 1_000L,
            endMs = 1_200L,
            nextCueStartMs = 1_260L,
        )
        val clampedBudget = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
            SubtitleCuePlaybackContext(currentCue = tinyCue, playbackSpeed = 2.0f),
        )
        assertEquals(SubtitleTtsTimingCalculator.MIN_SPEECH_BUDGET_MS, clampedBudget)

        val nonFiniteSpeedBudget = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
            SubtitleCuePlaybackContext(currentCue = cue, playbackSpeed = Float.NaN),
        )
        assertEquals(1_920L, nonFiniteSpeedBudget)
    }

    @Test
    fun estimateNaturalDurationMsScalesWithWordsExtraLettersAndPunctuation() {
        assertEquals(0L, SubtitleTtsTimingCalculator.estimateNaturalDurationMs("   "))

        val simple = SubtitleTtsTimingCalculator.estimateNaturalDurationMs("Xin chào")
        assertEquals(580L, simple)

        val withPunctuation = SubtitleTtsTimingCalculator.estimateNaturalDurationMs("Xin chào, tôi là Minh.")
        assertEquals(1_500L, withPunctuation)

        val ellipsis = SubtitleTtsTimingCalculator.estimateNaturalDurationMs("Không...")
        val singleDot = SubtitleTtsTimingCalculator.estimateNaturalDurationMs("Không.")
        assertEquals(singleDot, ellipsis)

        val longEnglish = SubtitleTtsTimingCalculator.estimateNaturalDurationMs("extraordinary")
        val shortEnglish = SubtitleTtsTimingCalculator.estimateNaturalDurationMs("good")
        assertTrue(longEnglish > shortEnglish)
    }

    @Test
    fun calculateTimingAwareSpeedKeepsBaseSpeedWhenPlentyOfTimeExists() {
        val longSentence = "Anh ấy đã quyết định rời khỏi thành phố vào sáng sớm hôm sau để bắt đầu lại."
        val generousContext = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue(
                rawText = longSentence,
                startMs = 0L,
                endMs = 3_500L,
                nextCueStartMs = 6_000L,
            ),
        )
        val budgetMs = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(generousContext)
        val speed = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = longSentence,
            budgetMs = budgetMs,
            baseSpeed = 1.0f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 2.1f,
        )
        assertEquals(1.0f, speed, 0.001f)
    }

    @Test
    fun calculateTimingAwareSpeedAcceleratesTightCuesAndRespectsBounds() {
        val mediumSentence = "Chúng ta không còn nhiều thời gian nữa đâu!"
        val tightContext = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue(
                rawText = mediumSentence,
                startMs = 1_000L,
                endMs = 2_400L,
                nextCueStartMs = 2_600L,
            ),
            playbackSpeed = 1.25f,
        )
        val tightBudgetMs = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(tightContext)
        val accelerated = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = mediumSentence,
            budgetMs = tightBudgetMs,
            baseSpeed = 1.0f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 2.1f,
        )
        assertTrue("Expected accelerated speed in 1.4..2.1, got $accelerated", accelerated in 1.4f..2.1f)

        val veryLongSentence = "Tôi thực sự không thể tin rằng mọi chuyện lại diễn ra nhanh như vậy trong buổi tối hôm nay."
        val capped = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = veryLongSentence,
            budgetMs = 900L,
            baseSpeed = 1.0f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 2.1f,
        )
        assertEquals(2.1f, capped, 0.001f)

        val cappedAtThree = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = veryLongSentence,
            budgetMs = 900L,
            baseSpeed = 1.0f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 3.0f,
        )
        assertEquals(3.0f, cappedAtThree, 0.001f)

        val disabled = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = veryLongSentence,
            budgetMs = 900L,
            baseSpeed = 1.15f,
            autoSpeedEnabled = false,
            maxAutoSpeed = 2.1f,
        )
        assertEquals(1.15f, disabled, 0.001f)
    }

    @Test
    fun calculateTimingAwareSpeedFallsBackToLengthScalingWhenBudgetNull() {
        val veryLongText = "a".repeat(100)
        val fallbackSpeed = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = veryLongText,
            budgetMs = null,
            baseSpeed = 1.5f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 1.9f,
        )
        assertEquals(1.9f, fallbackSpeed, 0.001f)
    }

    @Test
    fun steppedOptionsStayWithinClampBoundsAndCycleWithoutGettingStuck() {
        fun assertCyclesCleanly(
            steps: List<Float>,
            defaultVal: Float,
            clamp: (Float) -> Float,
        ) {
            assertTrue("Steps must be strictly increasing", steps.zipWithNext().all { (a, b) -> b > a })
            steps.forEach { step ->
                assertEquals("Step $step must not be altered by clamp", step, clamp(step), 0.001f)
            }
            // Cycling from the default value must visit every step without getting stuck.
            var current = clamp(defaultVal)
            val visited = mutableSetOf<Float>()
            repeat(steps.size) {
                val next = clamp(SubtitleTtsTimingCalculator.nextSteppedOption(steps, current))
                assertTrue("Cycling stuck at $current", kotlin.math.abs(next - current) > 0.005f)
                visited += next
                current = next
            }
            assertEquals("Cycle should cover all steps", steps.size, visited.size)
        }

        assertCyclesCleanly(
            steps = SubtitleTtsTimingCalculator.MOVIE_VOLUME_STEPS,
            defaultVal = SubtitleTtsTimingCalculator.DEFAULT_MOVIE_VOLUME,
            clamp = { SubtitleTtsTimingCalculator.clampVolume(it, SubtitleTtsTimingCalculator.DEFAULT_MOVIE_VOLUME) },
        )
        assertCyclesCleanly(
            steps = SubtitleTtsTimingCalculator.VOICE_VOLUME_STEPS,
            defaultVal = SubtitleTtsTimingCalculator.DEFAULT_VOICE_VOLUME,
            clamp = { SubtitleTtsTimingCalculator.clampVolume(it, SubtitleTtsTimingCalculator.DEFAULT_VOICE_VOLUME) },
        )
        assertCyclesCleanly(
            steps = SubtitleTtsTimingCalculator.BASE_SPEED_STEPS,
            defaultVal = SubtitleTtsTimingCalculator.DEFAULT_BASE_SPEED,
            clamp = SubtitleTtsTimingCalculator::clampBaseSpeed,
        )
        assertCyclesCleanly(
            steps = SubtitleTtsTimingCalculator.PITCH_STEPS,
            defaultVal = SubtitleTtsTimingCalculator.DEFAULT_PITCH,
            clamp = SubtitleTtsTimingCalculator::clampPitch,
        )
        assertCyclesCleanly(
            steps = SubtitleTtsTimingCalculator.MAX_AUTO_SPEED_STEPS,
            defaultVal = SubtitleTtsTimingCalculator.DEFAULT_MAX_AUTO_SPEED,
            clamp = SubtitleTtsTimingCalculator::clampMaxAutoSpeed,
        )

        // Regression check for off-step legacy values (e.g. 1.25f clamped to 1.4f, or 2.1f default).
        assertEquals(
            1.60f,
            SubtitleTtsTimingCalculator.nextSteppedOption(SubtitleTtsTimingCalculator.MAX_AUTO_SPEED_STEPS, 1.40f),
            0.001f,
        )
        assertEquals(
            2.25f,
            SubtitleTtsTimingCalculator.nextSteppedOption(SubtitleTtsTimingCalculator.MAX_AUTO_SPEED_STEPS, 2.10f),
            0.001f,
        )
        assertEquals(
            1.40f,
            SubtitleTtsTimingCalculator.nextSteppedOption(SubtitleTtsTimingCalculator.MAX_AUTO_SPEED_STEPS, 3.00f),
            0.001f,
        )
    }

    @Test
    fun dynamicCarryOverAvoidsLongDelayAfterEstimatedSpeechEnds() {
        val estimated = SubtitleTtsTimingCalculator.estimateSpokenDurationMs("Xin chào.", speechSpeed = 1.0f)
        assertTrue(estimated in 400L..1_000L)

        // When current speech has already exceeded its estimated length (trailing silence / missed onDone),
        // carry-over should stay minimal instead of waiting the full 1500ms.
        val tailWait = SubtitleTtsTimingCalculator.computeDynamicCarryOverMs(
            elapsedCurrentSpeechMs = estimated + 100L,
            estimatedCurrentSpeechMs = estimated,
            pendingCueBudgetMs = 2_000L,
        )
        assertEquals(220L, tailWait)

        // When current speech still has ~400ms left and next cue has plenty of budget, allow finishing.
        val finishWait = SubtitleTtsTimingCalculator.computeDynamicCarryOverMs(
            elapsedCurrentSpeechMs = (estimated - 400L).coerceAtLeast(0L),
            estimatedCurrentSpeechMs = estimated,
            pendingCueBudgetMs = 2_500L,
        )
        assertTrue(finishWait in 500L..700L)

        // When next cue has a short budget, cap carry-over to avoid snowball delay into subsequent cues.
        val cappedWait = SubtitleTtsTimingCalculator.computeDynamicCarryOverMs(
            elapsedCurrentSpeechMs = 100L,
            estimatedCurrentSpeechMs = 3_000L,
            pendingCueBudgetMs = 1_000L,
        )
        assertEquals(550L, cappedWait)
    }
}
