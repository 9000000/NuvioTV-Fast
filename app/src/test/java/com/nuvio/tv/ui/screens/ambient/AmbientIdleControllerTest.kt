package com.nuvio.tv.ui.screens.ambient

import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientIdleControllerTest {

    private fun keyEvent(keyCode: Int, action: Int, repeatCount: Int = 0): KeyEvent =
        mockk<KeyEvent> {
            every { this@mockk.keyCode } returns keyCode
            every { this@mockk.action } returns action
            every { this@mockk.repeatCount } returns repeatCount
        }

    @Test
    fun `any non-volume key dismisses ambient mode and swallows its up event`() {
        var now = 1_000L
        val controller = AmbientIdleController(uptimeMillisProvider = { now })
        controller.show()
        controller.allowVolumeKeysWhileShowing = true

        now = 2_000L
        assertTrue(controller.consumeKeyEvent(keyEvent(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN)))
        assertFalse(controller.isShowing.value)

        // Matching ACTION_UP for the swallowed key must still be consumed so underlying UI doesn't trigger.
        assertTrue(controller.consumeKeyEvent(keyEvent(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP)))
        assertFalse(controller.consumeKeyEvent(keyEvent(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN)))
    }

    @Test
    fun `volume keys pass through without dismissing when audible trailer playback is active`() {
        val controller = AmbientIdleController(uptimeMillisProvider = { 5_000L })
        controller.show()
        controller.allowVolumeKeysWhileShowing = true

        listOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE
        ).forEach { code ->
            assertFalse(controller.consumeKeyEvent(keyEvent(code, KeyEvent.ACTION_DOWN)))
            assertFalse(controller.consumeKeyEvent(keyEvent(code, KeyEvent.ACTION_UP)))
            assertTrue("Ambient mode must stay showing for volume key $code", controller.isShowing.value)
        }
    }

    @Test
    fun `volume keys still dismiss ambient mode when trailer audio is muted or backdrop mode is active`() {
        val controller = AmbientIdleController(uptimeMillisProvider = { 5_000L })
        controller.show()
        controller.allowVolumeKeysWhileShowing = false

        assertTrue(controller.consumeKeyEvent(keyEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN)))
        assertFalse(controller.isShowing.value)
    }
}
