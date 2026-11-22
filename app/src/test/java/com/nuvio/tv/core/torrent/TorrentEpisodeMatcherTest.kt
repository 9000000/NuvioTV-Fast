package com.nuvio.tv.core.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentEpisodeMatcherTest {

    @Test
    fun matchesMultiSeasonPacksWithSeasonFolders() {
        val files = listOf(
            TorrServerRemoteFile(id = 1, path = "Breaking Bad/Season 01/01. Pilot.mkv", length = 1_000_000_000L),
            TorrServerRemoteFile(id = 2, path = "Breaking Bad/Season 01/02. Cat's in the Bag.mkv", length = 1_000_000_000L),
            TorrServerRemoteFile(id = 10, path = "Breaking Bad/Season 02/01. Seven Thirty-Seven.mkv", length = 1_100_000_000L),
            TorrServerRemoteFile(id = 11, path = "Breaking Bad/Season 02/02. Grilled.mkv", length = 1_100_000_000L)
        )

        // Target Season 2 Episode 1 (requestedIdx = 1 which belongs to S01E01)
        val selectedId = TorrentEpisodeMatcher.selectBestMatchingFileId(
            files = files,
            requestedIdx = 1,
            targetSeason = 2,
            targetEpisode = 1
        )
        // Must reject requestedIdx = 1 because it's Season 1, and select file id 10 (Season 2 Episode 1)
        assertEquals(10, selectedId)

        // Target Season 1 Episode 2
        val selectedS1E2 = TorrentEpisodeMatcher.selectBestMatchingFileId(
            files = files,
            requestedIdx = null,
            targetSeason = 1,
            targetEpisode = 2
        )
        assertEquals(2, selectedS1E2)
    }

    @Test
    fun handlesMultiSeasonRangeRootFolder() {
        val files = listOf(
            TorrServerRemoteFile(id = 1, path = "Show S01-S05 Complete 1080p/Season 1/01.mkv", length = 500_000_000L),
            TorrServerRemoteFile(id = 2, path = "Show S01-S05 Complete 1080p/Season 2/01.mkv", length = 500_000_000L),
            TorrServerRemoteFile(id = 3, path = "Show S01-S05 Complete 1080p/Season 3/01.mkv", length = 500_000_000L)
        )

        // Looking for Season 3 Episode 1: "S01-S05" in root folder must NOT be mistaken for Season 1
        val selectedId = TorrentEpisodeMatcher.selectBestMatchingFileId(
            files = files,
            requestedIdx = 1,
            targetSeason = 3,
            targetEpisode = 1
        )
        assertEquals(3, selectedId)
    }

    @Test
    fun matchesShortSeasonFolderNames() {
        val files = listOf(
            TorrServerRemoteFile(id = 1, path = "The Wire/S1/01 - The Target.mkv", length = 800_000_000L),
            TorrServerRemoteFile(id = 2, path = "The Wire/S2/01 - Ebb Tide.mkv", length = 800_000_000L)
        )

        val selected = TorrentEpisodeMatcher.selectBestMatchingFileId(
            files = files,
            requestedIdx = null,
            targetSeason = 2,
            targetEpisode = 1
        )
        assertEquals(2, selected)
    }

    @Test
    fun matchesNxMMNotationInMultiSeason() {
        val files = listOf(
            TorrServerRemoteFile(id = 1, path = "Friends/Season 1/Friends - [01x05].mkv", length = 600_000_000L),
            TorrServerRemoteFile(id = 2, path = "Friends/Season 2/Friends - [02x05].mkv", length = 600_000_000L),
            TorrServerRemoteFile(id = 3, path = "Friends/Season 5/Friends - [05x05].mkv", length = 600_000_000L)
        )

        val selected = TorrentEpisodeMatcher.selectBestMatchingFileId(
            files = files,
            requestedIdx = null,
            targetSeason = 5,
            targetEpisode = 5
        )
        assertEquals(3, selected)
    }

    @Test
    fun matchesMultiEpisodeFiles() {
        val files = listOf(
            TorrServerRemoteFile(id = 1, path = "Show.S02E01-E02.mkv", length = 1_500_000_000L),
            TorrServerRemoteFile(id = 2, path = "Show.S02E03.mkv", length = 800_000_000L)
        )

        // Episode 2 is inside S02E01-E02
        val selectedEp2 = TorrentEpisodeMatcher.selectBestMatchingFileId(
            files = files,
            requestedIdx = null,
            targetSeason = 2,
            targetEpisode = 2
        )
        assertEquals(1, selectedEp2)

        // Episode 3 is file 2
        val selectedEp3 = TorrentEpisodeMatcher.selectBestMatchingFileId(
            files = files,
            requestedIdx = null,
            targetSeason = 2,
            targetEpisode = 3
        )
        assertEquals(2, selectedEp3)
    }

    @Test
    fun ignoresExtrasAndSamples() {
        val files = listOf(
            TorrServerRemoteFile(id = 1, path = "Show/Season 1/Sample/sample.mkv", length = 50_000_000L),
            TorrServerRemoteFile(id = 2, path = "Show/Season 1/Extras/Interview.mkv", length = 200_000_000L),
            TorrServerRemoteFile(id = 3, path = "Show/Season 1/01. Pilot.mkv", length = 900_000_000L)
        )

        val selected = TorrentEpisodeMatcher.selectBestMatchingFileId(
            files = files,
            requestedIdx = null,
            targetSeason = 1,
            targetEpisode = 1
        )
        assertEquals(3, selected)
    }

    @Test
    fun matchesEpisodeMethodRespectsFolderStructure() {
        // Season 2 Episode 1
        assertTrue(TorrentEpisodeMatcher.matchesEpisode("Show/Season 02/01.mkv", 2, 1))
        assertFalse(TorrentEpisodeMatcher.matchesEpisode("Show/Season 01/01.mkv", 2, 1))
        assertFalse(TorrentEpisodeMatcher.matchesEpisode("Show/Season 02/02.mkv", 2, 1))

        // Season 1 Episode 5
        assertTrue(TorrentEpisodeMatcher.matchesEpisode("Show/Season 1/Episode 05.mkv", 1, 5))
        assertFalse(TorrentEpisodeMatcher.matchesEpisode("Show/Season 2/Episode 05.mkv", 1, 5))
    }
}
