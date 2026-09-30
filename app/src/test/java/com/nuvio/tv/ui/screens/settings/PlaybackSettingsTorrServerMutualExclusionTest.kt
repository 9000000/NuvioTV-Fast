package com.nuvio.tv.ui.screens.settings

import android.content.Context
import com.nuvio.tv.MainDispatcherRule
import com.nuvio.tv.core.plugin.PluginManager
import com.nuvio.tv.core.torrent.TorrentModeCoordinator
import com.nuvio.tv.core.torrent.TorrentService
import com.nuvio.tv.core.torrent.TorrentSettings
import com.nuvio.tv.data.local.DeviceLocalPlayerPreferences
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
class PlaybackSettingsTorrServerMutualExclusionTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val playerSettingsDataStore = mockk<PlayerSettingsDataStore>(relaxed = true)
    private val deviceLocalPlayerPreferences = mockk<DeviceLocalPlayerPreferences>(relaxed = true)
    private val trailerSettingsDataStore = mockk<TrailerSettingsDataStore>(relaxed = true)
    private val addonRepository = mockk<AddonRepository>(relaxed = true) {
        every { getInstalledAddons() } returns flowOf(emptyList())
    }
    private val pluginManager = mockk<PluginManager>(relaxed = true)
    private val torrentSettings = mockk<TorrentSettings>(relaxed = true)
    private val torrentService = mockk<TorrentService>(relaxed = true)
    private val torrentModeCoordinator = mockk<TorrentModeCoordinator>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private fun createViewModel(): PlaybackSettingsViewModel {
        return PlaybackSettingsViewModel(
            playerSettingsDataStore = playerSettingsDataStore,
            deviceLocalPlayerPreferences = deviceLocalPlayerPreferences,
            trailerSettingsDataStore = trailerSettingsDataStore,
            addonRepository = addonRepository,
            pluginManager = pluginManager,
            torrentSettings = torrentSettings,
            torrentService = torrentService,
            torrentModeCoordinator = torrentModeCoordinator,
            context = context
        )
    }

    @Test
    fun enablesP2pDelegatesToCoordinator() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.setP2pEnabled(true)
        runCurrent()

        verify { torrentModeCoordinator.enableNativeTorrent() }
    }

    @Test
    fun disablesP2pDelegatesToCoordinator() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.setP2pEnabled(false)
        runCurrent()

        verify { torrentModeCoordinator.disableNativeTorrent() }
    }
}
