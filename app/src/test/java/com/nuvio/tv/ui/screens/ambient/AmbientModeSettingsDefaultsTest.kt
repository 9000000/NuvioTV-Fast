package com.nuvio.tv.ui.screens.ambient

import com.nuvio.tv.domain.model.AmbientDisplayMode
import com.nuvio.tv.domain.model.AmbientModeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientModeSettingsDefaultsTest {

    @Test
    fun `ambient mode defaults to enabled trailer mode with audio on and 1 minute timeout`() {
        val defaults = AmbientModeSettings()

        assertTrue(defaults.enabled)
        assertEquals(AmbientDisplayMode.TRAILER, defaults.displayMode)
        assertTrue(defaults.trailerAudioEnabled)
        assertEquals(1, defaults.timeoutMinutes)
        assertTrue(AmbientModeSettings.TIMEOUT_OPTIONS_MINUTES.contains(defaults.timeoutMinutes))
    }

    @Test
    fun `display mode storage parser defaults to TRAILER when unset or unrecognized`() {
        assertEquals(AmbientDisplayMode.TRAILER, AmbientDisplayMode.fromStorage(null))
        assertEquals(AmbientDisplayMode.TRAILER, AmbientDisplayMode.fromStorage(""))
        assertEquals(AmbientDisplayMode.TRAILER, AmbientDisplayMode.fromStorage("unknown"))
        assertEquals(AmbientDisplayMode.BACKDROP, AmbientDisplayMode.fromStorage("backdrop"))
        assertEquals(AmbientDisplayMode.TRAILER, AmbientDisplayMode.fromStorage("trailer"))
    }
}
