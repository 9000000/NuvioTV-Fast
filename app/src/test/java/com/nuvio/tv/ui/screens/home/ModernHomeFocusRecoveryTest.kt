package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.lazy.LazyListItemInfo
import com.nuvio.tv.ui.util.StableList
import org.junit.Assert.assertEquals
import org.junit.Test

class ModernHomeFocusRecoveryTest {

    @Test
    fun `picks nearest surviving non-empty visible row favoring downward tie-break`() {
        val rows = listOf(
            nonEmptyRow("r0"),
            emptyRow("r1"),
            nonEmptyRow("r2"),
            nonEmptyRow("r3")
        )
        val visible = listOf(
            fakeVisibleItem(0, "r0"),
            fakeVisibleItem(1, "r1"),
            fakeVisibleItem(2, "r2")
        )

        assertEquals(2, findNearestNonEmptyRowIndex(rows, preferredIdx = 1, visibleItems = visible))
    }

    @Test
    fun `falls back backward when subsequent rows are removed or empty`() {
        val rows = listOf(
            nonEmptyRow("r0"),
            nonEmptyRow("r1"),
            emptyRow("r2")
        )

        assertEquals(1, findNearestNonEmptyRowIndex(rows, preferredIdx = 5))
    }

    @Test
    fun `returns -1 when all rows are empty or list is empty`() {
        assertEquals(-1, findNearestNonEmptyRowIndex(emptyList(), preferredIdx = 0))
        assertEquals(-1, findNearestNonEmptyRowIndex(listOf(emptyRow("r0"), emptyRow("r1")), preferredIdx = 0))
    }

    private fun nonEmptyRow(key: String): HeroCarouselRow =
        HeroCarouselRow(
            key = key,
            title = key,
            globalRowIndex = 0,
            items = StableList(
                listOf(
                    ModernCarouselItem(
                        key = "${key}_0",
                        title = key,
                        subtitle = null,
                        imageUrl = "https://example.test/poster.jpg",
                        heroPreview = HeroPreview(
                            title = key,
                            logo = null,
                            description = null,
                            contentTypeText = null,
                            yearText = null,
                            imdbText = null,
                            genres = StableList(emptyList()),
                            poster = null,
                            backdrop = null,
                            imageUrl = null
                        ),
                        payload = ModernPayload.Catalog(
                            focusKey = "${key}_0",
                            itemId = "${key}_id",
                            itemType = "movie",
                            addonBaseUrl = "",
                            trailerTitle = key,
                            trailerReleaseInfo = null,
                            trailerApiType = "movie"
                        )
                    )
                )
            )
        )

    private fun emptyRow(key: String): HeroCarouselRow =
        HeroCarouselRow(
            key = key,
            title = key,
            globalRowIndex = 0,
            items = StableList(emptyList())
        )

    private fun fakeVisibleItem(index: Int, key: String): LazyListItemInfo =
        object : LazyListItemInfo {
            override val index: Int = index
            override val key: Any = key
            override val offset: Int = 0
            override val size: Int = 200
        }
}
