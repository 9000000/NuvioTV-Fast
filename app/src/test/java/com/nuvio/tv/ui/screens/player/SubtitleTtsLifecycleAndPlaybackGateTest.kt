package com.nuvio.tv.ui.screens.player

import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.extractor.text.CuesWithTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTtsLifecycleAndPlaybackGateTest {

    @Test
    fun `shouldAllowTtsPlayback blocks startup loading, ColdStartPrime, buffering, pending seek, and background`() {
        // ColdStartPrime: playWhenReady=true & STATE_READY but first frame not rendered yet
        assertFalse(
            shouldAllowTtsPlayback(
                isPlaying = true,
                isBuffering = false,
                hasRenderedFirstFrame = false,
                hasVideoTrackOrUnknown = true,
                hasPendingSeek = false,
                isPositionFromRequestedMedia = true,
                isReleasingPlayer = false,
                isInBackground = false,
            ),
        )

        // Buffering
        assertFalse(
            shouldAllowTtsPlayback(
                isPlaying = true,
                isBuffering = true,
                hasRenderedFirstFrame = true,
                hasVideoTrackOrUnknown = true,
                hasPendingSeek = false,
                isPositionFromRequestedMedia = true,
                isReleasingPlayer = false,
                isInBackground = false,
            ),
        )

        // Pending startup resume seek
        assertFalse(
            shouldAllowTtsPlayback(
                isPlaying = true,
                isBuffering = false,
                hasRenderedFirstFrame = true,
                hasVideoTrackOrUnknown = true,
                hasPendingSeek = true,
                isPositionFromRequestedMedia = true,
                isReleasingPlayer = false,
                isInBackground = false,
            ),
        )

        // MPV still reporting previous file before loadfile completes
        assertFalse(
            shouldAllowTtsPlayback(
                isPlaying = true,
                isBuffering = false,
                hasRenderedFirstFrame = true,
                hasVideoTrackOrUnknown = true,
                hasPendingSeek = false,
                isPositionFromRequestedMedia = false,
                isReleasingPlayer = false,
                isInBackground = false,
            ),
        )

        // Paused / backgrounded / releasing
        assertFalse(
            shouldAllowTtsPlayback(
                isPlaying = false,
                isBuffering = false,
                hasRenderedFirstFrame = true,
                hasVideoTrackOrUnknown = true,
                hasPendingSeek = false,
                isPositionFromRequestedMedia = true,
                isReleasingPlayer = false,
                isInBackground = false,
            ),
        )
        assertFalse(
            shouldAllowTtsPlayback(
                isPlaying = true,
                isBuffering = false,
                hasRenderedFirstFrame = true,
                hasVideoTrackOrUnknown = true,
                hasPendingSeek = false,
                isPositionFromRequestedMedia = true,
                isReleasingPlayer = false,
                isInBackground = true,
            ),
        )

        // Active video playback after first frame
        assertTrue(
            shouldAllowTtsPlayback(
                isPlaying = true,
                isBuffering = false,
                hasRenderedFirstFrame = true,
                hasVideoTrackOrUnknown = true,
                hasPendingSeek = false,
                isPositionFromRequestedMedia = true,
                isReleasingPlayer = false,
                isInBackground = false,
            ),
        )

        // Audio-only stream with known tracks
        assertTrue(
            shouldAllowTtsPlayback(
                isPlaying = true,
                isBuffering = false,
                hasRenderedFirstFrame = false,
                hasVideoTrackOrUnknown = false,
                hasPendingSeek = false,
                isPositionFromRequestedMedia = true,
                isReleasingPlayer = false,
                isInBackground = false,
            ),
        )
    }

    @Test
    fun `CueTrackingTextOutput reports onCuesShown once when Media3 invokes both overloads`() {
        var delegateLegacyCalls = 0
        var delegateGroupCalls = 0
        val delegate = object : TextOutput {
            override fun onCues(cueGroup: CueGroup) {
                delegateGroupCalls++
            }

            @Deprecated("Deprecated in Media3")
            override fun onCues(cues: List<Cue>) {
                delegateLegacyCalls++
            }
        }
        val shown = mutableListOf<List<Cue>>()
        val tracking = CueTrackingTextOutput(delegate) { shown.add(it) }
        val cues = listOf(Cue.Builder().setText("Xin chào").build())

        @Suppress("DEPRECATION")
        tracking.onCues(cues)
        tracking.onCues(CueGroup(cues, 1_000_000L))

        assertEquals(1, delegateLegacyCalls)
        assertEquals(1, delegateGroupCalls)
        assertEquals(1, shown.size)
        assertEquals("Xin chào", shown.single().single().text.toString())
    }

    @Test
    fun `SubtitleCueTracker trims whitespace jitter and resets cleanly`() {
        val tracker = SubtitleCueTracker()
        tracker.updateTextWithTiming("Xin chào")
        val version1 = tracker.state.value.version
        tracker.updateTextWithTiming("  Xin chào\n")
        assertEquals(version1, tracker.state.value.version)

        tracker.reset()
        assertFalse(tracker.state.value.visible)
        assertEquals("", tracker.state.value.text)

        tracker.updateTextWithTiming("Xin chào")
        assertTrue(tracker.state.value.visible)
        assertTrue(tracker.state.value.version > version1)
    }

    @Test
    fun `collectSpeakableSidecarTimedCues ignores SDH and music cues when resolving earliestStartMs`() {
        val sdhCue = CuesWithTiming(
            listOf(Cue.Builder().setText("[tiếng cửa mở]").build()),
            10_000_000L,
            2_000_000L,
        )
        val musicCue = CuesWithTiming(
            listOf(Cue.Builder().setText("♪ ♫").build()),
            10_500_000L,
            2_000_000L,
        )
        val dialogueCue = CuesWithTiming(
            listOf(Cue.Builder().setText("Xin chào").build()),
            11_000_000L,
            3_000_000L,
        )
        val list = listOf(sdhCue, musicCue, dialogueCue)
        val prefixMax = buildSidecarPrefixMaxEndUs(list)

        val speakable = collectSpeakableSidecarTimedCues(
            cues = list,
            positionUs = 11_500_000L,
            stripSdh = false,
            prefixMaxEndUs = prefixMax,
        )
        assertEquals(1, speakable.size)
        assertEquals(11_000_000L, speakable.single().startTimeUs)
    }
}
