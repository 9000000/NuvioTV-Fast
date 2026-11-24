package com.nuvio.tv.ui.screens.home

import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.data.local.EpisodeShuffleProfile
import com.nuvio.tv.data.local.EpisodeShuffleStore
import com.nuvio.tv.data.local.TraktSettingsDataStore
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.TmdbSettings
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.domain.repository.CatalogRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeCatalogLazyPipelineTest {

    private val created = mutableListOf<HomeViewModel>()

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `lazy catalog flush is not blocked by a slower sibling lazy catalog`() = runBlocking {
        val fastCatalogId = "catalog_fast"
        val slowCatalogId = "catalog_slow"
        val addon = testAddon(listOf(fastCatalogId, slowCatalogId))
        val fastDescriptor = addon.catalogs[0]
        val slowDescriptor = addon.catalogs[1]
        val fastRow = sampleRow(addon, fastCatalogId, "Fast Catalog")
        val slowRow = sampleRow(addon, slowCatalogId, "Slow Catalog")
        val slowGate = CompletableDeferred<Unit>()

        val catalogRepository = mockk<CatalogRepository>(relaxed = true) {
            every {
                getCatalog(
                    addonBaseUrl = any(),
                    addonId = any(),
                    addonName = any(),
                    catalogId = eq(fastCatalogId),
                    catalogName = any(),
                    type = any(),
                    skip = any(),
                    skipStep = any(),
                    extraArgs = any(),
                    supportsSkip = any(),
                    posterScreen = any()
                )
            } returns flow {
                emit(NetworkResult.Loading)
                emit(NetworkResult.Success(fastRow))
            }
            every {
                getCatalog(
                    addonBaseUrl = any(),
                    addonId = any(),
                    addonName = any(),
                    catalogId = eq(slowCatalogId),
                    catalogName = any(),
                    type = any(),
                    skip = any(),
                    skipStep = any(),
                    extraArgs = any(),
                    supportsSkip = any(),
                    posterScreen = any()
                )
            } returns flow {
                emit(NetworkResult.Loading)
                slowGate.await()
                emit(NetworkResult.Success(slowRow))
            }
        }

        val viewModel = newViewModel(catalogRepository)
        // Ensure uiState's combine pipeline is active from the start.
        viewModel.uiState.value
        viewModel.hasRenderedFirstCatalog = true

        val fastKey = viewModel.catalogKey(addon.id, fastDescriptor.apiType, fastCatalogId)
        val slowKey = viewModel.catalogKey(addon.id, slowDescriptor.apiType, slowCatalogId)
        synchronized(viewModel.catalogStateLock) {
            viewModel.catalogOrder.addAll(listOf(fastKey, slowKey))
            viewModel.pendingLazyCatalogs[fastKey] = addon to fastDescriptor
            viewModel.pendingLazyCatalogs[slowKey] = addon to slowDescriptor
            viewModel.placeholderDescriptors.add(placeholderFor(fastKey, addon, fastDescriptor, "Fast Catalog"))
            viewModel.placeholderDescriptors.add(placeholderFor(slowKey, addon, slowDescriptor, "Slow Catalog"))
        }

        viewModel.requestLazyCatalogLoad(fastKey)
        viewModel.requestLazyCatalogLoad(slowKey)

        withTimeout(5_000) {
            while (true) {
                val state = viewModel.uiState.value
                val hasFastCatalogRow = state.catalogRows.any { it.catalogId == fastCatalogId && it.items.isNotEmpty() }
                val hasFastHomeRow = state.homeRows.any { it is HomeRow.Catalog && it.row.catalogId == fastCatalogId }
                if (hasFastCatalogRow && hasFastHomeRow) break
                delay(20)
            }
        }

        assertTrue(
            "slow sibling catalog should still be suspended when fast catalog renders",
            slowGate.isActive
        )
        assertFalse(
            "slow catalog should not have rendered yet",
            viewModel.uiState.value.catalogRows.any { it.catalogId == slowCatalogId }
        )

        slowGate.complete(Unit)

        withTimeout(5_000) {
            while (viewModel.uiState.value.catalogRows.none { it.catalogId == slowCatalogId }) {
                delay(20)
            }
        }
        assertEquals(
            listOf(fastCatalogId, slowCatalogId),
            viewModel.uiState.value.catalogRows.map { it.catalogId }
        )
    }

    @Test
    fun `eager catalog flush is not blocked by a slower sibling eager catalog`(): Unit = runBlocking {
        val addon = testAddon(listOf("eager_0", "eager_1", "eager_slow"))
        val slowGate = CompletableDeferred<Unit>()
        val firstGate = CompletableDeferred<Unit>()

        val catalogRepository = mockk<CatalogRepository>(relaxed = true) {
            every {
                getCatalog(
                    addonBaseUrl = any(),
                    addonId = any(),
                    addonName = any(),
                    catalogId = any(),
                    catalogName = any(),
                    type = any(),
                    skip = any(),
                    skipStep = any(),
                    extraArgs = any(),
                    supportsSkip = any(),
                    posterScreen = any()
                )
            } answers {
                val id = arg<String>(3)
                flow {
                    emit(NetworkResult.Loading)
                    if (id == "eager_1") firstGate.await()
                    if (id == "eager_slow") slowGate.await()
                    emit(NetworkResult.Success(sampleRow(addon, id, "Title $id")))
                }
            }
        }

        val viewModel = newViewModel(catalogRepository)
        viewModel.uiState.value
        viewModel.isOpenFilesHighCheck = { true }
        viewModel._uiState.update { it.copy(layoutPreferencesReady = true, homeLayout = HomeLayout.MODERN) }

        viewModel.loadAllCatalogsPipeline(listOf(addon))

        // Wait for eager_0 (first catalog) to render so hasRenderedFirstCatalog becomes true.
        withTimeout(5_000) {
            while (viewModel.uiState.value.catalogRows.none { it.catalogId == "eager_0" }) {
                delay(20)
            }
        }

        // Now release eager_1 while eager_slow is still blocked: eager_1 must appear promptly (< 500ms)
        // without waiting on the 800ms safety flush or eager_slow completion.
        firstGate.complete(Unit)
        withTimeout(500) {
            while (viewModel.uiState.value.catalogRows.none { it.catalogId == "eager_1" }) {
                delay(20)
            }
        }
        assertTrue(slowGate.isActive)
        slowGate.complete(Unit)
    }

    @Test
    fun `background drip-load triggers up to dripAheadRows in order after eager catalogs finish`() = runBlocking {
        val catalogIds = (0 until 14).map { "cat_$it" }
        val addon = testAddon(catalogIds)
        val requestedCatalogIds = CopyOnWriteArrayList<String>()

        val catalogRepository = mockk<CatalogRepository>(relaxed = true) {
            every {
                getCatalog(
                    addonBaseUrl = any(),
                    addonId = any(),
                    addonName = any(),
                    catalogId = any(),
                    catalogName = any(),
                    type = any(),
                    skip = any(),
                    skipStep = any(),
                    extraArgs = any(),
                    supportsSkip = any(),
                    posterScreen = any()
                )
            } answers {
                val catalogId = arg<String>(3)
                requestedCatalogIds.add(catalogId)
                flowOf(NetworkResult.Success(sampleRow(addon, catalogId, "Title $catalogId")))
            }
        }

        val viewModel = newViewModel(catalogRepository)
        viewModel.isOpenFilesHighCheck = { false }
        viewModel._uiState.update { it.copy(layoutPreferencesReady = true, homeLayout = HomeLayout.MODERN) }

        viewModel.loadAllCatalogsPipeline(listOf(addon))

        val expectedTotal = viewModel.imageLoadingProfile.eagerCatalogRows + viewModel.imageLoadingProfile.dripAheadRows
        withTimeout(5_000) {
            while (requestedCatalogIds.size < expectedTotal) {
                delay(25)
            }
        }
        // Allow extra time to verify drip-load does not keep pulling past dripAheadRows.
        delay(350)

        val eagerCount = viewModel.imageLoadingProfile.eagerCatalogRows
        val dripCount = viewModel.imageLoadingProfile.dripAheadRows
        assertEquals(expectedTotal, requestedCatalogIds.size)
        assertEquals(
            catalogIds.take(eagerCount).toSet(),
            requestedCatalogIds.take(eagerCount).toSet()
        )
        assertEquals(
            catalogIds.drop(eagerCount).take(dripCount),
            requestedCatalogIds.drop(eagerCount)
        )
        val remainingPendingKeys = synchronized(viewModel.catalogStateLock) {
            viewModel.pendingLazyCatalogs.keys.toList()
        }
        assertEquals(
            catalogIds.drop(expectedTotal).map { id ->
                viewModel.catalogKey(addon.id, ContentType.MOVIE.toApiString(), id)
            },
            remainingPendingKeys
        )
    }

    @Test
    fun `scheduleBackgroundDripLazyCatalogs respects open-file pressure guard and dripAheadRows cap`() = runBlocking {
        val catalogIds = (0 until 14).map { "cat_$it" }
        val addon = testAddon(catalogIds)
        val requestedCatalogIds = CopyOnWriteArrayList<String>()

        val catalogRepository = mockk<CatalogRepository>(relaxed = true) {
            every {
                getCatalog(
                    addonBaseUrl = any(),
                    addonId = any(),
                    addonName = any(),
                    catalogId = any(),
                    catalogName = any(),
                    type = any(),
                    skip = any(),
                    skipStep = any(),
                    extraArgs = any(),
                    supportsSkip = any(),
                    posterScreen = any()
                )
            } answers {
                val catalogId = arg<String>(3)
                requestedCatalogIds.add(catalogId)
                flowOf(NetworkResult.Success(sampleRow(addon, catalogId, "Title $catalogId")))
            }
        }

        val viewModel = newViewModel(catalogRepository)
        val eagerCount = viewModel.imageLoadingProfile.eagerCatalogRows
        val dripCount = viewModel.imageLoadingProfile.dripAheadRows

        synchronized(viewModel.catalogStateLock) {
            addon.catalogs.forEachIndexed { index, descriptor ->
                val key = viewModel.catalogKey(addon.id, descriptor.apiType, descriptor.id)
                viewModel.catalogOrder.add(key)
                if (index < eagerCount) {
                    viewModel.catalogsMap[key] = sampleRow(addon, descriptor.id, descriptor.name)
                } else {
                    viewModel.pendingLazyCatalogs[key] = addon to descriptor
                }
            }
        }

        viewModel.isOpenFilesHighCheck = { true }
        viewModel.scheduleBackgroundDripLazyCatalogs(
            generation = viewModel.catalogLoadGeneration,
            initialDelayMs = 10L,
            batchDelayMs = 10L
        )
        delay(120)

        assertTrue(
            "drip-load must skip when open-file guard reports high pressure",
            requestedCatalogIds.isEmpty()
        )

        viewModel.isOpenFilesHighCheck = { false }
        viewModel.scheduleBackgroundDripLazyCatalogs(
            generation = viewModel.catalogLoadGeneration,
            initialDelayMs = 10L,
            batchDelayMs = 10L
        )

        withTimeout(5_000) {
            while (requestedCatalogIds.size < dripCount) {
                delay(20)
            }
        }
        delay(120)

        assertEquals(
            catalogIds.drop(eagerCount).take(dripCount),
            requestedCatalogIds.toList()
        )
    }

    private fun testAddon(catalogIds: List<String>) = Addon(
        id = "test.addon",
        name = "Test Addon",
        version = "1.0.0",
        description = null,
        logo = null,
        baseUrl = "https://addon.example",
        catalogs = catalogIds.map { id ->
            CatalogDescriptor(
                type = ContentType.MOVIE,
                id = id,
                name = "Catalog $id",
                showInHome = true,
                hasExplicitShowInHome = true
            )
        },
        types = listOf(ContentType.MOVIE),
        resources = emptyList()
    )

    private fun sampleRow(addon: Addon, catalogId: String, title: String) = CatalogRow(
        addonId = addon.id,
        addonName = addon.displayName,
        addonBaseUrl = addon.baseUrl,
        catalogId = catalogId,
        catalogName = title,
        type = ContentType.MOVIE,
        items = listOf(
            MetaPreview(
                id = "tt_$catalogId",
                type = ContentType.MOVIE,
                name = "Item $catalogId",
                poster = "https://img.example/$catalogId.jpg",
                posterShape = PosterShape.POSTER,
                background = null,
                logo = null,
                description = null,
                releaseInfo = null,
                imdbRating = null,
                genres = emptyList()
            )
        )
    )

    private fun placeholderFor(
        catalogKey: String,
        addon: Addon,
        descriptor: CatalogDescriptor,
        displayTitle: String
    ) = HomeViewModel.PlaceholderDescriptor(
        catalogKey = catalogKey,
        addonId = addon.id,
        addonName = addon.displayName,
        addonBaseUrl = addon.baseUrl,
        catalogId = descriptor.id,
        catalogName = descriptor.name,
        apiType = descriptor.apiType,
        displayTitle = displayTitle
    )

    private fun newViewModel(catalogRepository: CatalogRepository): HomeViewModel {
        val profileManager = mockk<com.nuvio.tv.core.profile.ProfileManager>(relaxed = true) {
            every { activeProfileReady } returns MutableStateFlow(false)
            every { activeProfileId } returns MutableStateFlow(1)
        }
        val cwEnrichmentCache =
            mockk<com.nuvio.tv.data.local.ContinueWatchingEnrichmentCache>(relaxed = true) {
                every { cacheCleared } returns MutableStateFlow(0)
            }
        val episodeShuffleStore = mockk<EpisodeShuffleStore>(relaxed = true) {
            every { profiles } returns MutableStateFlow(EpisodeShuffleProfile(profileId = 1, available = false))
        }
        val watchProgressRepository = mockk<WatchProgressRepository>(relaxed = true) {
            every { watchedItems } returns MutableStateFlow(emptyList<WatchedItem>())
            every { allProgress } returns MutableStateFlow(emptyList<WatchProgress>())
            every { observeNextUpSeeds() } returns MutableStateFlow(emptyList<WatchProgress>())
            every { getAllEpisodeProgress(any()) } returns flowOf(emptyMap())
            every { observeWatchedMovieIds() } returns MutableStateFlow(emptySet())
        }
        val traktSettingsDataStore = mockk<TraktSettingsDataStore>(relaxed = true) {
            every { continueWatchingDaysCap } returns MutableStateFlow(0)
            every { dismissedNextUpKeys } returns MutableStateFlow(emptySet())
        }
        val viewModel = HomeViewModel(
            appContext = mockk(relaxed = true),
            addonRepository = mockk(relaxed = true),
            startupSyncService = mockk(relaxed = true),
            catalogRepository = catalogRepository,
            watchProgressRepository = watchProgressRepository,
            libraryRepository = mockk(relaxed = true),
            metaRepository = mockk(relaxed = true),
            episodeShuffleStore = episodeShuffleStore,
            episodeShuffle = com.nuvio.tv.domain.model.EpisodeShuffle(),
            collectionsDataStore = mockk(relaxed = true),
            layoutPreferenceDataStore = mockk(relaxed = true),
            playerSettingsDataStore = mockk(relaxed = true),
            tmdbSettingsDataStore = mockk(relaxed = true),
            mdbListSettingsDataStore = mockk(relaxed = true),
            traktSettingsDataStore = traktSettingsDataStore,
            authSessionNoticeDataStore = mockk(relaxed = true),
            tmdbService = mockk(relaxed = true),
            tmdbMetadataService = mockk(relaxed = true),
            mdbListRepository = mockk(relaxed = true),
            imdbEpisodeRatingsRepository = mockk(relaxed = true),
            trailerService = mockk(relaxed = true),
            watchedSeriesStateHolder = mockk(relaxed = true),
            cwEnrichmentCache = cwEnrichmentCache,
            profileManager = profileManager,
            tvRecommendationManager = mockk(relaxed = true),
            serverCatalog = mockk(relaxed = true),
            trailerPlayerPool = mockk(relaxed = true),
            backgroundTrailerController = mockk(relaxed = true)
        )
        viewModel.startupGracePeriodActive = false
        viewModel.currentTmdbSettings = TmdbSettings(enabled = false)
        created += viewModel
        return viewModel
    }
}
