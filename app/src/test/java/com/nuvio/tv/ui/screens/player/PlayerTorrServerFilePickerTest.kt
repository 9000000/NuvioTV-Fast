package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.torrent.TorrServerAddonConfigData
import com.nuvio.tv.core.torrent.TorrServerRemoteFile
import com.nuvio.tv.core.torrent.TorrServerStreamProvider
import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTorrServerFilePickerTest {

    private fun createStream(
        name: String? = null,
        title: String? = null,
        url: String? = null,
        infoHash: String? = null,
        addonName: String = "test-addon"
    ): Stream = Stream(
        name = name,
        title = title,
        description = null,
        url = url,
        ytId = null,
        infoHash = infoHash,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = addonName,
        addonLogo = null
    )

    @Test
    fun playerUiStateInitializesWithTorrentFilePickerDefaults() {
        val state = PlayerUiState()

        assertFalse(state.showTorrentFilePicker)
        assertFalse(state.torrentFilePickerLoading)
        assertNull(state.torrentFilePickerError)
        assertEquals("", state.torrentFilePickerTitle)
        assertTrue(state.torrentFilePickerFiles.isEmpty())
        assertNull(state.torrentFilePickerPendingStream)
        assertNull(state.torrentFilePickerPendingHash)
    }

    @Test
    fun playerUiStateUpdatesWithTorrentFilePickerValues() {
        val sampleStream = createStream(
            name = "Test Stream",
            infoHash = "0123456789abcdef0123456789abcdef01234567"
        )
        val sampleFiles = listOf(
            TorrServerRemoteFile(id = 1, path = "Movie/film.mkv", length = 1_000_000_000L),
            TorrServerRemoteFile(id = 2, path = "Movie/sample.mp4", length = 20_000_000L)
        )

        val updated = PlayerUiState().copy(
            showTorrentFilePicker = true,
            torrentFilePickerLoading = false,
            torrentFilePickerError = null,
            torrentFilePickerTitle = "Test Movie",
            torrentFilePickerFiles = sampleFiles,
            torrentFilePickerPendingStream = sampleStream,
            torrentFilePickerPendingHash = sampleStream.infoHash
        )

        assertTrue(updated.showTorrentFilePicker)
        assertEquals(2, updated.torrentFilePickerFiles.size)
        assertEquals("Movie/film.mkv", updated.torrentFilePickerFiles.first().path)
        assertEquals(sampleStream, updated.torrentFilePickerPendingStream)
        assertEquals("0123456789abcdef0123456789abcdef01234567", updated.torrentFilePickerPendingHash)
    }

    @Test
    fun playerEventCoversTorrentFilePickerEvents() {
        val dismissEvent: PlayerEvent = PlayerEvent.OnDismissTorrentFilePicker
        val selectEvent: PlayerEvent = PlayerEvent.OnTorrentFileSelected(fileId = 42)

        assertTrue(dismissEvent is PlayerEvent.OnDismissTorrentFilePicker)
        assertTrue(selectEvent is PlayerEvent.OnTorrentFileSelected)
        assertEquals(42, (selectEvent as PlayerEvent.OnTorrentFileSelected).fileId)
    }

    @Test
    fun torrServerStreamRecognitionRule() {
        fun isTorrServerStream(
            stream: Stream,
            torrServerConfig: TorrServerAddonConfigData
        ): Boolean {
            val isExplicitTorrServer = stream.addonName == TorrServerStreamProvider.PROVIDER_NAME
            return isExplicitTorrServer || (torrServerConfig.enabled && stream.isTorrent())
        }

        val torrentStream = createStream(
            name = "Torrent 1080p",
            infoHash = "1234567890123456789012345678901234567890"
        )
        val httpStream = createStream(
            name = "Direct HTTP",
            url = "https://example.com/video.mp4"
        )
        val explicitTorrServerStream = createStream(
            name = "TorrServer Provider",
            url = "http://127.0.0.1:8090/stream?link=...",
            addonName = TorrServerStreamProvider.PROVIDER_NAME
        )

        // Case 1: TorrServer enabled, torrent stream -> true
        assertTrue(isTorrServerStream(torrentStream, TorrServerAddonConfigData(enabled = true)))

        // Case 2: TorrServer disabled, torrent stream -> false
        assertFalse(isTorrServerStream(torrentStream, TorrServerAddonConfigData(enabled = false)))

        // Case 3: TorrServer enabled, direct HTTP stream -> false
        assertFalse(isTorrServerStream(httpStream, TorrServerAddonConfigData(enabled = true)))

        // Case 4: Explicit TorrServer addon provider -> always true regardless of config.enabled
        assertTrue(isTorrServerStream(explicitTorrServerStream, TorrServerAddonConfigData(enabled = false)))
        assertTrue(isTorrServerStream(explicitTorrServerStream, TorrServerAddonConfigData(enabled = true)))
    }

    @Test
    fun movieFileSortingRule_placesLargestVideoFileFirst() {
        val videoExtensions = setOf("mkv", "mp4", "avi", "webm", "ts", "m4v", "mov", "wmv", "flv")

        fun sortFiles(
            files: List<TorrServerRemoteFile>,
            targetSeason: Int?,
            targetEpisode: Int?,
            contentType: String?
        ): List<TorrServerRemoteFile> {
            val isMovie = contentType?.equals("movie", ignoreCase = true) == true ||
                (targetSeason == null && targetEpisode == null &&
                 contentType?.equals("series", ignoreCase = true) != true &&
                 contentType?.equals("tv", ignoreCase = true) != true)

            return if (isMovie) {
                files.sortedWith(
                    compareByDescending<TorrServerRemoteFile> { file ->
                        val ext = file.path.substringAfterLast('.', "").lowercase()
                        ext in videoExtensions
                    }.thenByDescending { it.length }
                    .thenBy { it.path }
                )
            } else {
                files.sortedWith(
                    compareByDescending<TorrServerRemoteFile> { file ->
                        val ext = file.path.substringAfterLast('.', "").lowercase()
                        ext in videoExtensions
                    }.thenBy { it.path }
                )
            }
        }

        val movieFiles = listOf(
            TorrServerRemoteFile(id = 1, path = "Sample/sample.mp4", length = 50_000_000L),
            TorrServerRemoteFile(id = 2, path = "subs.srt", length = 100_000L),
            TorrServerRemoteFile(id = 3, path = "Movie.2024.1080p.mkv", length = 12_000_000_000L),
            TorrServerRemoteFile(id = 4, path = "Movie.2024.720p.mp4", length = 4_000_000_000L),
            TorrServerRemoteFile(id = 5, path = "readme.txt", length = 5_000L)
        )

        // Phim lẻ (contentType = "movie")
        val sortedMovie = sortFiles(movieFiles, targetSeason = null, targetEpisode = null, contentType = "movie")
        assertEquals(3, sortedMovie[0].id) // 12 GB video file must be first
        assertEquals(4, sortedMovie[1].id) // 4 GB video file must be second
        assertEquals(1, sortedMovie[2].id) // 50 MB sample must be third
        assertFalse(sortedMovie[3].path.substringAfterLast('.') in videoExtensions)

        // Phim lẻ (targetSeason = null, targetEpisode = null, contentType = null)
        val sortedDefaultMovie = sortFiles(movieFiles, targetSeason = null, targetEpisode = null, contentType = null)
        assertEquals(3, sortedDefaultMovie[0].id)
        assertEquals(4, sortedDefaultMovie[1].id)
        assertEquals(1, sortedDefaultMovie[2].id)

        // Phim bộ (targetEpisode != null) -> sắp xếp theo tên path để giữ thứ tự tập
        val seriesFiles = listOf(
            TorrServerRemoteFile(id = 1, path = "Show.S01E02.mkv", length = 1_200_000_000L),
            TorrServerRemoteFile(id = 2, path = "Show.S01E01.mkv", length = 1_100_000_000L),
            TorrServerRemoteFile(id = 3, path = "Show.S01E03.mkv", length = 1_300_000_000L)
        )
        val sortedSeries = sortFiles(seriesFiles, targetSeason = 1, targetEpisode = 1, contentType = "series")
        assertEquals("Show.S01E01.mkv", sortedSeries[0].path)
        assertEquals("Show.S01E02.mkv", sortedSeries[1].path)
        assertEquals("Show.S01E03.mkv", sortedSeries[2].path)
    }
}
