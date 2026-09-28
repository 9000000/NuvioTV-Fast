package com.nuvio.tv.ui.screens.settings

import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIconsTest {

    private val rowIconsBySection: Map<PlaybackSection, Any> = mapOf(
        PlaybackSection.PLAYER to PlaybackIcons.Player,
        PlaybackSection.STREAM_SELECTION to PlaybackIcons.StreamSelection,
        PlaybackSection.UP_NEXT to PlaybackIcons.UpNext,
        PlaybackSection.SKIP_SEGMENTS to PlaybackIcons.SkipSegments,
        PlaybackSection.PLAYER_INTERFACE to PlaybackIcons.PlayerInterface,
        PlaybackSection.AUDIO to PlaybackIcons.Audio,
        PlaybackSection.SUBTITLES to PlaybackIcons.Subtitles,
        PlaybackSection.VIDEO to PlaybackIcons.Video,
        PlaybackSection.BUFFER_NETWORK to PlaybackIcons.BufferNetwork,
        PlaybackSection.P2P to PlaybackIcons.P2p
    )

    @Test
    fun `every playback section has a row icon set`() {
        assertEquals(PlaybackSection.entries.toSet(), rowIconsBySection.keys)
    }

    @Test
    fun `no icon repeats inside a playback section`() {
        rowIconsBySection.forEach { (section, icons) ->
            val names = iconsOf(icons).map { it.value.name }
            val repeated = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertTrue("$section repeats $repeated", repeated.isEmpty())
        }
    }

    @Test
    fun `a section header icon is not reused by its own rows`() {
        rowIconsBySection.forEach { (section, icons) ->
            val rowNames = iconsOf(icons).map { it.value.name }
            assertFalse("$section header icon is used by a row", section.icon.name in rowNames)
        }
    }

    @Test
    fun `section header icons are unique`() {
        assertEquals(PlaybackSection.entries.size, PlaybackSection.entries.map { it.icon.name }.distinct().size)
        assertEquals(LayoutSection.entries.size, LayoutSection.entries.map { it.icon.name }.distinct().size)
    }

    @Test
    fun `shared meanings keep the same icon`() {
        assertEquals(PlaybackIcons.Audio.secondaryLanguage.name, PlaybackIcons.Subtitles.secondaryLanguage.name)
        assertEquals("Filled.Extension", PlaybackIcons.StreamSelection.allowedAddons.name)
        assertEquals("Filled.Power", PlaybackIcons.StreamSelection.allowedPlugins.name)
        assertEquals(PlaybackIcons.Subtitles.forced.name, PlaybackIcons.Player.forwardSubtitles.name)
    }

    private fun iconsOf(holder: Any): Map<String, ImageVector> =
        holder.javaClass.declaredMethods
            .filter { it.parameterCount == 0 && it.returnType == ImageVector::class.java }
            .associate { it.name to it.invoke(holder) as ImageVector }
}
