package com.nuvio.tv.core.torrent

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class TorrentModeCoordinatorTest {
    private val torrentSettings = mockk<TorrentSettings>(relaxed = true)
    private val torrServerAddonConfig = mockk<TorrServerAddonConfig>(relaxed = true)
    private val torrentService = mockk<TorrentService>(relaxed = true)

    private val coordinator = TorrentModeCoordinator(
        torrentSettings = torrentSettings,
        torrServerAddonConfig = torrServerAddonConfig,
        torrentService = torrentService
    )

    @Test
    fun enablingTorrServerDisablesNativeTorrentAndShutsDownService() {
        coordinator.onTorrServerEnabledChanged(true)

        verify { torrentSettings.setP2pEnabled(false) }
        verify { torrentService.shutdown() }
        verify { torrServerAddonConfig.setEnabled(true) }
    }

    @Test
    fun disablingTorrServerOnlyDisablesTorrServerAndShutsDownService() {
        coordinator.onTorrServerEnabledChanged(false)

        verify { torrServerAddonConfig.setEnabled(false) }
        verify { torrentService.shutdown() }
        verify(exactly = 0) { torrentSettings.setP2pEnabled(any()) }
    }

    @Test
    fun enablingNativeTorrentDisablesTorrServer() {
        coordinator.onNativeTorrentEnabledChanged(true)

        verify { torrServerAddonConfig.setEnabled(false) }
        verify { torrentSettings.setP2pEnabled(true) }
    }

    @Test
    fun disablingNativeTorrentDisablesP2pAndShutsDownService() {
        coordinator.onNativeTorrentEnabledChanged(false)

        verify { torrentSettings.setP2pEnabled(false) }
        verify { torrentService.shutdown() }
        verify(exactly = 0) { torrServerAddonConfig.setEnabled(any()) }
    }
}
