package com.nuvio.tv.features.livetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LiveTvM3uParserTest {

    @Test
    fun parseM3uPlaylist_standardChannel_parsedCorrectly() {
        val payload = """
            #EXTM3U
            #EXTINF:-1 tvg-id="vtv1" tvg-name="VTV1 HD" tvg-logo="https://example.com/vtv1.png" group-title="Vietnam", VTV1 HD
            https://stream.example.com/vtv1/index.m3u8
        """.trimIndent()

        val channels = parseM3uPlaylist(payload)
        assertEquals(1, channels.size)

        val ch = channels.first()
        assertEquals("VTV1 HD", ch.name)
        assertEquals("https://stream.example.com/vtv1/index.m3u8", ch.streamUrl)
        assertEquals("https://example.com/vtv1.png", ch.logoUrl)
        assertEquals("Vietnam", ch.group)
        assertEquals("m3u8", ch.streamType)
        assertNull(ch.drmType)
        assertNull(ch.drmKey)
    }

    @Test
    fun parseM3uPlaylist_kodiProps_clearkeyParsed() {
        val payload = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.manifest_type=mpd
            #KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
            #KODIPROP:inputstream.adaptive.license_key=10000000000000000000000000000001:20000000000000000000000000000002
            #EXTINF:-1 tvg-name="HBO HD" group-title="Movies", HBO HD
            https://stream.example.com/hbo.mpd
        """.trimIndent()

        val channels = parseM3uPlaylist(payload)
        assertEquals(1, channels.size)

        val ch = channels.first()
        assertEquals("HBO HD", ch.name)
        assertEquals("https://stream.example.com/hbo.mpd", ch.streamUrl)
        assertEquals("mpd", ch.streamType)
        assertEquals("clearkey", ch.drmType)
        assertEquals("10000000000000000000000000000001:20000000000000000000000000000002", ch.drmKey)
    }

    @Test
    fun parseM3uPlaylist_kodiProps_widevineAndPipeHeaders() {
        val payload = """
            #EXTM3U
            #EXT-X-KODI:inputstream.adaptive.manifest_type=mpd
            #EXT-X-KODI:inputstream.adaptive.license_type=com.widevine.alpha
            #EXT-X-KODI:inputstream.adaptive.license_key=https://lic.example.com/wv|User-Agent=CustomUA/1.0&Referer=https://ref.com
            #EXTINF:-1 tvg-name="Sports 1", Sports 1
            https://stream.example.com/sports1.mpd|Origin=https://origin.com
        """.trimIndent()

        val channels = parseM3uPlaylist(payload)
        assertEquals(1, channels.size)

        val ch = channels.first()
        assertEquals("Sports 1", ch.name)
        assertEquals("https://stream.example.com/sports1.mpd", ch.streamUrl)
        assertEquals("mpd", ch.streamType)
        assertEquals("widevine", ch.drmType)
        assertEquals("https://lic.example.com/wv", ch.drmKey)
        assertEquals("CustomUA/1.0", ch.headers["User-Agent"])
        assertEquals("https://ref.com", ch.headers["Referer"])
        assertEquals("https://origin.com", ch.headers["Origin"])
    }

    @Test
    fun parseM3uPlaylist_inlineExtInfDrmAttributes() {
        val payload = """
            #EXTM3U
            #EXTINF:-1 license_type="clearkey" license_key="aabbccdd11223344:5566778899001122" manifest_type="mpd" group-title="News", BBC News
            https://live.example.com/bbc/manifest.mpd
        """.trimIndent()

        val channels = parseM3uPlaylist(payload)
        assertEquals(1, channels.size)

        val ch = channels.first()
        assertEquals("BBC News", ch.name)
        assertEquals("clearkey", ch.drmType)
        assertEquals("aabbccdd11223344:5566778899001122", ch.drmKey)
        assertEquals("mpd", ch.streamType)
        assertEquals("News", ch.group)
    }

    @Test
    fun parseM3uPlaylist_inlineUnquotedAttributes() {
        val payload = """
            #EXTM3U
            #EXTINF:-1 license_type=clearkey license_key=deadbeef:cafebabe manifest_type=mpd group-title=Kids, Cartoon Network
            https://live.example.com/cn/manifest.mpd
        """.trimIndent()

        val channels = parseM3uPlaylist(payload)
        assertEquals(1, channels.size)

        val ch = channels.first()
        assertEquals("Cartoon Network", ch.name)
        assertEquals("clearkey", ch.drmType)
        assertEquals("deadbeef:cafebabe", ch.drmKey)
        assertEquals("mpd", ch.streamType)
        assertEquals("Kids", ch.group)
    }

    @Test
    fun parseM3uPlaylist_extXKeyHlsDrm() {
        val payload = """
            #EXTM3U
            #EXTINF:-1, Discovery Channel
            #EXT-X-KEY:METHOD=SAMPLE-AES-CTR,URI="https://drm.example.com/key",KEYFORMAT="com.widevine"
            https://stream.example.com/discovery/master.m3u8
        """.trimIndent()

        val channels = parseM3uPlaylist(payload)
        assertEquals(1, channels.size)

        val ch = channels.first()
        assertEquals("Discovery Channel", ch.name)
        assertEquals("widevine", ch.drmType)
        assertEquals("https://drm.example.com/key", ch.drmKey)
        assertEquals("m3u8", ch.streamType)
    }

    @Test
    fun parseM3uPlaylist_extGrpAndNoiseDividerIgnored() {
        val payload = """
            #EXTM3U
            ====================================
            //==================== INDONESIA ====================
            <===================================>
            ------------------------------------
            🔸🔸🔸 HD CHANNELS 🔸🔸🔸
            ====================================
            #EXTINF:-1 tvg-name="Trans TV", Trans TV
            #EXTGRP: National
            https://stream.example.com/transtv.m3u8
        """.trimIndent()

        val channels = parseM3uPlaylist(payload)
        assertEquals(1, channels.size)

        val ch = channels.first()
        assertEquals("Trans TV", ch.name)
        assertEquals("National", ch.group)
        assertEquals("https://stream.example.com/transtv.m3u8", ch.streamUrl)
    }

    @Test
    fun parseM3uPlaylist_extVlcOptAndExtHttp() {
        val payload = """
            #EXTM3U
            #EXTVLCOPT:http-user-agent=VLC/3.0
            #EXTVLCOPT:http-referrer=https://referrer.example.com
            #EXTINF:-1, Test Channel
            https://stream.example.com/test.m3u8
        """.trimIndent()

        val channels = parseM3uPlaylist(payload)
        assertEquals(1, channels.size)

        val ch = channels.first()
        assertEquals("VLC/3.0", ch.headers["User-Agent"])
        assertEquals("https://referrer.example.com", ch.headers["Referer"])
    }

    @Test
    fun parseM3uPlaylist_playlistLevelUserAgentInheritance() {
        val payload = """
            #EXTM3U
            #EXTINF:-1 tvg-id="update", Update
            #EXTVLCOPT:http-user-agent=Dalvik/2.1.0
            https://example.com/update.mp4
            #EXTINF:-1 tvg-id="VTV2" group-title="Dự phòng", VTV2
            #KODIPROP:inputstream.adaptive.manifest_type=mpd
            #KODIPROP:inputstream.adaptive.license_type=widevine
            #KODIPROP:inputstream.adaptive.license_key=https://tv.vietanhtv.top/mytv2/key.php
            https://s7485.cdn.mytvnet.vn/pkg20/manifest.mpd
        """.trimIndent()

        val channels = parseM3uPlaylist(payload)
        assertEquals(2, channels.size)

        val vtv2 = channels[1]
        assertEquals("VTV2", vtv2.name)
        assertEquals("Dalvik/2.1.0", vtv2.headers["User-Agent"])
        assertEquals("widevine", vtv2.drmType)
        assertEquals("https://tv.vietanhtv.top/mytv2/key.php", vtv2.drmKey)
    }
}

