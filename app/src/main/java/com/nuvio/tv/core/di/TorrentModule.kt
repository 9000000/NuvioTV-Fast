package com.nuvio.tv.core.di

import android.content.Context
import com.nuvio.tv.core.torrent.TorrServerAddonConfig
import com.nuvio.tv.core.torrent.TorrServerApi
import com.nuvio.tv.core.torrent.TorrServerBinary
import com.nuvio.tv.core.torrent.TorrServerRemoteApi
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

    // ===== P2P Native (Embedded TorrServer) =====
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

    // ===== TorrServer Remote Addon =====
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
        addonApi: com.nuvio.tv.data.remote.api.AddonApi
    ): com.nuvio.tv.core.torrent.TorrServerStreamProvider =
        com.nuvio.tv.core.torrent.TorrServerStreamProvider(context, addonConfig, addonApi)

    // ===== TorrentService (handles both P2P and Remote) =====
    @Provides
    @Singleton
    fun provideTorrentService(
        @dagger.hilt.android.qualifiers.ApplicationContext appContext: android.content.Context,
        binary: TorrServerBinary,
        api: TorrServerApi,
        remoteApi: TorrServerRemoteApi,
        addonConfig: TorrServerAddonConfig
    ): TorrentService = TorrentService(appContext, binary, api, remoteApi, addonConfig)
}
