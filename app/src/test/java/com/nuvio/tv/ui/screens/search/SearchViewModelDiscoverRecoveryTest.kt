package com.nuvio.tv.ui.screens.search

import android.content.Context
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.data.local.DiscoverSelectionDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.SearchHistoryDataStore
import com.nuvio.tv.data.local.WatchedSeriesStateHolder
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.DiscoverLocation
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import com.nuvio.tv.ui.components.posteroptions.PosterOptionsController
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelDiscoverRecoveryTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `discover recovers automatically after initial empty addon emission settles`() = runTest {
        val addonsFlow = MutableStateFlow<List<Addon>>(emptyList())
        val viewModel = newDiscoverViewModel(addonsFlow)

        viewModel.ensureDiscoverLoaded()
        runCurrent()
        // Wait past the initial settle window while addonsFlow is still empty.
        advanceTimeBy(500)
        runCurrent()

        assertTrue(viewModel.uiState.value.discoverInitialized)
        assertTrue(viewModel.uiState.value.discoverCatalogs.isEmpty())
        assertFalse(viewModel.uiState.value.discoverLoading)

        // Emit real installed addons later (simulating async manifest/DataStore load).
        addonsFlow.value = listOf(discoverAddon())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.discoverCatalogs.size)
        assertEquals(1, state.discoverResults.size)
        assertEquals("m1", state.discoverResults.single().id)
        assertFalse(state.discoverLoading)
    }

    @Test
    fun `discover stays loading during startup race when real addons arrive within settle window`() = runTest {
        val addonsFlow = MutableStateFlow<List<Addon>>(emptyList())
        val viewModel = newDiscoverViewModel(addonsFlow)

        viewModel.ensureDiscoverLoaded()
        runCurrent()
        assertTrue(viewModel.uiState.value.discoverLoading)
        assertFalse(viewModel.uiState.value.discoverInitialized)

        // Real addons arrive 100ms later, before settle timeout expires.
        advanceTimeBy(100)
        addonsFlow.value = listOf(discoverAddon())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.discoverInitialized)
        assertEquals(1, state.discoverCatalogs.size)
        assertEquals(1, state.discoverResults.size)
        assertFalse(state.discoverLoading)
    }

    private fun newDiscoverViewModel(addonsFlow: MutableStateFlow<List<Addon>>): SearchViewModel {
        val addonRepository = object : AddonRepository {
            override fun getInstalledAddons(): Flow<List<Addon>> = addonsFlow
            override suspend fun fetchAddon(baseUrl: String): NetworkResult<Addon> = error("unused")
            override suspend fun addAddon(url: String) = error("unused")
            override suspend fun removeAddon(url: String) = error("unused")
            override suspend fun setAddonOrder(urls: List<String>) = error("unused")
            override suspend fun setAddonEnabled(url: String, enabled: Boolean) = error("unused")
        }
        val catalogRepository = object : CatalogRepository {
            override fun getCatalog(
                addonBaseUrl: String,
                addonId: String,
                addonName: String,
                catalogId: String,
                catalogName: String,
                type: String,
                skip: Int,
                skipStep: Int,
                extraArgs: Map<String, String>,
                supportsSkip: Boolean,
                posterScreen: com.nuvio.tv.core.poster.CustomPosterScreen
            ): Flow<NetworkResult<CatalogRow>> = flow {
                emit(NetworkResult.Loading)
                emit(
                    NetworkResult.Success(
                        CatalogRow(
                            addonId = addonId,
                            addonName = addonName,
                            addonBaseUrl = addonBaseUrl,
                            catalogId = catalogId,
                            catalogName = catalogName,
                            type = ContentType.MOVIE,
                            items = listOf(
                                MetaPreview(
                                    id = "m1",
                                    type = ContentType.MOVIE,
                                    name = "Discover Item",
                                    poster = null,
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
                    )
                )
            }
        }
        val layoutPreferences = mockk<LayoutPreferenceDataStore>()
        every { layoutPreferences.discoverLocation } returns flowOf(DiscoverLocation.IN_SIDEBAR)
        every { layoutPreferences.posterCardWidthDp } returns flowOf(126)
        every { layoutPreferences.posterLabelsEnabled } returns flowOf(true)
        every { layoutPreferences.catalogAddonNameEnabled } returns flowOf(true)
        every { layoutPreferences.posterCardHeightDp } returns flowOf(189)
        every { layoutPreferences.posterCardCornerRadiusDp } returns flowOf(12)
        every { layoutPreferences.catalogTypeSuffixEnabled } returns flowOf(true)
        every { layoutPreferences.hideUnreleasedContent } returns flowOf(false)

        val discoverSelection = mockk<DiscoverSelectionDataStore>(relaxed = true)
        coEvery { discoverSelection.getSelectedCatalogKey() } returns null

        val history = mockk<SearchHistoryDataStore>(relaxed = true)
        every { history.recentSearches } returns flowOf(emptyList())

        val watchProgress = mockk<WatchProgressRepository>()
        every { watchProgress.observeWatchedMovieIds() } returns flowOf(emptySet())

        val watchedSeries = mockk<WatchedSeriesStateHolder>()
        every { watchedSeries.fullyWatchedSeriesIds } returns MutableStateFlow(emptySet())

        return SearchViewModel(
            addonRepository = addonRepository,
            catalogRepository = catalogRepository,
            metaRepository = mockk(relaxed = true),
            discoverSelectionDataStore = discoverSelection,
            layoutPreferenceDataStore = layoutPreferences,
            searchHistoryDataStore = history,
            watchProgressRepository = watchProgress,
            watchedSeriesStateHolder = watchedSeries,
            posterOptions = mockk<PosterOptionsController>(relaxed = true),
            serverCatalog = mockk { every { searchAddons } returns flowOf(emptyList()) },
            context = mockk<Context>(relaxed = true)
        )
    }

    private fun discoverAddon(): Addon = Addon(
        id = "addon",
        name = "Addon",
        version = "1.0.0",
        description = null,
        logo = null,
        baseUrl = "https://example.test",
        catalogs = listOf(
            CatalogDescriptor(
                type = ContentType.MOVIE,
                id = "popular",
                name = "Popular"
            )
        ),
        types = listOf(ContentType.MOVIE),
        resources = emptyList()
    )
}
