package com.nuvio.tv.core.di

import android.content.Context
import com.nuvio.tv.core.torrent.TorrServerApi
import com.nuvio.tv.core.torrent.TorrServerBinary
import com.nuvio.tv.core.torrent.TorrentService
import com.nuvio.tv.core.torrent.TorrentSettings
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
    fun provideTorrServerBinary(
        @ApplicationContext context: Context
    ): TorrServerBinary = TorrServerBinary(context)

    @Provides
    @Singleton
    fun provideTorrServerApi(
        binary: TorrServerBinary
    ): TorrServerApi = TorrServerApi(binary)

    @Provides
    @Singleton
    fun provideTorrentService(
        @dagger.hilt.android.qualifiers.ApplicationContext appContext: android.content.Context,
        binary: TorrServerBinary,
        api: TorrServerApi,
        addonConfig: com.nuvio.tv.core.torrent.TorrServerAddonConfig
    ): TorrentService = TorrentService(appContext, binary, api, addonConfig)

    @Provides
    @Singleton
    fun provideTorrServerAddonConfig(
        @ApplicationContext context: Context
    ): com.nuvio.tv.core.torrent.TorrServerAddonConfig =
        com.nuvio.tv.core.torrent.TorrServerAddonConfig(context)

    @Provides
    @Singleton
    fun provideTorrServerRemoteApi(
        addonConfig: com.nuvio.tv.core.torrent.TorrServerAddonConfig
    ): com.nuvio.tv.core.torrent.TorrServerRemoteApi =
        com.nuvio.tv.core.torrent.TorrServerRemoteApi(addonConfig)

    @Provides
    @Singleton
    fun provideTorrServerStreamProvider(
        @ApplicationContext context: Context,
        addonConfig: com.nuvio.tv.core.torrent.TorrServerAddonConfig,
        addonApi: com.nuvio.tv.data.remote.api.AddonApi
    ): com.nuvio.tv.core.torrent.TorrServerStreamProvider =
        com.nuvio.tv.core.torrent.TorrServerStreamProvider(context, addonConfig, addonApi)
}
