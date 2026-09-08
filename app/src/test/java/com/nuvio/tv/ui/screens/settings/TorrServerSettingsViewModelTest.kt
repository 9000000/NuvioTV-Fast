package com.nuvio.tv.ui.screens.settings

import com.nuvio.tv.MainDispatcherRule
import com.nuvio.tv.core.torrent.TorrServerAddonConfig
import com.nuvio.tv.core.torrent.TorrServerAddonConfigData
import com.nuvio.tv.core.torrent.TorrServerRemoteApi
import com.nuvio.tv.core.torrent.TorrentService
import com.nuvio.tv.core.torrent.TorrentSettings
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.repository.AddonRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TorrServerSettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val configFlow = MutableStateFlow(TorrServerAddonConfigData(enabled = false, serverUrl = "http://127.0.0.1:8090"))
    private val addonConfig = mockk<TorrServerAddonConfig>(relaxed = true) {
        every { config } returns configFlow
    }
    private val remoteApi = mockk<TorrServerRemoteApi>(relaxed = true)
    private val torrentSettings = mockk<TorrentSettings>(relaxed = true)
    private val torrentService = mockk<TorrentService>(relaxed = true)

    private val sampleAddons = listOf(
        Addon(
            id = "torrentio",
            name = "Torrentio",
            version = "1.0.0",
            description = "Torrents",
            logo = null,
            baseUrl = "https://torrentio.strem.fun",
            catalogs = emptyList(),
            types = emptyList(),
            resources = listOf(AddonResource(name = "stream", types = emptyList(), idPrefixes = null))
        ),
        Addon(
            id = "subtitles",
            name = "OpenSubtitles",
            version = "1.0.0",
            description = "Subtitles only",
            logo = null,
            baseUrl = "https://subtitles.strem.fun",
            catalogs = emptyList(),
            types = emptyList(),
            resources = listOf(AddonResource(name = "subtitles", types = emptyList(), idPrefixes = null))
        )
    )
    private val addonsFlow = MutableStateFlow(sampleAddons)
    private val addonRepository = mockk<AddonRepository>(relaxed = true) {
        every { getInstalledAddons() } returns addonsFlow
    }

    private fun createViewModel(): TorrServerSettingsViewModel {
        return TorrServerSettingsViewModel(
            addonConfig = addonConfig,
            remoteApi = remoteApi,
            torrentSettings = torrentSettings,
            torrentService = torrentService,
            addonRepository = addonRepository
        )
    }

    @Test
    fun enablesTorrServerAndAutomaticallyDisablesNativeP2P() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onEvent(TorrServerSettingsEvent.ToggleEnabled(true))
        runCurrent()

        verify { addonConfig.setEnabled(true) }
        verify { torrentSettings.setP2pEnabled(false) }
        verify { torrentService.shutdown() }
    }

    @Test
    fun filtersOnlyStreamAddonsForQuickSelection() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        val installedStreamAddons = viewModel.uiState.value.installedAddons
        assertEquals(1, installedStreamAddons.size)
        assertEquals("Torrentio", installedStreamAddons.first().displayName)
    }

    @Test
    fun selectingInstalledAddonUpdatesAddonUrl() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onEvent(TorrServerSettingsEvent.SelectInstalledAddon("https://torrentio.strem.fun/manifest.json"))
        runCurrent()

        verify { addonConfig.setAddonUrl("https://torrentio.strem.fun/manifest.json") }
    }
}
