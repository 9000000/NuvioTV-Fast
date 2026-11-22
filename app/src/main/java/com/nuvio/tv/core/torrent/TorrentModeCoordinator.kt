package com.nuvio.tv.core.torrent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates mutually exclusive activation between Native Torrent (Nuvio Engine)
 * and TorrServer (External Client).
 *
 * When TorrServer is enabled, Native Torrent is automatically disabled and shut down.
 * When Native Torrent is enabled, TorrServer is automatically disabled.
 */
@Singleton
class TorrentModeCoordinator @Inject constructor(
    private val torrentSettings: TorrentSettings,
    private val torrServerAddonConfig: TorrServerAddonConfig,
    private val torrentService: TorrentService
) {
    fun enableNativeTorrent() {
        torrServerAddonConfig.setEnabled(false)
        torrentSettings.setP2pEnabled(true)
    }

    fun disableNativeTorrent() {
        torrentSettings.setP2pEnabled(false)
        torrentService.shutdown()
    }

    fun enableTorrServer() {
        torrentSettings.setP2pEnabled(false)
        torrentService.shutdown()
        torrServerAddonConfig.setEnabled(true)
    }

    fun disableTorrServer() {
        torrServerAddonConfig.setEnabled(false)
        torrentService.shutdown()
    }

    fun onTorrServerEnabledChanged(enabled: Boolean) {
        if (enabled) enableTorrServer() else disableTorrServer()
    }

    fun onNativeTorrentEnabledChanged(enabled: Boolean) {
        if (enabled) enableNativeTorrent() else disableNativeTorrent()
    }
}
