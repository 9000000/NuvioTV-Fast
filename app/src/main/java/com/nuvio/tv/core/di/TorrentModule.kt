package com.nuvio.tv.core.di

import android.content.Context
import com.nuvio.tv.core.torrent.TorrentModeCoordinator
import com.nuvio.tv.core.torrent.TorrentService
import com.nuvio.tv.core.torrent.TorrentSettings
import com.nuvio.tv.core.torrent.TorrServerAddonConfig
import com.nuvio.tv.core.torrent.TorrServerRemoteApi
import com.nuvio.tv.core.torrent.TorrServerStreamProvider
import com.nuvio.tv.data.remote.api.AddonApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TorrentModule {

    @Provides
    @Singleton
    fun provideTorrentSettings(
        @ApplicationContext context: Context
    ): TorrentSettings = TorrentSettings(context)

    @Provides
    @Singleton
    fun provideTorrentService(
        @ApplicationContext context: Context,
        torrentSettings: TorrentSettings
    ): TorrentService = TorrentService(context, torrentSettings)

    // ===== TorrServer Remote Client =====
    @Provides
    @Singleton
    fun provideTorrServerAddonConfig(
        @ApplicationContext context: Context
    ): TorrServerAddonConfig = TorrServerAddonConfig(context)

    @Provides
    @Singleton
    fun provideTorrServerRemoteApi(
        addonConfig: TorrServerAddonConfig
    ): TorrServerRemoteApi = TorrServerRemoteApi(addonConfig)

    @Provides
    @Singleton
    fun provideTorrServerStreamProvider(
        @ApplicationContext context: Context,
        addonConfig: TorrServerAddonConfig,
        addonApi: AddonApi
    ): TorrServerStreamProvider = TorrServerStreamProvider(context, addonConfig, addonApi)

    @Provides
    @Singleton
    fun provideTorrentModeCoordinator(
        torrentSettings: TorrentSettings,
        torrServerAddonConfig: TorrServerAddonConfig,
        torrentService: TorrentService
    ): TorrentModeCoordinator = TorrentModeCoordinator(torrentSettings, torrServerAddonConfig, torrentService)
}
