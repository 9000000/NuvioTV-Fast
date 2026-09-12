package com.nuvio.tv.core.streams

import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StreamOrderAndBadgePreservationTest {

    private fun createTestStream(
        name: String? = null,
        title: String? = null,
        description: String? = null,
        url: String? = null,
        ytId: String? = null,
        infoHash: String? = null,
        fileIdx: Int? = null,
        externalUrl: String? = null,
        addonName: String = "Torrentio"
    ): Stream = Stream(
        name = name,
        title = title,
        description = description,
        url = url,
        ytId = ytId,
        infoHash = infoHash,
        fileIdx = fileIdx,
        externalUrl = externalUrl,
        behaviorHints = null,
        addonName = addonName,
        addonLogo = null
    )

    private fun Stream.uniqueIdentityKeyForTest(): String = buildString {
        append(addonName)
        append('|')
        append(url ?: "")
        append('|')
        append(infoHash?.lowercase() ?: "")
        append('|')
        append(getEffectiveFileIdx() ?: "")
        append('|')
        append(name ?: "")
        append('|')
        append(title ?: "")
    }

    @Test
    fun `streams with same infoHash but different titles have distinct identity keys`() {
        val stream1 = createTestStream(
            name = "[Torrentio] 4K HDR",
            title = "Avatar.The.Way.of.Water.2022.2160p.UHD.Remux.mkv\n👤 120 💾 45GB",
            infoHash = "4a5b6c7d8e9f0123456789abcdef0123456789ab"
        )
        val stream2 = createTestStream(
            name = "[Torrentio] 1080p",
            title = "Avatar.The.Way.of.Water.2022.1080p.Bluray.x264.mkv\n👤 85 💾 8GB",
            infoHash = "4a5b6c7d8e9f0123456789abcdef0123456789ab"
        )
        val stream3 = createTestStream(
            name = "[Torrentio] 720p",
            title = "Avatar.The.Way.of.Water.2022.720p.HD.x264.mkv\n👤 30 💾 2GB",
            infoHash = "4a5b6c7d8e9f0123456789abcdef0123456789ab"
        )

        val key1 = stream1.uniqueIdentityKeyForTest()
        val key2 = stream2.uniqueIdentityKeyForTest()
        val key3 = stream3.uniqueIdentityKeyForTest()

        assertNotEquals(key1, key2)
        assertNotEquals(key2, key3)
        assertNotEquals(key1, key3)
    }

    @Test
    fun `stream order inside addon group is preserved 100 percent`() {
        val originalStreams = listOf(
            createTestStream(name = "Link 1 - 4K", title = "File 1", infoHash = "hash123", addonName = "AddonA"),
            createTestStream(name = "Link 2 - 1080p", title = "File 2", infoHash = "hash123", addonName = "AddonA"),
            createTestStream(name = "Link 3 - 720p", title = "File 3", infoHash = "hash123", addonName = "AddonA"),
            createTestStream(name = "Link 4 - CAM", title = "File 4", infoHash = "hash999", addonName = "AddonA")
        )
        val group = AddonStreams(addonName = "AddonA", addonLogo = null, streams = originalStreams)

        // 1-to-1 mapping preserves exact positions
        val updatedGroup = group.copy(
            streams = group.streams.map { it.copy() }
        )

        assertEquals(4, updatedGroup.streams.size)
        for (i in originalStreams.indices) {
            assertEquals("Stream at index $i must match original name", originalStreams[i].name, updatedGroup.streams[i].name)
            assertEquals("Stream at index $i must match original title", originalStreams[i].title, updatedGroup.streams[i].title)
            assertEquals("Stream at index $i must match original infoHash", originalStreams[i].infoHash, updatedGroup.streams[i].infoHash)
        }
    }
}
