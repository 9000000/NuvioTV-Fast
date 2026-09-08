package com.nuvio.tv.ui.screens.player

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerFirstFrameCodecRecoveryPolicyTest {

    @After
    fun tearDown() {
        Vc1VideoFormatHeuristics.hasDeviceVc1DecoderOverride = null
    }

    @Test
    fun evaluate_whenNotPlayWhenReady_returnsNone() {
        val input = PlayerFirstFrameCodecRecoveryPolicy.Input(
            playWhenReady = false,
            isManualDv81Mode2Active = false,
            dv7Mode1AlreadyForced = false,
            currentVideoTrackIsLikelyVc1 = true,
            isVc1SoftwareFallbackActive = false,
            currentVideoTrackSelected = false,
            isVc1TrackSelectionBypassActive = false
        )
        assertEquals(PlayerFirstFrameCodecRecoveryPolicy.RecoveryAction.None, PlayerFirstFrameCodecRecoveryPolicy.evaluateAfterWatchdogTimeout(input))
    }

    @Test
    fun evaluate_whenVc1AndNoDeviceDecoder_failsImmediately() {
        Vc1VideoFormatHeuristics.hasDeviceVc1DecoderOverride = false

        val input = PlayerFirstFrameCodecRecoveryPolicy.Input(
            playWhenReady = true,
            isManualDv81Mode2Active = false,
            dv7Mode1AlreadyForced = false,
            currentVideoTrackIsLikelyVc1 = true,
            isVc1SoftwareFallbackActive = false,
            currentVideoTrackSelected = false,
            isVc1TrackSelectionBypassActive = false
        )
        assertEquals(
            PlayerFirstFrameCodecRecoveryPolicy.RecoveryAction.FailVc1Unsupported,
            PlayerFirstFrameCodecRecoveryPolicy.evaluateAfterWatchdogTimeout(input)
        )
    }

    @Test
    fun evaluate_whenVc1_alwaysFailsImmediatelyWithoutRetry() {
        val input = PlayerFirstFrameCodecRecoveryPolicy.Input(
            playWhenReady = true,
            isManualDv81Mode2Active = false,
            dv7Mode1AlreadyForced = false,
            currentVideoTrackIsLikelyVc1 = true,
            isVc1SoftwareFallbackActive = false,
            currentVideoTrackSelected = true,
            isVc1TrackSelectionBypassActive = false
        )
        assertEquals(
            PlayerFirstFrameCodecRecoveryPolicy.RecoveryAction.FailVc1Unsupported,
            PlayerFirstFrameCodecRecoveryPolicy.evaluateAfterWatchdogTimeout(input)
        )
    }
}
