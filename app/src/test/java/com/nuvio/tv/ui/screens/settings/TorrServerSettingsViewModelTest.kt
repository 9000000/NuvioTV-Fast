package com.nuvio.tv.ui.screens.settings

import com.nuvio.tv.MainDispatcherRule
import com.nuvio.tv.core.torrent.TorrServerAddonConfig
import com.nuvio.tv.core.torrent.TorrServerAddonConfigData
import com.nuvio.tv.core.torrent.TorrServerRemoteApi
import com.nuvio.tv.core.torrent.TorrentService
import com.nuvio.tv.core.torrent.TorrentSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
    private val remoteApi = mockk<TorrServerRemoteApi>(relaxed = true) {
        io.mockk.coEvery { checkGStreamerSupport(any(), any(), any()) } returns Result.success(true)
        io.mockk.coEvery { healthCheck(any(), any(), any()) } returns Result.success("MatriX.134")
    }
    private val torrentSettings = mockk<TorrentSettings>(relaxed = true)
    private val torrentService = mockk<TorrentService>(relaxed = true)

    private fun createViewModel(): TorrServerSettingsViewModel {
        return TorrServerSettingsViewModel(
            addonConfig = addonConfig,
            remoteApi = remoteApi,
            torrentSettings = torrentSettings,
            torrentService = torrentService
        )
    }

    @Test
    fun togglesTorrServerEnabled() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onEvent(TorrServerSettingsEvent.ToggleEnabled(true))
        runCurrent()

        verify { addonConfig.setEnabled(true) }

        viewModel.onEvent(TorrServerSettingsEvent.ToggleEnabled(false))
        runCurrent()

        verify { addonConfig.setEnabled(false) }
        verify { torrentService.shutdown() }
    }

    @Test
    fun togglesUseEmbeddedServerAndShutsDownWhenDisabled() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onEvent(TorrServerSettingsEvent.ToggleUseEmbeddedServer(false))
        runCurrent()

        verify { addonConfig.setUseEmbeddedServer(false) }
        verify { torrentService.shutdown() }

        viewModel.onEvent(TorrServerSettingsEvent.ToggleUseEmbeddedServer(true))
        runCurrent()

        verify { addonConfig.setUseEmbeddedServer(true) }
    }

    @Test
    fun togglesHideTorrentStats() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onEvent(TorrServerSettingsEvent.ToggleHideTorrentStats(true))
        runCurrent()

        verify { torrentSettings.setHideTorrentStats(true) }
    }

    @Test
    fun updatesServerUrl() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onEvent(TorrServerSettingsEvent.UpdateServerUrl("http://192.168.1.50:8090"))
        runCurrent()

        verify { addonConfig.setServerUrl("http://192.168.1.50:8090") }
    }

    @Test
    fun updatesCredentials() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onEvent(TorrServerSettingsEvent.UpdateCredentials("admin", "secret"))
        runCurrent()

        verify { addonConfig.setCredentials("admin", "secret") }
    }

    @Test
    fun togglesStreamingOptions() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onEvent(TorrServerSettingsEvent.TogglePreload(false))
        viewModel.onEvent(TorrServerSettingsEvent.ToggleSaveToDb(true))
        viewModel.onEvent(TorrServerSettingsEvent.ToggleGst(true))
        runCurrent()

        verify { addonConfig.setPreload(false) }
        verify { addonConfig.setSaveToDb(true) }
        verify { addonConfig.setGst(true) }
    }
}
