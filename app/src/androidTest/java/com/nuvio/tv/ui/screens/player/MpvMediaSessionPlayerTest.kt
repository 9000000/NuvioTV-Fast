package com.nuvio.tv.ui.screens.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MpvMediaSessionPlayerTest {

    @Test
    fun exposesSingleItemControlsAndForwardsCommandsToMpvTarget() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val target = RecordingTarget()
            val player = MpvMediaSessionPlayer(
                snapshotProvider = { snapshot() },
                commandTarget = target
            )

            assertTrue(player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
            assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
            assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_BACK))
            assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))
            assertFalse(player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH))
            assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))

            player.pause()
            player.play()
            player.seekTo(12_345L)

            assertEquals(listOf(false, true), target.playWhenReadyCalls)
            assertEquals(listOf(12_345L), target.seekCalls)
            assertEquals(12_000L, player.duration)
            assertEquals(2_000L, player.currentPosition)
            player.release()
        }
    }

    @Test
    fun doesNotExposeSeekCommandsForUnseekableItem() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player = MpvMediaSessionPlayer(
                snapshotProvider = { snapshot(isSeekable = false) },
                commandTarget = RecordingTarget()
            )

            assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
            assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_BACK))
            assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))
            player.release()
        }
    }

    private fun snapshot(isSeekable: Boolean = true) = MpvMediaSessionSnapshot(
        mediaId = "stream-id",
        mediaItem = MediaItem.Builder()
            .setMediaId("stream-id")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Episode").build())
            .build(),
        durationMs = 12_000L,
        positionMs = 2_000L,
        bufferedPositionMs = 4_000L,
        isPlaying = true,
        isBuffering = false,
        isEnded = false,
        isSeekable = isSeekable,
        speed = 1f,
    )

    private class RecordingTarget : MpvMediaSessionCommandTarget {
        val playWhenReadyCalls = mutableListOf<Boolean>()
        val seekCalls = mutableListOf<Long>()

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            playWhenReadyCalls += playWhenReady
        }

        override fun seekTo(positionMs: Long) {
            seekCalls += positionMs
        }
    }
}
