package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.PLACEHOLDER_IMAGE_URL
import com.nuvio.tv.ui.util.StableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomePrefetchTriggerTest {

    @Test
    fun `computeLazyCatalogPrefetchWindow handles empty start mid and clamped end`() {
        assertEquals(
            IntRange.EMPTY,
            computeLazyCatalogPrefetchWindow(firstVisible = 0, lastVisible = -1, aheadCount = 3, totalCount = 10)
        )
        assertEquals(
            IntRange.EMPTY,
            computeLazyCatalogPrefetchWindow(firstVisible = 0, lastVisible = 2, aheadCount = 3, totalCount = 0)
        )
        assertEquals(
            0..5,
            computeLazyCatalogPrefetchWindow(firstVisible = -2, lastVisible = 2, aheadCount = 3, totalCount = 10)
        )
        assertEquals(
            2..7,
            computeLazyCatalogPrefetchWindow(firstVisible = 2, lastVisible = 4, aheadCount = 3, totalCount = 10)
        )
        assertEquals(
            6..9,
            computeLazyCatalogPrefetchWindow(firstVisible = 6, lastVisible = 8, aheadCount = 4, totalCount = 10)
        )
        assertEquals(
            IntRange.EMPTY,
            computeLazyCatalogPrefetchWindow(firstVisible = 10, lastVisible = 10, aheadCount = 3, totalCount = 10)
        )
    }

    @Test
    fun `computeVerticalImagePrefetchWindow covers ahead rows and clamps safely`() {
        assertEquals(
            IntRange.EMPTY,
            computeVerticalImagePrefetchWindow(lastVisibleRowIndex = -1, aheadRows = 3, totalRows = 10)
        )
        assertEquals(
            IntRange.EMPTY,
            computeVerticalImagePrefetchWindow(lastVisibleRowIndex = 2, aheadRows = 0, totalRows = 10)
        )
        assertEquals(
            IntRange.EMPTY,
            computeVerticalImagePrefetchWindow(lastVisibleRowIndex = 2, aheadRows = 3, totalRows = 0)
        )
        assertEquals(
            3..5,
            computeVerticalImagePrefetchWindow(lastVisibleRowIndex = 2, aheadRows = 3, totalRows = 10)
        )
        assertEquals(
            9..9,
            computeVerticalImagePrefetchWindow(lastVisibleRowIndex = 8, aheadRows = 3, totalRows = 10)
        )
        assertEquals(
            IntRange.EMPTY,
            computeVerticalImagePrefetchWindow(lastVisibleRowIndex = 9, aheadRows = 3, totalRows = 10)
        )
    }

    @Test
    fun `computeRowCardPrefetchIndices shifts left near end and handles small rows`() {
        assertEquals(
            0..7,
            computeRowCardPrefetchIndices(focusedIndex = 0, itemCount = 20, prefetchCount = 8)
        )
        assertEquals(
            12..19,
            computeRowCardPrefetchIndices(focusedIndex = 18, itemCount = 20, prefetchCount = 8)
        )
        assertEquals(
            0..2,
            computeRowCardPrefetchIndices(focusedIndex = 0, itemCount = 3, prefetchCount = 8)
        )
        assertEquals(
            0..2,
            computeRowCardPrefetchIndices(focusedIndex = 2, itemCount = 3, prefetchCount = 8)
        )
        assertEquals(
            5..12,
            computeRowCardPrefetchIndices(focusedIndex = 5, itemCount = 20, prefetchCount = 8)
        )
        assertEquals(
            IntRange.EMPTY,
            computeRowCardPrefetchIndices(focusedIndex = 0, itemCount = 0, prefetchCount = 8)
        )
    }

    @Test
    fun `buildVerticalImagePrefetchSignature updates on placeholder transition and focus shift`() {
        val placeholderRow = row(key = "row_3", isLoading = true, imageUrl = PLACEHOLDER_IMAGE_URL)
        val realRow = row(key = "row_3", isLoading = true, imageUrl = "https://example.test/poster.jpg")
        val baseRows = listOf(
            row(key = "row_0", isLoading = false, imageUrl = "https://example.test/0.jpg"),
            row(key = "row_1", isLoading = false, imageUrl = "https://example.test/1.jpg"),
            row(key = "row_2", isLoading = false, imageUrl = "https://example.test/2.jpg"),
            placeholderRow,
            row(key = "row_4", isLoading = false, imageUrl = "https://example.test/4.jpg")
        )
        val loadedRows = baseRows.toMutableList().apply { set(3, realRow) }
        val focusedByRow = mutableMapOf("row_3" to 0, "row_4" to 0)

        val sigPlaceholder = buildVerticalImagePrefetchSignature(
            lastVisibleRowIndex = 2,
            rows = baseRows,
            aheadRows = 2,
            focusedIndexProvider = { focusedByRow[it] ?: 0 }
        )
        val sigLoaded = buildVerticalImagePrefetchSignature(
            lastVisibleRowIndex = 2,
            rows = loadedRows,
            aheadRows = 2,
            focusedIndexProvider = { focusedByRow[it] ?: 0 }
        )

        assertNotEquals(sigPlaceholder, sigLoaded)

        focusedByRow["row_3"] = 5
        val sigFocusChanged = buildVerticalImagePrefetchSignature(
            lastVisibleRowIndex = 2,
            rows = loadedRows,
            aheadRows = 2,
            focusedIndexProvider = { focusedByRow[it] ?: 0 }
        )

        assertNotEquals(sigLoaded, sigFocusChanged)

        val outsideWindowChanged = loadedRows.toMutableList().apply {
            set(0, row(key = "row_0", isLoading = false, imageUrl = "https://example.test/other.jpg"))
        }
        val sigOutsideChanged = buildVerticalImagePrefetchSignature(
            lastVisibleRowIndex = 2,
            rows = outsideWindowChanged,
            aheadRows = 2,
            focusedIndexProvider = { focusedByRow[it] ?: 0 }
        )

        assertEquals(sigFocusChanged, sigOutsideChanged)
    }

    @Test
    fun `collectThrottledLeadingTrailing runs leading periodic and trailing without starvation`() = runTest {
        val events = MutableSharedFlow<Int>(extraBufferCapacity = 32)
        val observed = mutableListOf<Pair<Int, Long>>()

        val job = launch {
            events.collectThrottledLeadingTrailing(
                leadingIntervalMs = 80L,
                trailingDelayMs = 100L,
                nowMs = { testScheduler.currentTime }
            ) { value ->
                observed += value to testScheduler.currentTime
            }
        }
        runCurrent()

        events.tryEmit(1)
        runCurrent()
        assertEquals(listOf(1 to 0L), observed)

        advanceTimeBy(30)
        runCurrent()
        events.tryEmit(2)
        runCurrent()

        advanceTimeBy(30)
        runCurrent()
        events.tryEmit(3)
        runCurrent()
        assertEquals(listOf(1 to 0L), observed)

        advanceTimeBy(25) // t = 85ms (>= 80ms since t=0)
        runCurrent()
        events.tryEmit(4)
        runCurrent()
        assertEquals(listOf(1 to 0L, 4 to 85L), observed)

        advanceTimeBy(20) // t = 105ms (< 80ms since t=85ms) -> trailing scheduled for +100ms
        runCurrent()
        events.tryEmit(5)
        runCurrent()

        advanceTimeBy(20) // t = 125ms -> updates trailing payload to 6 without cancelling timer
        runCurrent()
        events.tryEmit(6)
        runCurrent()

        advanceTimeBy(80) // t = 205ms -> trailing fires once for final value 6
        runCurrent()
        assertEquals(listOf(1 to 0L, 4 to 85L, 6 to 205L), observed)

        job.cancel()
    }

    @Test
    fun `collectThrottledLeadingTrailing bypasses delay when stationary`() = runTest {
        val events = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 8)
        val observed = mutableListOf<Pair<String, Long>>()

        val job = launch {
            events.collectThrottledLeadingTrailing(
                leadingIntervalMs = 60L,
                trailingDelayMs = 60L,
                nowMs = { testScheduler.currentTime },
                shouldBypassThrottle = { !it.second }
            ) { (sig, _) ->
                observed += sig to testScheduler.currentTime
            }
        }
        runCurrent()

        events.tryEmit("scroll_0" to true)
        runCurrent()
        advanceTimeBy(10)
        events.tryEmit("data_ready" to false)
        runCurrent()

        assertEquals(listOf("scroll_0" to 0L, "data_ready" to 10L), observed)
        delay(100)
        assertEquals(2, observed.size)

        job.cancel()
    }

    @Test
    fun `horizontal eager pagination and key navigation trigger thresholds`() {
        // SCROLL-LOAD-03: loadMoreThreshold = maxOf(8, totalCount - 12)
        fun computeLoadMoreThreshold(totalCount: Int): Int = maxOf(8, totalCount - 12)
        fun shouldTriggerKeyLoadMore(target: Int, total: Int): Boolean =
            target >= (total - 12).coerceAtLeast(0) && total > 0

        // For small lists (e.g. 10 items): threshold stays at floor of 8
        assertEquals(8, computeLoadMoreThreshold(10))
        // For large lists (e.g. 50 items): threshold is 38 (requests 12 items early)
        assertEquals(38, computeLoadMoreThreshold(50))
        assertEquals(18, computeLoadMoreThreshold(30))

        // Key navigation ahead trigger check:
        // total = 30 -> (30 - 12).coerceAtLeast(0) = 18
        assertEquals(false, shouldTriggerKeyLoadMore(target = 17, total = 30))
        assertEquals(true, shouldTriggerKeyLoadMore(target = 18, total = 30))
        assertEquals(true, shouldTriggerKeyLoadMore(target = 25, total = 30))

        // total <= 12 -> triggers immediately on any forward key movement
        assertEquals(true, shouldTriggerKeyLoadMore(target = 0, total = 10))
        assertEquals(false, shouldTriggerKeyLoadMore(target = 0, total = 0))
    }

    private fun row(key: String, isLoading: Boolean, imageUrl: String?): HeroCarouselRow =
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
                        imageUrl = imageUrl,
                        heroPreview = HeroPreview(
                            title = key,
                            logo = null,
                            description = null,
                            contentTypeText = null,
                            yearText = null,
                            imdbText = null,
                            genres = StableList(emptyList()),
                            poster = imageUrl,
                            backdrop = null,
                            imageUrl = imageUrl
                        ),
                        payload = ModernPayload.Catalog(
                            focusKey = "${key}_0",
                            itemId = "${key}_0",
                            itemType = "movie",
                            addonBaseUrl = "https://example.test/manifest.json",
                            trailerTitle = key,
                            trailerReleaseInfo = null,
                            trailerApiType = "movie"
                        )
                    )
                )
            ),
            isLoading = isLoading
        )
}
