package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.streams.StreamBadgeFilter
import com.nuvio.tv.core.streams.StreamBadgeImport
import com.nuvio.tv.core.streams.StreamBadgeMatcher
import com.nuvio.tv.core.streams.StreamBadgeRules
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMatchTextTest {
    private val filters = StreamBadgeMatcher.compile(
        StreamBadgeRules(
            imports = listOf(
                StreamBadgeImport(
                    sourceUrl = "https://example.com/badges.json",
                    filters = listOf(
                        filter("4K", "(?i)\\b(2160p|4k)\\b"),
                        filter("WebDL", "(?i)\\bweb[-. ]?dl\\b"),
                        filter("Vision", "(?i)\\b(dv|dovi|dolby[ .]?vision)\\b"),
                        filter("HDR", "(?i)\\bhdr(10)?\\b"),
                        filter("10bit", "(?i)\\b10[ -]?bit\\b"),
                        filter("HEVC", "(?i)\\b(hevc|[xh][ .]?265)\\b"),
                        filter("Atmos", "(?i)\\batmos\\b"),
                        filter("English", "(?i)\\b(eng|english)\\b")
                    )
                )
            )
        )
    )

    private fun filter(name: String, pattern: String) =
        StreamBadgeFilter(name = name, pattern = pattern, imageURL = "https://example.com/$name.png")

    private fun matchedNames(info: PlayerMediaInfo, languages: List<String> = emptyList()): List<String> =
        StreamBadgeMatcher.matchedBadges(listOf(playbackMatchText(info, languages)), filters).map { it.name }

    @Test
    fun `matches what is playing`() {
        val info = PlayerMediaInfo(
            videoCodec = "HEVC",
            videoWidth = 3840,
            videoHeight = 1600,
            audioCodec = "E-AC-3-JOC",
            audioChannels = 6,
            hdr = listOf("DV", "HDR10"),
            bitDepth = 10
        )
        assertEquals(
            listOf("4K", "Vision", "HDR", "10bit", "HEVC", "Atmos", "English"),
            matchedNames(info, listOf("eng"))
        )
    }

    @Test
    fun `skips badges the playback does not have`() {
        val info = PlayerMediaInfo(videoCodec = "AVC", videoWidth = 1920, videoHeight = 1080, audioCodec = "AAC", audioChannels = 2)
        assertEquals(emptyList<String>(), matchedNames(info))
    }

    @Test
    fun `reads atmos from mpv audio profile`() {
        val info = PlayerMediaInfo(audioCodec = "TrueHD", audioProfiles = listOf("Dolby TrueHD + Dolby Atmos"))
        assertEquals(listOf("Atmos"), matchedNames(info))
    }

    @Test
    fun `uses the pack chosen for the player`() {
        val streamPack = StreamBadgeImport(
            sourceUrl = "https://example.com/stream.json",
            filters = listOf(filter("Stream4K", "\u2063\u200b\u2063"))
        )
        val playerPack = StreamBadgeImport(
            sourceUrl = "https://example.com/player.json",
            filters = listOf(filter("Player4K", "(?i)\\b2160p\\b"))
        )
        val rules = StreamBadgeRules(imports = listOf(streamPack, playerPack)).normalized()
        val text = playbackMatchText(PlayerMediaInfo(videoWidth = 3840, videoHeight = 2160), emptyList())
        fun namesFor(source: String) =
            StreamBadgeMatcher.matchedBadges(listOf(text), StreamBadgeMatcher.compile(rules.forPlayer(source))).map { it.name }

        assertEquals(emptyList<String>(), namesFor(""))
        assertEquals(listOf("Player4K"), namesFor("https://example.com/player.json"))
        assertEquals(emptyList<String>(), namesFor("https://example.com/removed.json"))
    }

    @Test
    fun `shows nothing before playback info loads`() {
        assertEquals(emptyList<String>(), matchedNames(PlayerMediaInfo()))
    }

    @Test
    fun `DTS-HD MA not triggered by German label`() {
        // Real ExoPlayer input: DTS-HD HRA track with German label.
        // Before fix: "German" contains "ma", producing a false DTS-HD MA profile.
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_DTS_HD)
            .setLabel("German DTS-HD HRA 7.1")
            .build()
        val profiles = exoAudioProfiles(format)
        assertFalse("DTS-HD MA should not appear for German DTS-HD HRA", profiles.contains("DTS-HD MA"))
        assertTrue("DTS-HD should appear", profiles.contains("DTS-HD"))
    }

    @Test
    fun `DTS-HD Master Audio label produces MA profile`() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_DTS_HD)
            .setLabel("English DTS-HD Master Audio 7.1")
            .build()
        val profiles = exoAudioProfiles(format)
        assertTrue("DTS-HD MA should appear for Master Audio label", profiles.contains("DTS-HD MA"))
    }

    @Test
    fun `DTS-HD MA and DTS-X both appear`() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_DTS_HD)
            .setLabel("DTS-HD MA DTS:X 7.1")
            .build()
        val profiles = exoAudioProfiles(format)
        assertTrue("DTS-HD MA should appear", profiles.contains("DTS-HD MA"))
        assertTrue("DTS:X should appear", profiles.contains("DTS:X"))
    }

    @Test
    fun `pcm_s24le maps to PCM via mpv`() {
        val info = mpvMediaInfo { name ->
            when (name) {
                "current-tracks/audio/codec" -> "pcm_s24le"
                else -> null
            }
        }
        assertEquals("PCM", info.audioCodec)
    }

    @Test
    fun `group exclusion skips filename language when decoder matches`() {
        val langGroup = "Language"
        val sourceGroup = "Source"
        val playerFilters = StreamBadgeMatcher.compile(
            StreamBadgeRules(
                imports = listOf(
                    StreamBadgeImport(
                        sourceUrl = "https://example.com/badges.json",
                        filters = listOf(
                            StreamBadgeFilter(name = "English", pattern = "(?i)\\benglish\\b", imageURL = "eng.png", groupId = langGroup),
                            StreamBadgeFilter(name = "Polish", pattern = "(?i)\\bpol\\b", imageURL = "pol.png", groupId = langGroup),
                            StreamBadgeFilter(name = "WebDL", pattern = "(?i)\\bweb[-.]?dl\\b", imageURL = "webdl.png", groupId = sourceGroup)
                        )
                    )
                )
            )
        )
        val playback = "DD+ 5.1 English"
        val filename = listOf("Movie.POL.ENG.WebDL.mkv")
        val badges = StreamBadgeMatcher.matchedPlayerBadges(playback, filename, playerFilters).map { it.name }
        assertTrue("English should appear (decoder match)", "English" in badges)
        assertFalse("Polish should NOT appear (group covered by decoder)", "Polish" in badges)
        assertTrue("WebDL should appear (different group)", "WebDL" in badges)
    }
}
