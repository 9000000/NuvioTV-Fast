package com.nuvio.tv.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentFilePickerMatcherTest {

    @Test
    fun matchesAnimeEpisodePatternsForOnePiece() {
        // One Piece Episode 1050
        val matcher1050 = buildEpisodeMatcher(season = 1, episode = 1050)

        // Common real-world release formats
        assertTrue(matcher1050("[SubsPlease] One Piece - 1050 (1080p) [2A65F9C1].mkv"))
        assertTrue(matcher1050("[Erai-raws] One Piece - 1050 [1080p][Multiple Subtitle].mkv"))
        assertTrue(matcher1050("One Piece - 1050.mkv"))
        assertTrue(matcher1050("One Piece 1050 [1080p].mp4"))
        assertTrue(matcher1050("One_Piece_-_1050_[1080p].mkv"))
        assertTrue(matcher1050("One.Piece.1050.1080p.mkv"))
        assertTrue(matcher1050("[Judas] One Piece - 1050.mkv"))
        assertTrue(matcher1050("One Piece 1050v2.mkv"))
        assertTrue(matcher1050("One.Piece.S01E1050.1080p.mkv"))
        assertTrue(matcher1050("One Piece E1050.mkv"))
        assertTrue(matcher1050("One Piece EP1050.mkv"))
        assertTrue(matcher1050("One Piece Episode 1050.mkv"))

        // Should NOT match other episodes
        assertFalse(matcher1050("[SubsPlease] One Piece - 1049 (1080p).mkv"))
        assertFalse(matcher1050("[SubsPlease] One Piece - 1051 (1080p).mkv"))
        assertFalse(matcher1050("[SubsPlease] One Piece - 050 (1080p).mkv"))
    }

    @Test
    fun matchesLeadingZeroEpisodesForLongAnime() {
        // Episode 500 with leading zeros in batch torrents (0500)
        val matcher500 = buildEpisodeMatcher(season = 1, episode = 500)

        assertTrue(matcher500("One Piece - 0500 [720p].mkv"))
        assertTrue(matcher500("One Piece - 500 (1080p).mkv"))
        assertTrue(matcher500("[HorribleSubs] One Piece - 500 [720p].mkv"))
        assertFalse(matcher500("One Piece - 0501 [720p].mkv"))
    }

    @Test
    fun doesNotFalsePositiveOnResolutionOrYear() {
        // Episode 24 should not match year 2024
        val matcher24 = buildEpisodeMatcher(season = 1, episode = 24)

        assertTrue(matcher24("One Piece - 024 [720p].mkv"))
        assertTrue(matcher24("One Piece - 24.mkv"))
        assertFalse(matcher24("One Piece Movie 2024 [1080p].mkv"))

        // Episode 1080 should not trigger on resolution 1080p of another episode
        val matcher25 = buildEpisodeMatcher(season = 1, episode = 25)
        assertFalse(matcher25("One Piece - 1080 (1080p).mkv"))
    }

    @Test
    fun respectsDifferentSeasonWhenExplicitlySpecified() {
        val matcherS1E5 = buildEpisodeMatcher(season = 1, episode = 5)

        assertTrue(matcherS1E5("Show.S01E05.mkv"))
        assertTrue(matcherS1E5("Show - 05.mkv"))
        assertFalse(matcherS1E5("Show.S02E05.mkv"))
    }
}
