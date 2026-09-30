package com.nuvio.tv.features.livetv

/**
 * Backward-compatible facade delegating to [XtreamPortalService] and [StalkerPortalService].
 */
internal suspend fun fetchXtreamChannels(settings: LiveTvXtreamSettings): List<LiveTvChannel> =
    XtreamPortalService.fetchChannels(settings)

internal suspend fun fetchStalkerChannels(
    settings: LiveTvStalkerSettings,
    maxPagesLimit: Int = 500
): List<LiveTvChannel> =
    StalkerPortalService.fetchChannels(settings, maxPagesLimit)

internal suspend fun preparePortalChannelForPlayback(
    channel: LiveTvChannel,
    settings: LiveTvStalkerSettings
): LiveTvChannel =
    StalkerPortalService.prepareForPlayback(channel, settings)
