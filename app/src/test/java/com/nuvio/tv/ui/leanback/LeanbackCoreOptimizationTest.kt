package com.nuvio.tv.ui.leanback

import android.app.ActivityManager
import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.nuvio.tv.core.image.CustomPosterFallbackInterceptor
import com.nuvio.tv.core.image.HomeImageLoadingProfile
import com.nuvio.tv.domain.model.PLACEHOLDER_IMAGE_URL
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.adapter.LeanbackUpdatePlan
import com.nuvio.tv.ui.leanback.adapter.computeLeanbackUpdatePlan
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
    fun `leanback row and universal adapters resolve low-ram 4-2 vs standard 6-2 horizontal prefetch profiles`() {
        val gib = 1024L * 1024L * 1024L
        val (lowRamContext, lowRamAm) = mockDeviceContext(totalRamBytes = 2L * gib)
        val (standardContext, _) = mockDeviceContext(totalRamBytes = 4L * gib)

        val lowRamProfile = LeanbackArtworkLoader.resolveProfileForContext(lowRamContext)
        val standardProfile = LeanbackArtworkLoader.resolveProfileForContext(standardContext)
        assertEquals(4, lowRamProfile.horizontalPrefetchAhead)
        assertEquals(2, lowRamProfile.horizontalPrefetchBehind)
        assertEquals(6, standardProfile.horizontalPrefetchAhead)
        assertEquals(2, standardProfile.horizontalPrefetchBehind)
        verify(exactly = 1) { lowRamAm.getMemoryInfo(any()) }

        val modernItems = (0 until 20).map { idx ->
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
        val universalItems = (0 until 20).map { idx ->
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

            // LeanbackRowAdapter: Low-RAM (4 ahead, 2 behind)
            val lowRamRowAdapter = createRowAdapter().apply {
                submitItems(modernItems)
                attachedRecyclerView = mockAttachedRecyclerView(lowRamContext, visibleCount = 6, firstIndex = 0)
            }
            assertEquals(lowRamProfile, lowRamRowAdapter.resolveLoadingProfile(lowRamContext))
            lowRamRowAdapter.onFocusedPositionChanged(lowRamContext, 0)
            assertEquals(
                (6..9).map { "https://example.com/modern_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )
            // Cached profile reused across subsequent calls and scroll settle
            enqueuedRequests.clear()
            lowRamRowAdapter.attachedRecyclerView = mockAttachedRecyclerView(lowRamContext, visibleCount = 6, firstIndex = 5)
            lowRamRowAdapter.onScrollSettled()
            assertEquals(
                listOf(11, 12, 13, 14, 4, 3).map { "https://example.com/modern_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )
            verify(exactly = 2) { lowRamAm.getMemoryInfo(any()) }

            // LeanbackRowAdapter: Standard (6 ahead, 2 behind)
            enqueuedRequests.clear()
            val standardRowAdapter = createRowAdapter().apply {
                submitItems(modernItems)
                attachedRecyclerView = mockAttachedRecyclerView(standardContext, visibleCount = 6, firstIndex = 0)
            }
            assertEquals(standardProfile, standardRowAdapter.resolveLoadingProfile(standardContext))
            standardRowAdapter.onFocusedPositionChanged(standardContext, 0)
            assertEquals(
                (6..11).map { "https://example.com/modern_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )
            enqueuedRequests.clear()
            standardRowAdapter.attachedRecyclerView = mockAttachedRecyclerView(standardContext, visibleCount = 6, firstIndex = 6)
            standardRowAdapter.onFocusedPositionChanged(standardContext, 9)
            assertEquals(
                listOf(12, 13, 14, 15, 16, 17, 5, 4).map { "https://example.com/modern_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )

            // LeanbackUniversalAdapter: Low-RAM (4 ahead, 2 behind)
            enqueuedRequests.clear()
            val lowRamUniversalAdapter = createUniversalAdapter().apply {
                submitItems(universalItems)
                attachedRecyclerView = mockAttachedRecyclerView(lowRamContext, visibleCount = 6, firstIndex = 0)
            }
            assertEquals(lowRamProfile, lowRamUniversalAdapter.resolveLoadingProfile(lowRamContext))
            lowRamUniversalAdapter.onFocusedPositionChanged(lowRamContext, 0)
            assertEquals(
                (6..9).map { "https://example.com/poster_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )
            enqueuedRequests.clear()
            lowRamUniversalAdapter.attachedRecyclerView = mockAttachedRecyclerView(lowRamContext, visibleCount = 6, firstIndex = 5)
            lowRamUniversalAdapter.onScrollSettled()
            assertEquals(
                listOf(11, 12, 13, 14, 4, 3).map { "https://example.com/poster_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )
            verify(exactly = 3) { lowRamAm.getMemoryInfo(any()) }

            // LeanbackUniversalAdapter: Standard (6 ahead, 2 behind) + explicit override + submitItems while attached
            enqueuedRequests.clear()
            val standardUniversalAdapter = createUniversalAdapter().apply {
                submitItems(universalItems)
                attachedRecyclerView = mockAttachedRecyclerView(standardContext, visibleCount = 6, firstIndex = 0)
            }
            assertEquals(standardProfile, standardUniversalAdapter.resolveLoadingProfile(standardContext))
            standardUniversalAdapter.onFocusedPositionChanged(standardContext, 0)
            assertEquals(
                (6..11).map { "https://example.com/poster_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )
            enqueuedRequests.clear()
            standardUniversalAdapter.loadingProfileProvider = { HomeImageLoadingProfile.forDevice(0L) }
            standardUniversalAdapter.attachedRecyclerView = mockAttachedRecyclerView(standardContext, visibleCount = 6, firstIndex = 6)
            standardUniversalAdapter.prefetchFromVisibleRange(standardContext)
            assertEquals(
                listOf(12, 13, 14, 15, 5, 4).map { "https://example.com/poster_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )

            // Verify applyItemsInternal & applyItemsUpdate trigger edge prefetch with profile counts when attached
            enqueuedRequests.clear()
            val attachedRowAdapter = createRowAdapter()
            val attachedRowRv = mockAttachedRecyclerView(lowRamContext, visibleCount = 6, firstIndex = 0)
            attachedRowAdapter.attachedRecyclerView = attachedRowRv
            attachedRowAdapter.submitItems(modernItems)
            assertEquals(
                (6..9).map { "https://example.com/modern_$it.jpg" },
                enqueuedRequests.map { it.data.toString() }
            )

            enqueuedRequests.clear()
            val attachedUniversalAdapter = createUniversalAdapter()
            val attachedUniversalRv = mockAttachedRecyclerView(standardContext, visibleCount = 6, firstIndex = 0)
            attachedUniversalAdapter.attachedRecyclerView = attachedUniversalRv
            attachedUniversalAdapter.submitItems(universalItems)
            assertEquals(
                (6..11).map { "https://example.com/poster_$it.jpg" },
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
        onLoadMore = {},
        onNavigateDown = { false },
        onNavigateUp = { false },
        onNavigateLeftOnFirstItem = { false },
        onNavigateBack = { false }
    ).withTestObservable()

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
