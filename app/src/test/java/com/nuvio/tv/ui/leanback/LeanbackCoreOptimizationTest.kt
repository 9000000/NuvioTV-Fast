package com.nuvio.tv.ui.leanback

import android.app.ActivityManager
import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.ui.unit.dp
import coil3.Extras
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.nuvio.tv.core.image.CustomPosterFallbackInterceptor
import com.nuvio.tv.core.image.HomeImageLoadingProfile
import com.nuvio.tv.domain.model.PLACEHOLDER_IMAGE_URL
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.adapter.LeanbackUpdatePlan
import com.nuvio.tv.ui.leanback.adapter.computeLeanbackUpdatePlan
import com.nuvio.tv.ui.leanback.container.isVerticalGridLastRow
import com.nuvio.tv.ui.leanback.core.LeanbackArtworkLoader
import com.nuvio.tv.ui.leanback.core.LeanbackColorEngine
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.screens.home.HeroPreview
import com.nuvio.tv.ui.screens.home.LeanbackRowAdapter
import com.nuvio.tv.ui.screens.home.ModernCarouselItem
import com.nuvio.tv.ui.screens.home.ModernPayload
import com.nuvio.tv.ui.util.StableList
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LeanbackCoreOptimizationTest {

    @Test
    fun `color engine returns fallback color for dark grayscale or transparent pixels`() {
        val fallback = 0xFF38BDF8.toInt()
        val darkAndGrayPixels = intArrayOf(
            0x00FF0000, // transparent red
            0xFF080808.toInt(), // near black
            0xFF808080.toInt(), // pure gray (0 saturation)
            0xFFFFFFFF.toInt()  // pure white (0 saturation)
        )

        val result = LeanbackColorEngine.computeDominantNeonColorFromPixels(
            pixels = darkAndGrayPixels,
            count = darkAndGrayPixels.size,
            fallbackColor = fallback
        )

        assertEquals(fallback, result)
    }

    @Test
    fun `color engine prioritizes vibrant chromatic hue over dark background and boosts value`() {
        val fallback = 0xFF38BDF8.toInt()
        // Mostly dark background with vibrant crimson-red accent pixels (#CC1828)
        val pixels = IntArray(40) { idx ->
            if (idx < 32) 0xFF101014.toInt() else 0xFFCC1828.toInt()
        }

        val extracted = LeanbackColorEngine.computeDominantNeonColorFromPixels(
            pixels = pixels,
            count = pixels.size,
            fallbackColor = fallback
        )

        val r = (extracted ushr 16) and 0xFF
        val g = (extracted ushr 8) and 0xFF
        val b = extracted and 0xFF
        // Boosted to V = 1.0 -> red channel reaches 255 and dominates green/blue
        assertEquals(255, r)
        assertTrue("Expected red channel to strongly dominate green ($g) and blue ($b)", r > g * 3 && r > b * 2)
    }

    @Test
    fun `color engine coalesces concurrent extractions for the same key into single work execution`() {
        val key = "test_coalesce_poster_key"
        val fallback = 0xFF38BDF8.toInt()
        val expectedColor = 0xFFFF2244.toInt()
        val workCount = AtomicInteger(0)
        val startGate = CountDownLatch(1)
        val finishGate = CountDownLatch(3)
        val results = java.util.Collections.synchronizedList(mutableListOf<Int>())

        repeat(3) {
            LeanbackColorEngine.runCoalescedExtractionForTest(
                key = key,
                compute = {
                    startGate.await(2, TimeUnit.SECONDS)
                    workCount.incrementAndGet()
                    expectedColor
                },
                dispatch = { runnable -> runnable.run() },
                onExtracted = { color ->
                    results.add(color)
                    finishGate.countDown()
                },
                fallbackColor = fallback
            )
        }

        startGate.countDown()
        assertTrue(finishGate.await(3, TimeUnit.SECONDS))
        assertEquals("Should execute pixel extraction only once for duplicate in-flight keys", 1, workCount.get())
        assertEquals(listOf(expectedColor, expectedColor, expectedColor), results.toList())
    }

    @Test
    fun `artwork loader normalizes fallback url and matches custom poster memory cache keys`() {
        val customUrl = "https://api.ratingposterdb.com/key/imdb/poster-default/tt1234567.jpg"
        val rawTmdbUrl = "https://image.tmdb.org/t/p/original/abc.jpg"

        assertNull(LeanbackArtworkLoader.resolveNormalizedFallbackUrl(customUrl, null))
        assertNull(LeanbackArtworkLoader.resolveNormalizedFallbackUrl(customUrl, "   "))
        assertNull(LeanbackArtworkLoader.resolveNormalizedFallbackUrl(customUrl, customUrl))
        assertEquals(rawTmdbUrl, LeanbackArtworkLoader.resolveNormalizedFallbackUrl(customUrl, rawTmdbUrl))

        val baseKey = LeanbackArtworkLoader.buildBaseMemoryKey(rawTmdbUrl, 240, 360, blurred = false)
        assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg_240x360", baseKey)

        val blurredKey = LeanbackArtworkLoader.buildBaseMemoryKey(rawTmdbUrl, 400, 225, blurred = true)
        assertEquals("https://image.tmdb.org/t/p/w780/abc.jpg_400x225_blur", blurredKey)

        val primaryKey = LeanbackArtworkLoader.buildPrimaryMemoryCacheKey(baseKey, rawTmdbUrl)
        assertEquals(
            MemoryCache.Key(baseKey, mapOf(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to rawTmdbUrl)),
            primaryKey
        )
    }

    @Test
    fun `update planner uses O(N) in-place item change instead of full reset for lists larger than 300 items`() {
        data class DummyCard(val id: String, val watched: Boolean)

        val oldList = (0 until 500).map { DummyCard("item_$it", watched = false) }
        val newList = oldList.mapIndexed { idx, card ->
            if (idx == 42 || idx == 415) card.copy(watched = true) else card
        }

        val plan = computeLeanbackUpdatePlan(
            oldItems = oldList,
            newItems = newList,
            keyOf = { it.id },
            visualsMatch = { a, b -> a == b }
        )

        assertTrue("Expected InPlaceChanges for 500-item list with same keys, got $plan", plan is LeanbackUpdatePlan.InPlaceChanges)
        assertEquals(listOf(42, 415), (plan as LeanbackUpdatePlan.InPlaceChanges).changedPositions)
    }

    @Test
    fun `update planner detects append-only with prefix visual updates`() {
        data class DummyCard(val id: String, val progress: Int)

        val oldList = listOf(DummyCard("a", 10), DummyCard("b", 20))
        val newList = listOf(DummyCard("a", 55), DummyCard("b", 20), DummyCard("c", 0), DummyCard("d", 0))

        val plan = computeLeanbackUpdatePlan(
            oldItems = oldList,
            newItems = newList,
            keyOf = { it.id },
            visualsMatch = { a, b -> a == b }
        )

        assertEquals(
            LeanbackUpdatePlan.AppendAndChange(
                insertStart = 2,
                insertCount = 2,
                changedPrefixPositions = listOf(0)
            ),
            plan
        )
    }

    @Test
    fun `artwork loader prefetch ignores placeholder empty and blank urls without enqueue`() {
        val context = mockk<Context>(relaxed = true)
        val imageLoader = mockk<ImageLoader>(relaxed = true)
        val tracker = LinkedHashSet<String>()

        LeanbackArtworkLoader.prefetch(
            context = context,
            imageLoader = imageLoader,
            imageUrl = PLACEHOLDER_IMAGE_URL,
            widthPx = 240,
            heightPx = 360,
            prefetchedKeysTracker = tracker
        )
        LeanbackArtworkLoader.prefetch(
            context = context,
            imageLoader = imageLoader,
            imageUrl = "  $PLACEHOLDER_IMAGE_URL  ",
            widthPx = 240,
            heightPx = 360,
            prefetchedKeysTracker = tracker
        )
        LeanbackArtworkLoader.prefetch(
            context = context,
            imageLoader = imageLoader,
            imageUrl = "   ",
            widthPx = 240,
            heightPx = 360,
            prefetchedKeysTracker = tracker
        )
        LeanbackArtworkLoader.prefetch(
            context = context,
            imageLoader = imageLoader,
            imageUrl = null,
            widthPx = 240,
            heightPx = 360,
            prefetchedKeysTracker = tracker
        )

        assertTrue("Placeholder or blank URLs must not populate prefetchedKeysTracker", tracker.isEmpty())
        verify(exactly = 0) { imageLoader.enqueue(any()) }
        assertNull(
            LeanbackArtworkLoader.resolveNormalizedFallbackUrl(
                imageUrl = "https://example.com/poster.jpg",
                fallbackUrl = PLACEHOLDER_IMAGE_URL
            )
        )
    }

    @Test
    fun `artwork loader loadIntoImageView ignores placeholder empty without touching imageView or SingletonImageLoader`() {
        val imageView = mockk<ImageView>()
        val imageLoader = mockk<ImageLoader>(relaxed = true)
        var callbackInvoked = false

        mockkObject(SingletonImageLoader)
        try {
            every { SingletonImageLoader.get(any()) } returns imageLoader

            val disposable = LeanbackArtworkLoader.loadIntoImageView(
                imageView = imageView,
                imageUrl = PLACEHOLDER_IMAGE_URL,
                fallbackUrl = "https://example.com/fallback.jpg",
                widthPx = 240,
                heightPx = 360,
                isStillBound = { true },
                onCacheHit = { callbackInvoked = true },
                onAsyncSuccess = { callbackInvoked = true },
                onAsyncError = { callbackInvoked = true }
            )

            assertNull(disposable)
            assertTrue(!callbackInvoked)
            verify { imageView wasNot Called }
            verify(exactly = 0) { SingletonImageLoader.get(any()) }
            verify(exactly = 0) { imageLoader.enqueue(any()) }
        } finally {
            unmockkObject(SingletonImageLoader)
        }
    }

    @Test
    fun `computeHorizontalPrefetchIndices covers start middle end and empty or single item rows`() {
        // Start of row with 6 visible cards (0..5) -> prefetches offscreen right edge 6..11
        assertEquals(
            listOf(6, 7, 8, 9, 10, 11),
            LeanbackArtworkLoader.computeHorizontalPrefetchIndices(
                focusedPosition = 0,
                firstVisiblePosition = 0,
                lastVisiblePosition = 5,
                itemCount = 20
            )
        )

        // Middle of row -> prioritizes forward edge beyond lastVisible, then nearest behind firstVisible
        assertEquals(
            listOf(11, 12, 13, 14, 15, 16, 4, 3),
            LeanbackArtworkLoader.computeHorizontalPrefetchIndices(
                focusedPosition = 8,
                firstVisiblePosition = 5,
                lastVisiblePosition = 10,
                itemCount = 20
            )
        )

        // End of row -> clamps right edge cleanly and still prefetches nearest left offscreen items
        assertEquals(
            listOf(13, 12),
            LeanbackArtworkLoader.computeHorizontalPrefetchIndices(
                focusedPosition = 19,
                firstVisiblePosition = 14,
                lastVisiblePosition = 19,
                itemCount = 20
            )
        )

        // Unknown visible bounds (< 0) -> falls back around focusedPosition
        assertEquals(
            listOf(5, 6, 7, 8, 9, 10, 3, 2),
            LeanbackArtworkLoader.computeHorizontalPrefetchIndices(
                focusedPosition = 4,
                firstVisiblePosition = -1,
                lastVisiblePosition = -1,
                itemCount = 20
            )
        )

        // Near end -> distinct valid indices only
        val nearEnd = LeanbackArtworkLoader.computeHorizontalPrefetchIndices(
            focusedPosition = 6,
            firstVisiblePosition = 2,
            lastVisiblePosition = 7,
            itemCount = 10
        )
        assertEquals(listOf(8, 9, 1, 0), nearEnd)
        assertEquals(nearEnd.distinct(), nearEnd)
        assertTrue(nearEnd.all { it in 0 until 10 })

        // Single item and empty list -> emptyList()
        assertEquals(
            emptyList<Int>(),
            LeanbackArtworkLoader.computeHorizontalPrefetchIndices(
                focusedPosition = 0,
                firstVisiblePosition = 0,
                lastVisiblePosition = 0,
                itemCount = 1
            )
        )
        assertEquals(
            emptyList<Int>(),
            LeanbackArtworkLoader.computeHorizontalPrefetchIndices(
                focusedPosition = 0,
                firstVisiblePosition = -1,
                lastVisiblePosition = -1,
                itemCount = 0
            )
        )
    }

    @Test
    fun `artwork loader respects isHoldScrolling flag during prefetch and loadIntoImageView`() {
        val context = mockk<Context>(relaxed = true)
        val imageLoader = mockk<ImageLoader>(relaxed = true)
        val tracker = LinkedHashSet<String>()
        val imageView = mockk<ImageView>(relaxed = true)
        val testUrl = "https://example.com/test_poster.jpg"

        mockkObject(SingletonImageLoader)
        try {
            every { SingletonImageLoader.get(any()) } returns imageLoader

            // 1. While isHoldScrolling is true -> prefetch must return immediately without enqueuing
            LeanbackArtworkLoader.isHoldScrolling = true
            LeanbackArtworkLoader.prefetch(
                context = context,
                imageLoader = imageLoader,
                imageUrl = testUrl,
                widthPx = 240,
                heightPx = 360,
                prefetchedKeysTracker = tracker
            )
            assertTrue("Prefetch must be skipped during hold scroll", tracker.isEmpty())
            verify(exactly = 0) { imageLoader.enqueue(any()) }

            // 2. While isHoldScrolling is true and NOT in memory cache -> loadIntoImageView must return null
            every { imageLoader.memoryCache } returns null
            var cacheHitInvoked = false
            val disposable = LeanbackArtworkLoader.loadIntoImageView(
                imageView = imageView,
                imageUrl = testUrl,
                fallbackUrl = null,
                widthPx = 240,
                heightPx = 360,
                isStillBound = { true },
                onCacheHit = { cacheHitInvoked = true },
                onAsyncSuccess = {},
                onAsyncError = {}
            )
            assertNull("loadIntoImageView must return null when isHoldScrolling is active and uncached", disposable)
            assertTrue(!cacheHitInvoked)
            verify(exactly = 0) { imageLoader.enqueue(any()) }

            // 3. Reset isHoldScrolling to false -> prefetch works normally
            LeanbackArtworkLoader.isHoldScrolling = false
            LeanbackArtworkLoader.prefetch(
                context = context,
                imageLoader = imageLoader,
                imageUrl = testUrl,
                widthPx = 240,
                heightPx = 360,
                prefetchedKeysTracker = tracker
            )
            assertTrue("Prefetch tracker must record the prefetched key when hold scroll ends", tracker.isNotEmpty())
            verify(atLeast = 1) { imageLoader.enqueue(any()) }
        } finally {
            LeanbackArtworkLoader.isHoldScrolling = false
            unmockkObject(SingletonImageLoader)
        }
    }

    @Test
    fun `leanback row and universal adapters resolve low-ram 10-3 vs standard 16-4 horizontal prefetch profiles`() {
        val gib = 1024L * 1024L * 1024L
        val (lowRamContext, lowRamAm) = mockDeviceContext(totalRamBytes = 2L * gib)
        val (standardContext, _) = mockDeviceContext(totalRamBytes = 4L * gib)

        val lowRamProfile = LeanbackArtworkLoader.resolveProfileForContext(lowRamContext)
        val standardProfile = LeanbackArtworkLoader.resolveProfileForContext(standardContext)
        assertEquals(10, lowRamProfile.horizontalPrefetchAhead)
        assertEquals(3, lowRamProfile.horizontalPrefetchBehind)
        assertEquals(16, standardProfile.horizontalPrefetchAhead)
        assertEquals(4, standardProfile.horizontalPrefetchBehind)
        verify(exactly = 1) { lowRamAm.getMemoryInfo(any()) }

        val modernItems = (0 until 26).map { idx ->
            val url = "https://example.com/modern_$idx.jpg"
            ModernCarouselItem(
                key = "modern_$idx",
                title = "Item $idx",
                subtitle = null,
                imageUrl = url,
                heroPreview = HeroPreview(
                    title = "Item $idx",
                    logo = null,
                    description = null,
                    contentTypeText = null,
                    yearText = null,
                    imdbText = null,
                    genres = StableList(),
                    poster = url,
                    backdrop = null,
                    imageUrl = url
                ),
                payload = ModernPayload.Catalog(
                    focusKey = "focus_$idx",
                    itemId = "id_$idx",
                    itemType = "movie",
                    addonBaseUrl = "https://addon.example",
                    trailerTitle = "Item $idx",
                    trailerReleaseInfo = null,
                    trailerApiType = "movie"
                )
            )
        }
        val universalItems = (0 until 26).map { idx ->
            LeanbackCardItem.Poster(
                key = "poster_$idx",
                title = "Poster $idx",
                imageUrl = "https://example.com/poster_$idx.jpg"
            )
        }

        val imageLoader = mockk<ImageLoader>(relaxed = true)
        val enqueuedRequests = mutableListOf<ImageRequest>()
        every { imageLoader.memoryCache } returns null
        every { imageLoader.enqueue(capture(enqueuedRequests)) } returns mockk(relaxed = true)

        mockkObject(SingletonImageLoader)
        try {
            every { SingletonImageLoader.get(any()) } returns imageLoader

            // LeanbackRowAdapter: Low-RAM (10 ahead, 3 behind)
            val lowRamRowAdapter = createRowAdapter().apply {
                submitItems(modernItems)
                attachedRecyclerView = mockAttachedRecyclerView(lowRamContext, visibleCount = 6, firstIndex = 0)
            }
            assertEquals(lowRamProfile, lowRamRowAdapter.resolveLoadingProfile(lowRamContext))
            lowRamRowAdapter.onFocusedPositionChanged(lowRamContext, 0)
            assertEquals(
                (6..15).map { "https://example.com/modern_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )
            // Cached profile reused across subsequent calls and scroll settle
            enqueuedRequests.clear()
            lowRamRowAdapter.attachedRecyclerView = mockAttachedRecyclerView(lowRamContext, visibleCount = 6, firstIndex = 5)
            lowRamRowAdapter.onScrollSettled()
            assertEquals(
                listOf(16, 17, 18, 19, 20, 4, 3, 2).map { "https://example.com/modern_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )
            verify(exactly = 2) { lowRamAm.getMemoryInfo(any()) }

            // LeanbackRowAdapter: Standard (16 ahead, 4 behind)
            enqueuedRequests.clear()
            val standardRowAdapter = createRowAdapter().apply {
                submitItems(modernItems)
                attachedRecyclerView = mockAttachedRecyclerView(standardContext, visibleCount = 6, firstIndex = 0)
            }
            assertEquals(standardProfile, standardRowAdapter.resolveLoadingProfile(standardContext))
            standardRowAdapter.onFocusedPositionChanged(standardContext, 0)
            assertEquals(
                (6..21).map { "https://example.com/modern_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )
            enqueuedRequests.clear()
            standardRowAdapter.attachedRecyclerView = mockAttachedRecyclerView(standardContext, visibleCount = 6, firstIndex = 6)
            standardRowAdapter.onFocusedPositionChanged(standardContext, 9)
            assertEquals(
                listOf(22, 23, 24, 25, 5, 4, 3, 2).map { "https://example.com/modern_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )

            // LeanbackUniversalAdapter: Low-RAM (10 ahead, 3 behind)
            enqueuedRequests.clear()
            val lowRamUniversalAdapter = createUniversalAdapter().apply {
                submitItems(universalItems)
                attachedRecyclerView = mockAttachedRecyclerView(lowRamContext, visibleCount = 6, firstIndex = 0)
            }
            assertEquals(lowRamProfile, lowRamUniversalAdapter.resolveLoadingProfile(lowRamContext))
            lowRamUniversalAdapter.onFocusedPositionChanged(lowRamContext, 0)
            assertEquals(
                (6..15).map { "https://example.com/poster_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )
            enqueuedRequests.clear()
            lowRamUniversalAdapter.attachedRecyclerView = mockAttachedRecyclerView(lowRamContext, visibleCount = 6, firstIndex = 5)
            lowRamUniversalAdapter.onScrollSettled()
            assertEquals(
                listOf(16, 17, 18, 19, 20, 4, 3, 2).map { "https://example.com/poster_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )
            verify(exactly = 3) { lowRamAm.getMemoryInfo(any()) }

            // LeanbackUniversalAdapter: Standard (16 ahead, 4 behind) + explicit override + submitItems while attached
            enqueuedRequests.clear()
            val standardUniversalAdapter = createUniversalAdapter().apply {
                submitItems(universalItems)
                attachedRecyclerView = mockAttachedRecyclerView(standardContext, visibleCount = 6, firstIndex = 0)
            }
            assertEquals(standardProfile, standardUniversalAdapter.resolveLoadingProfile(standardContext))
            standardUniversalAdapter.onFocusedPositionChanged(standardContext, 0)
            assertEquals(
                (6..21).map { "https://example.com/poster_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )
            enqueuedRequests.clear()
            standardUniversalAdapter.loadingProfileProvider = { HomeImageLoadingProfile.forDevice(0L) }
            standardUniversalAdapter.attachedRecyclerView = mockAttachedRecyclerView(standardContext, visibleCount = 6, firstIndex = 6)
            standardUniversalAdapter.prefetchFromVisibleRange(standardContext)
            assertEquals(
                listOf(5, 4, 3).map { "https://example.com/poster_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )

            // Verify applyItemsInternal & applyItemsUpdate trigger edge prefetch with profile counts when attached
            enqueuedRequests.clear()
            val attachedRowAdapter = createRowAdapter()
            val attachedRowRv = mockAttachedRecyclerView(lowRamContext, visibleCount = 6, firstIndex = 0)
            attachedRowAdapter.attachedRecyclerView = attachedRowRv
            attachedRowAdapter.submitItems(modernItems)
            assertEquals(
                (6..15).map { "https://example.com/modern_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )

            enqueuedRequests.clear()
            val attachedUniversalAdapter = createUniversalAdapter()
            val attachedUniversalRv = mockAttachedRecyclerView(standardContext, visibleCount = 6, firstIndex = 0)
            attachedUniversalAdapter.attachedRecyclerView = attachedUniversalRv
            attachedUniversalAdapter.submitItems(universalItems)
            assertEquals(
                (6..21).map { "https://example.com/poster_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )
        } finally {
            unmockkObject(SingletonImageLoader)
        }
    }

    private fun mockDeviceContext(totalRamBytes: Long): Pair<Context, ActivityManager> {
        val am = mockk<ActivityManager>()
        every { am.getMemoryInfo(any()) } answers {
            firstArg<ActivityManager.MemoryInfo>().totalMem = totalRamBytes
        }
        val appCtx = mockk<Context>(relaxed = true)
        every { appCtx.getSystemService(Context.ACTIVITY_SERVICE) } returns am
        every { appCtx.applicationContext } returns appCtx

        val activityCtx = mockk<Context>(relaxed = true)
        every { activityCtx.applicationContext } returns appCtx
        return activityCtx to am
    }

    private fun mockAttachedRecyclerView(context: Context, visibleCount: Int, firstIndex: Int): RecyclerView {
        val rv = mockk<RecyclerView>(relaxed = true)
        every { rv.context } returns context
        every { rv.isAttachedToWindow } returns true
        every { rv.childCount } returns visibleCount
        every { rv.visibility } returns View.VISIBLE
        val children = (0 until visibleCount).map { mockk<View>(relaxed = true) }
        every { rv.getChildAt(any()) } answers { children[firstArg()] }
        every { rv.getChildAdapterPosition(any()) } answers {
            firstIndex + children.indexOf(firstArg<View>())
        }
        every { rv.post(any()) } answers {
            firstArg<Runnable>().run()
            true
        }
        return rv
    }

    private fun <T : RecyclerView.Adapter<*>> T.withTestObservable(): T = apply {
        val observableField = RecyclerView.Adapter::class.java.getDeclaredField("mObservable").apply {
            isAccessible = true
        }
        val observable = observableField.get(this)
        val observersField = android.database.Observable::class.java.getDeclaredField("mObservers").apply {
            isAccessible = true
        }
        if (observersField.get(observable) == null) {
            observersField.set(observable, ArrayList<Any>())
        }
    }

    private fun createRowAdapter(): LeanbackRowAdapter = LeanbackRowAdapter(
        cardWidthPx = 240,
        cardHeightPx = 360,
        cornerRadiusPx = 12f,
        showLabels = true,
        animationsEnabled = { true },
        onItemFocused = { _, _ -> },
        onItemClick = {},
        onItemLongClick = {},
        onLoadMore = {}
    ).withTestObservable()

    @Test
    fun `vertical grid isLastRow handles partial trailing rows accurately`() {
        // 6 columns, 8 items -> row 0: 0..5, row 1: 6..7
        for (pos in 0..5) {
            assertTrue("pos $pos should NOT be last row in 6-col 8-item grid", !isVerticalGridLastRow(pos, totalItems = 8, cols = 6))
        }
        assertTrue(isVerticalGridLastRow(6, totalItems = 8, cols = 6))
        assertTrue(isVerticalGridLastRow(7, totalItems = 8, cols = 6))

        // Exact full rows: 4 cols, 8 items -> row 0: 0..3, row 1: 4..7
        assertTrue(!isVerticalGridLastRow(3, totalItems = 8, cols = 4))
        assertTrue(isVerticalGridLastRow(4, totalItems = 8, cols = 4))

        // Single row & empty
        assertTrue(isVerticalGridLastRow(0, totalItems = 1, cols = 6))
        assertTrue(!isVerticalGridLastRow(0, totalItems = 0, cols = 6))
        assertTrue(!isVerticalGridLastRow(-1, totalItems = 5, cols = 6))
    }

    @Test
    fun `computeAdaptiveGridColumns prevents horizontal overflow across screen and poster sizes`() {
        // Standard 960dp TV minus 80dp padding = 880dp
        assertEquals(6, computeAdaptiveGridColumns(availableWidth = 880.dp, cardWidth = 130.dp, horizontalSpacing = 12.dp))
        // Large poster 180dp on 880dp -> must shrink to 4 cols instead of overflowing 6 cols
        assertEquals(4, computeAdaptiveGridColumns(availableWidth = 880.dp, cardWidth = 180.dp, horizontalSpacing = 12.dp))
        // Landscape 260dp on 880dp -> 3 cols
        assertEquals(3, computeAdaptiveGridColumns(availableWidth = 880.dp, cardWidth = 260.dp, horizontalSpacing = 12.dp))
        // Clamping floor/ceiling
        assertEquals(2, computeAdaptiveGridColumns(availableWidth = 200.dp, cardWidth = 260.dp, horizontalSpacing = 12.dp, minColumns = 2))
        assertEquals(8, computeAdaptiveGridColumns(availableWidth = 2000.dp, cardWidth = 100.dp, horizontalSpacing = 10.dp, maxColumns = 8))
    }

    @Test
    fun `artwork loader defers hardware bitmap policy unless blur transformation is requested`() {
        val context = mockk<Context>(relaxed = true)
        val imageLoader = mockk<ImageLoader>(relaxed = true)
        val requests = mutableListOf<ImageRequest>()
        every { imageLoader.memoryCache } returns null
        every { imageLoader.enqueue(capture(requests)) } returns mockk(relaxed = true)

        LeanbackArtworkLoader.prefetch(
            context = context,
            imageLoader = imageLoader,
            imageUrl = "https://example.com/regular_poster.jpg",
            widthPx = 240,
            heightPx = 360,
            blurred = false
        )
        LeanbackArtworkLoader.prefetch(
            context = context,
            imageLoader = imageLoader,
            imageUrl = "https://example.com/blurred_episode.jpg",
            widthPx = 320,
            heightPx = 180,
            blurred = true
        )

        assertEquals(2, requests.size)
        // Regular artwork leaves allowHardware unset so HardwareBitmapSettingInterceptor controls it
        assertNull(requests[0].extras[Extras.Key.allowHardware])
        // Blurred artwork forces software bitmap for BlurTransformation pixel access
        assertEquals(false, requests[1].extras[Extras.Key.allowHardware])
    }

    @Test
    fun `universal adapter debounces repeated loadMore calls for unchanged item count`() {
        var loadMoreCount = 0
        val adapter = LeanbackUniversalAdapter(
            cardWidthPx = 240,
            cardHeightPx = 360,
            cornerRadiusPx = 12f,
            showLabels = true,
            animationsEnabled = { true },
            onItemFocused = { _, _ -> },
            onItemClick = {},
            onItemLongClick = {},
            onLoadMore = { loadMoreCount++ }
        ).withTestObservable()

        val initialItems = (0 until 10).map { idx ->
            LeanbackCardItem.Poster(key = "p_$idx", title = "P$idx", imageUrl = "https://example.com/p_$idx.jpg")
        }
        adapter.submitItems(initialItems)
        adapter.requestLoadMore()
        adapter.requestLoadMore()
        adapter.requestLoadMore()
        assertEquals("Repeated requestLoadMore with same item count must fire once", 1, loadMoreCount)

        // After new page arrives, requestLoadMore can fire again once debounce window elapses
        val nextPage = initialItems + LeanbackCardItem.Poster(
            key = "p_10",
            title = "P10",
            imageUrl = "https://example.com/p_10.jpg"
        )
        adapter.submitItems(nextPage)
        assertEquals(1, loadMoreCount)
    }

    @Test
    fun `theme style resolves all 7 poster border styles accurately including rainbow and solid colors`() {
        val dominantNeon = 0xFF00E5FF.toInt()
        val solidGold = 0xFFFFD000.toInt()
        val solidRed = 0xFFFF2A55.toInt()
        val rainbowStops = listOf(0xFFFF2A55.toInt(), 0xFFFFD000.toInt(), 0xFF05DF72.toInt(), 0xFF2B7FFF.toInt())

        // 1. NONE -> transparent and not rainbow
        val noneStyle = com.nuvio.tv.ui.leanback.core.LeanbackThemeStyle(
            posterBorderStyle = com.nuvio.tv.domain.model.PosterBorderStyle.NONE,
            hideFocusBorder = true,
            focusRingColor = 0
        )
        assertTrue(!noneStyle.isRainbowBorder)
        assertEquals(0, noneStyle.resolveFocusedBorderColor(dominantNeon, blendWithDominant = true))

        // 2. SOLID_GOLD -> pure solid gold without blending poster dominant color
        val goldStyle = com.nuvio.tv.ui.leanback.core.LeanbackThemeStyle(
            posterBorderStyle = com.nuvio.tv.domain.model.PosterBorderStyle.SOLID_GOLD,
            focusRingColor = solidGold
        )
        assertTrue(!goldStyle.isRainbowBorder)
        assertEquals(solidGold, goldStyle.resolveFocusedBorderColor(dominantNeon, blendWithDominant = true))
        assertEquals(solidGold, goldStyle.resolveFocusedBorderColor(dominantNeon, blendWithDominant = false))

        // 3. RAINBOW -> preserves rainbow gradient colors and marks isRainbowBorder = true
        val rainbowStyle = com.nuvio.tv.ui.leanback.core.LeanbackThemeStyle(
            posterBorderStyle = com.nuvio.tv.domain.model.PosterBorderStyle.RAINBOW,
            focusRingColor = solidRed,
            focusGradientColors = rainbowStops
        )
        assertTrue(rainbowStyle.isRainbowBorder)
        assertEquals(rainbowStops, rainbowStyle.focusGradientColors)
        assertEquals(solidRed, rainbowStyle.resolveFocusedBorderColor(dominantNeon, blendWithDominant = true))

        // 4. THEME -> returns exact theme ring when blendWithDominant is false
        val themeRing = 0xFF38BDF8.toInt()
        val themeStyle = com.nuvio.tv.ui.leanback.core.LeanbackThemeStyle(
            posterBorderStyle = com.nuvio.tv.domain.model.PosterBorderStyle.THEME,
            focusRingColor = themeRing
        )
        assertEquals(themeRing, themeStyle.resolveFocusedBorderColor(dominantNeon, blendWithDominant = false))

        // 5. Glow color resolution: SOLID_* harmonizes with border color, THEME/RAINBOW use dominant poster color, NONE is transparent
        assertEquals(0, noneStyle.resolveGlowColor(dominantNeon))
        assertEquals(solidGold, goldStyle.resolveGlowColor(dominantNeon))
        assertEquals(dominantNeon, rainbowStyle.resolveGlowColor(dominantNeon))
        assertEquals(dominantNeon, themeStyle.resolveGlowColor(dominantNeon))
        assertEquals(0xFFF59E0B.toInt(), themeStyle.resolveGlowColor(0, fallbackColor = 0xFFF59E0B.toInt()))
    }

    @Test
    fun `modern row adapter detects watched state changes in place even when item list reference is unchanged`() {
        val watchedIds = mutableSetOf<String>()
        val items = (0 until 5).map { idx ->
            val id = "movie_$idx"
            val url = "https://example.com/$id.jpg"
            val meta = com.nuvio.tv.domain.model.MetaPreview(
                id = id,
                type = com.nuvio.tv.domain.model.ContentType.MOVIE,
                rawType = "movie",
                name = "Movie $idx",
                poster = url,
                posterShape = com.nuvio.tv.domain.model.PosterShape.POSTER,
                background = null,
                logo = null,
                description = null,
                releaseInfo = "2026",
                imdbRating = 8.0f,
                genres = emptyList()
            )
            ModernCarouselItem(
                key = "key_$idx",
                title = "Movie $idx",
                subtitle = "2026",
                imageUrl = url,
                heroPreview = HeroPreview(
                    title = "Movie $idx",
                    logo = null,
                    description = null,
                    contentTypeText = "Movie",
                    yearText = "2026",
                    imdbText = "8.0",
                    genres = StableList(),
                    poster = url,
                    backdrop = null,
                    imageUrl = url
                ),
                payload = ModernPayload.Catalog(
                    focusKey = "focus_$idx",
                    itemId = id,
                    itemType = "movie",
                    addonBaseUrl = "https://addon.example",
                    trailerTitle = "Movie $idx",
                    trailerReleaseInfo = "2026",
                    trailerApiType = "movie"
                ),
                metaPreview = meta
            )
        }

        val adapter = createRowAdapter().apply {
            isCatalogItemWatched = { meta -> meta.id in watchedIds }
        }

        adapter.submitItems(items)
        assertTrue(adapter.syncWatchedStateChanges().isEmpty())

        // Mark movie_2 as watched with the exact same list reference
        watchedIds.add("movie_2")
        assertEquals(listOf(2), adapter.syncWatchedStateChanges())

        // Subsequent submitItems/sync without further watched state changes is a no-op
        adapter.submitItems(items)
        assertTrue(adapter.syncWatchedStateChanges().isEmpty())

        // Toggle movie_2 off and movie_0, movie_4 on
        watchedIds.remove("movie_2")
        watchedIds.add("movie_0")
        watchedIds.add("movie_4")
        assertEquals(listOf(0, 2, 4), adapter.syncWatchedStateChanges())
    }

    @Test
    fun `modern row adapter prefetches next up continue watching items with blur transformation when enabled`() {
        val (standardContext, _) = mockDeviceContext(totalRamBytes = 4L * 1024L * 1024L * 1024L)
        val nextUpInfo = com.nuvio.tv.ui.screens.home.NextUpInfo(
            contentId = "tt999",
            contentType = "series",
            name = "Show",
            poster = "https://example.com/show_poster.jpg",
            backdrop = "https://example.com/show_backdrop.jpg",
            logo = null,
            videoId = "tt999:1:2",
            season = 1,
            episode = 2,
            episodeTitle = "Ep 2",
            thumbnail = "https://example.com/ep2_thumb.jpg",
            released = "2026-01-01",
            hasAired = true,
            airDateLabel = null,
            lastWatched = 1000L,
            sortTimestamp = 1000L
        )
        val cwItems = (0 until 10).map { idx ->
            val url = "https://example.com/ep_${idx}_thumb.jpg"
            ModernCarouselItem(
                key = "cw_$idx",
                title = "Show $idx",
                subtitle = "S1 E$idx",
                imageUrl = url,
                heroPreview = HeroPreview(
                    title = "Show $idx",
                    logo = null,
                    description = null,
                    contentTypeText = "S1 E$idx",
                    yearText = null,
                    imdbText = null,
                    genres = StableList(),
                    poster = url,
                    backdrop = url,
                    imageUrl = url
                ),
                payload = ModernPayload.ContinueWatching(
                    com.nuvio.tv.ui.screens.home.ContinueWatchingItem.NextUp(nextUpInfo.copy(contentId = "tt$idx"))
                )
            )
        }

        val imageLoader = mockk<ImageLoader>(relaxed = true)
        val enqueuedRequests = mutableListOf<ImageRequest>()
        every { imageLoader.memoryCache } returns null
        every { imageLoader.enqueue(capture(enqueuedRequests)) } returns mockk(relaxed = true)

        mockkObject(SingletonImageLoader)
        try {
            every { SingletonImageLoader.get(any()) } returns imageLoader

            val adapter = createRowAdapter().apply {
                blurUnwatchedEpisodes = true
                submitItems(cwItems)
                attachedRecyclerView = mockAttachedRecyclerView(standardContext, visibleCount = 4, firstIndex = 0)
            }
            adapter.onFocusedPositionChanged(standardContext, 0)

            assertTrue(enqueuedRequests.isNotEmpty())
            // All prefetched NextUp items must disable hardware bitmaps and include "_blur" suffix in memoryCacheKey
            for (req in enqueuedRequests) {
                assertEquals(false, req.extras[Extras.Key.allowHardware])
                assertTrue("Expected _blur in memoryCacheKey, got ${req.memoryCacheKey}", req.memoryCacheKey?.endsWith("_blur") == true)
            }
        } finally {
            unmockkObject(SingletonImageLoader)
        }
    }

    @Test
    fun `poster border style POSTER_COLOR uses pure poster dominant color independent of theme`() {
        val themeColor = 0xFFFFD000.toInt() // Gold theme
        val dominantPosterColor = 0xFF00E5FF.toInt() // Neon Cyan poster
        val style = com.nuvio.tv.ui.leanback.core.LeanbackThemeStyle(
            focusRingColor = themeColor,
            focusGradientColors = listOf(themeColor, 0xFFFF7E1D.toInt()),
            posterBorderStyle = com.nuvio.tv.domain.model.PosterBorderStyle.POSTER_COLOR
        )

        // POSTER_COLOR must never activate theme gradient shaders
        org.junit.Assert.assertFalse(style.isRainbowBorder)

        // Border color must strictly match the poster dominant color regardless of theme or animation setting
        assertEquals(
            dominantPosterColor,
            style.resolveFocusedBorderColor(dominantColor = dominantPosterColor, blendWithDominant = true)
        )
        assertEquals(
            dominantPosterColor,
            style.resolveFocusedBorderColor(dominantColor = dominantPosterColor, blendWithDominant = false)
        )
        assertEquals(
            dominantPosterColor,
            style.resolveGlowColor(dominantColor = dominantPosterColor)
        )

        // Falls back to neutral defaultFallbackColor when poster has no valid extracted color
        assertEquals(
            LeanbackColorEngine.defaultFallbackColor,
            style.resolveFocusedBorderColor(dominantColor = 0, blendWithDominant = true)
        )
    }

    @Test
    fun `computeNextHorizontalTargetPosition clamps lookahead while scrolling and steps normally when idle`() {
        // When idle (not scrolling), steps freely +1 / -1 and clamps to 0..lastIdx
        assertEquals(
            1,
            com.nuvio.tv.ui.screens.home.computeNextHorizontalTargetPosition(
                anchorPos = 0,
                pendingTargetPos = RecyclerView.NO_POSITION,
                step = 1,
                totalCount = 20,
                isScrolling = false
            )
        )
        assertEquals(
            5,
            com.nuvio.tv.ui.screens.home.computeNextHorizontalTargetPosition(
                anchorPos = 2,
                pendingTargetPos = 4,
                step = 1,
                totalCount = 20,
                isScrolling = false
            )
        )

        // When scrolling forward, target cannot drift more than MAX_HORIZONTAL_LOOKAHEAD (2) ahead of anchorPos
        assertEquals(
            4,
            com.nuvio.tv.ui.screens.home.computeNextHorizontalTargetPosition(
                anchorPos = 2,
                pendingTargetPos = 4,
                step = 1,
                totalCount = 20,
                isScrolling = true
            )
        )
        // Once anchorPos advances to 3, target can advance to 5
        assertEquals(
            5,
            com.nuvio.tv.ui.screens.home.computeNextHorizontalTargetPosition(
                anchorPos = 3,
                pendingTargetPos = 4,
                step = 1,
                totalCount = 20,
                isScrolling = true
            )
        )

        // When scrolling backward, target cannot drift more than MAX_HORIZONTAL_LOOKAHEAD (2) behind anchorPos
        assertEquals(
            6,
            com.nuvio.tv.ui.screens.home.computeNextHorizontalTargetPosition(
                anchorPos = 8,
                pendingTargetPos = 6,
                step = -1,
                totalCount = 20,
                isScrolling = true
            )
        )
        assertEquals(
            5,
            com.nuvio.tv.ui.screens.home.computeNextHorizontalTargetPosition(
                anchorPos = 7,
                pendingTargetPos = 6,
                step = -1,
                totalCount = 20,
                isScrolling = true
            )
        )
    }

    @Test
    fun `modern and universal adapters coalesce onItemFocused during horizontal hold and flush once on settle`() {
        val modernFocusedEvents = mutableListOf<Pair<Int, String>>()
        val modernItems = (0 until 10).map { idx ->
            val url = "https://example.com/m_$idx.jpg"
            ModernCarouselItem(
                key = "m_$idx",
                title = "M $idx",
                subtitle = null,
                imageUrl = url,
                heroPreview = HeroPreview(
                    title = "M $idx",
                    logo = null,
                    description = null,
                    contentTypeText = null,
                    yearText = null,
                    imdbText = null,
                    genres = StableList(),
                    poster = url,
                    backdrop = null,
                    imageUrl = url
                ),
                payload = ModernPayload.Catalog(
                    focusKey = "f_$idx",
                    itemId = "id_$idx",
                    itemType = "movie",
                    addonBaseUrl = "https://addon.example",
                    trailerTitle = "M $idx",
                    trailerReleaseInfo = null,
                    trailerApiType = "movie"
                )
            )
        }
        val rowAdapter = LeanbackRowAdapter(
            cardWidthPx = 240,
            cardHeightPx = 360,
            cornerRadiusPx = 12f,
            showLabels = true,
            animationsEnabled = { true },
            onItemFocused = { pos, item -> modernFocusedEvents.add(pos to item.key) },
            onItemClick = {},
            onItemLongClick = {},
            onLoadMore = {}
        ).withTestObservable()
        rowAdapter.submitItems(modernItems)

        val modernGrid = mockk<com.nuvio.tv.ui.screens.home.ModernInteropHorizontalGridView>(relaxed = true)
        rowAdapter.attachedRecyclerView = modernGrid

        // Single tap (isHoldingHorizontalKey = false) -> dispatches immediately
        every { modernGrid.isHoldingHorizontalKey } returns false
        rowAdapter.dispatchItemFocused(0, modernItems[0])
        assertEquals(listOf(0 to "m_0"), modernFocusedEvents)

        // Holding horizontal key -> intermediate cards 1, 2, 3, 4 are coalesced without firing Compose callback
        modernFocusedEvents.clear()
        every { modernGrid.isHoldingHorizontalKey } returns true
        rowAdapter.dispatchItemFocused(1, modernItems[1])
        rowAdapter.dispatchItemFocused(2, modernItems[2])
        rowAdapter.dispatchItemFocused(3, modernItems[3])
        rowAdapter.dispatchItemFocused(4, modernItems[4])
        assertTrue("Intermediate focus events must be suppressed during horizontal key hold", modernFocusedEvents.isEmpty())

        // Key release / settle -> flushes only the final settled card (4, m_4) once
        every { modernGrid.isHoldingHorizontalKey } returns false
        rowAdapter.flushPendingFocusedItem()
        assertEquals(listOf(4 to "m_4"), modernFocusedEvents)

        // Redundant flush is a no-op
        rowAdapter.flushPendingFocusedItem()
        assertEquals(listOf(4 to "m_4"), modernFocusedEvents)

        // Same verification for LeanbackUniversalAdapter
        val universalFocusedEvents = mutableListOf<Pair<Int, String>>()
        val universalItems = (0 until 10).map { idx ->
            LeanbackCardItem.Poster(key = "u_$idx", title = "U $idx", imageUrl = "https://example.com/u_$idx.jpg")
        }
        val universalAdapter = LeanbackUniversalAdapter(
            cardWidthPx = 240,
            cardHeightPx = 360,
            cornerRadiusPx = 12f,
            showLabels = true,
            animationsEnabled = { true },
            onItemFocused = { pos, item -> universalFocusedEvents.add(pos to item.key) },
            onItemClick = {},
            onItemLongClick = {},
            onLoadMore = {}
        ).withTestObservable()
        universalAdapter.submitItems(universalItems)

        val interopGrid = mockk<com.nuvio.tv.ui.leanback.container.InteropHorizontalGridView>(relaxed = true)
        universalAdapter.attachedRecyclerView = interopGrid

        every { interopGrid.isHoldingHorizontalKey } returns true
        universalAdapter.dispatchItemFocused(1, universalItems[1])
        universalAdapter.dispatchItemFocused(2, universalItems[2])
        universalAdapter.dispatchItemFocused(5, universalItems[5])
        assertTrue(universalFocusedEvents.isEmpty())

        every { interopGrid.isHoldingHorizontalKey } returns false
        universalAdapter.flushPendingFocusedItem()
        assertEquals(listOf(5 to "u_5"), universalFocusedEvents)
    }

    @Test
    fun `modern and universal adapters stride-throttle horizontal prefetch while holding horizontal key`() {
        val (standardContext, _) = mockDeviceContext(totalRamBytes = 4L * 1024L * 1024L * 1024L)
        val universalItems = (0 until 30).map { idx ->
            LeanbackCardItem.Poster(
                key = "p_$idx",
                title = "P $idx",
                imageUrl = "https://example.com/p_$idx.jpg"
            )
        }

        val imageLoader = mockk<ImageLoader>(relaxed = true)
        val enqueuedRequests = mutableListOf<ImageRequest>()
        every { imageLoader.memoryCache } returns null
        every { imageLoader.enqueue(capture(enqueuedRequests)) } returns mockk(relaxed = true)

        mockkObject(SingletonImageLoader)
        try {
            every { SingletonImageLoader.get(any()) } returns imageLoader

            val interopGrid = mockk<com.nuvio.tv.ui.leanback.container.InteropHorizontalGridView>(relaxed = true)
            every { interopGrid.context } returns standardContext
            every { interopGrid.isAttachedToWindow } returns true
            every { interopGrid.childCount } returns 0
            every { interopGrid.isHoldingHorizontalKey } returns true

            val adapter = createUniversalAdapter().apply {
                submitItems(universalItems)
                attachedRecyclerView = interopGrid
            }

            // Position 0 triggers initial prefetch window
            adapter.onFocusedPositionChanged(standardContext, 0, aheadCount = 2, behindCount = 0)
            assertEquals(
                listOf("https://example.com/p_1.jpg", "https://example.com/p_2.jpg"),
                enqueuedRequests.map { it.data.toString() }
            )

            // Positions 1 and 2 (< stride 3 from 0) are skipped while holding horizontal key
            enqueuedRequests.clear()
            adapter.onFocusedPositionChanged(standardContext, 1, aheadCount = 2, behindCount = 0)
            adapter.onFocusedPositionChanged(standardContext, 2, aheadCount = 2, behindCount = 0)
            assertTrue("Prefetch must be throttled for steps smaller than stride 3 while holding key", enqueuedRequests.isEmpty())

            // Position 3 (distance 3 from 0) runs prefetch
            adapter.onFocusedPositionChanged(standardContext, 3, aheadCount = 2, behindCount = 0)
            assertEquals(
                listOf("https://example.com/p_4.jpg", "https://example.com/p_5.jpg"),
                enqueuedRequests.map { it.data.toString() }
            )
        } finally {
            unmockkObject(SingletonImageLoader)
        }
    }

    private fun createUniversalAdapter(): LeanbackUniversalAdapter = LeanbackUniversalAdapter(
        cardWidthPx = 240,
        cardHeightPx = 360,
        cornerRadiusPx = 12f,
        showLabels = true,
        animationsEnabled = { true },
        onItemFocused = { _, _ -> },
        onItemClick = {},
        onItemLongClick = {},
        onLoadMore = {}
    ).withTestObservable()
}
