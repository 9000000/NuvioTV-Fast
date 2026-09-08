package com.nuvio.tv.ui.screens.settings

import com.nuvio.tv.MainDispatcherRule
import com.nuvio.tv.core.plugin.PluginManager
import com.nuvio.tv.core.torrent.TorrServerAddonConfig
import com.nuvio.tv.core.torrent.TorrentSettings
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.TrailerSettingsDataStore
import com.nuvio.tv.domain.repository.AddonRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackSettingsTorrServerExclusionTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val playerSettingsDataStore = mockk<PlayerSettingsDataStore>(relaxed = true)
    private val trailerSettingsDataStore = mockk<TrailerSettingsDataStore>(relaxed = true)
    private val addonRepository = mockk<AddonRepository>(relaxed = true) {
        every { getInstalledAddons() } returns flowOf(emptyList())
    }
    private val pluginManager = mockk<PluginManager>(relaxed = true)
    private val torrentSettings = mockk<TorrentSettings>(relaxed = true)
    private val torrServerAddonConfig = mockk<TorrServerAddonConfig>(relaxed = true)

    private fun createViewModel(): PlaybackSettingsViewModel {
        return PlaybackSettingsViewModel(
            playerSettingsDataStore = playerSettingsDataStore,
            trailerSettingsDataStore = trailerSettingsDataStore,
            addonRepository = addonRepository,
            pluginManager = pluginManager,
            torrentSettings = torrentSettings,
            torrServerAddonConfig = torrServerAddonConfig
        )
    }

    @Test
    fun enablesP2pAndAutomaticallyDisablesTorrServer() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.setP2pEnabled(true)
        runCurrent()

        verify { torrentSettings.setP2pEnabled(true) }
        verify { torrServerAddonConfig.setEnabled(false) }
    }

    @Test
    fun disablesP2pWithoutTouchingTorrServer() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.setP2pEnabled(false)
        runCurrent()

        verify { torrentSettings.setP2pEnabled(false) }
        verify(exactly = 0) { torrServerAddonConfig.setEnabled(any()) }
    }
}
