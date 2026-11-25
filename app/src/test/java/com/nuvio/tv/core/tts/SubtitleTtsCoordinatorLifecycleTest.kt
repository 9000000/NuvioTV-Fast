package com.nuvio.tv.core.tts

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleTtsCoordinatorLifecycleTest {

    private fun createCoordinator(
        enabled: Boolean = true,
        waitForCueCompletion: Boolean = false,
    ): Pair<SubtitleTtsCoordinator, GoogleOfflineTtsEngine> {
        val engine = mockk<GoogleOfflineTtsEngine>(relaxed = true)
        every { engine.availability } returns MutableStateFlow(GoogleTtsAvailability.READY)
        every { engine.voices } returns MutableStateFlow(emptyList())
        every { engine.activeVoiceName } returns MutableStateFlow(null)
        every { engine.isGoogleEngineActive } returns MutableStateFlow(true)
        every { engine.isSpeaking } returns MutableStateFlow(false)
        every { engine.hasActiveUtteranceStarted() } returns false
        every { engine.isEngineSpeakingNow() } returns false

        val configFlow = MutableStateFlow(
            SubtitleTtsConfig(
                enabled = enabled,
                waitForCueCompletion = waitForCueCompletion,
                transliterateEnglishNames = false,
            ),
        )
        val coordinator = SubtitleTtsCoordinator(
            engine = engine,
            configFlow = configFlow,
            mainDispatcher = UnconfinedTestDispatcher(),
            transliterator = null,
        )
        return coordinator to engine
    }

    @Test
    fun startSessionDoesNotSpeakBeforePlaybackBecomesActiveAndSpeaksBufferedCueOnceOnStart() {
        val (coordinator, engine) = createCoordinator(enabled = true)
        coordinator.startSession()

        val ctx = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Xin chào", startMs = 0L, endMs = 3_000L),
        )
        coordinator.onSubtitleCueChanged("Xin chào", ctx)

        verify(exactly = 0) {
            engine.speak(any(), any(), any(), any(), any(), any(), any(), any())
        }

        coordinator.setPlaybackActive(true)

        val spoken = mutableListOf<String>()
        verify(exactly = 1) {
            engine.speak(capture(spoken), any(), any(), any(), any(), any(), any(), any())
        }
        assertEquals(listOf("Xin chào."), spoken)
    }

    @Test
    fun clearBufferedCuePreventsZeroTimestampCueFromSpeakingAfterStartupResumeSeek() {
        val (coordinator, engine) = createCoordinator(enabled = true)
        coordinator.startSession()

        // Sidecar emits 00:00 intro cue while player is still preparing resume seek
        coordinator.onSubtitleCueChanged(
            "Giới thiệu mở đầu",
            SubtitleCuePlaybackContext(TimedSpeechCue("Giới thiệu mở đầu", startMs = 0L, endMs = 2_000L)),
        )
        coordinator.clearBufferedCue()
        coordinator.setPlaybackActive(true)

        verify(exactly = 0) {
            engine.speak(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun pauseAndResumeDoesNotRepeatAlreadySpokenCue() {
        val (coordinator, engine) = createCoordinator(enabled = true)
        coordinator.startSession(initiallyPlaying = true)

        val ctx = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Câu thoại một", startMs = 5_000L, endMs = 9_000L),
        )
        coordinator.onSubtitleCueChanged("Câu thoại một", ctx)
        coordinator.setPlaybackActive(false)
        coordinator.setPlaybackActive(true)

        verify(exactly = 1) {
            engine.speak(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun rebuildWithPreservedHistoryDoesNotRepeatLastSpokenCueWhileUserSeekDoes() {
        val (coordinator, engine) = createCoordinator(enabled = true)
        val token1 = coordinator.startSession(initiallyPlaying = true)

        val ctx = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Câu đang phát", startMs = 20_000L, endMs = 24_000L),
        )
        coordinator.onSubtitleCueChanged("Câu đang phát", ctx)

        // In-place player rebuild preserves spoken history
        coordinator.stopSession(token1, preserveSpokenHistory = true)
        coordinator.startSession(initiallyPlaying = false, preserveSpokenHistory = true)
        coordinator.onSubtitleCueChanged("Câu đang phát", ctx.copy(positionMs = 20_500L))
        coordinator.setPlaybackActive(true)

        verify(exactly = 1) {
            engine.speak(any(), any(), any(), any(), any(), any(), any(), any())
        }

        // User-initiated seek clears history so re-watching the scene speaks again
        coordinator.onSeek()
        coordinator.onSubtitleCueChanged("Câu đang phát", ctx)

        verify(exactly = 2) {
            engine.speak(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun previewVoiceDoesNotLeakIntoCueQueueCompletionCallbacks() {
        val (coordinator, engine) = createCoordinator(enabled = true, waitForCueCompletion = true)
        coordinator.startSession(initiallyPlaying = true)

        coordinator.previewVoice(sampleText = "Thử giọng")

        verify(exactly = 1) {
            engine.speak(
                text = "Thử giọng.",
                voiceName = any(),
                pitch = any(),
                speed = any(),
                volume = any(),
                allowNetworkVoiceFallback = any(),
                preferredLanguageCode = any(),
                onCompleted = null,
            )
        }
    }
}
