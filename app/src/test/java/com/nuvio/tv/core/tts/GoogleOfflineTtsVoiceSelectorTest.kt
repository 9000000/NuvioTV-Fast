package com.nuvio.tv.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleOfflineTtsVoiceSelectorTest {

    private val sampleVoices = listOf(
        RawSystemVoice(
            name = "en-us-x-sfg-local",
            localeTag = "en-US",
            language = "en",
            country = "US",
            requiresNetwork = false,
        ),
        RawSystemVoice(
            name = "vi-vn-x-gft-network",
            localeTag = "vi-VN",
            language = "vi",
            country = "VN",
            requiresNetwork = true,
        ),
        RawSystemVoice(
            name = "vi-vn-x-vic-local",
            localeTag = "vi-VN",
            language = "vi",
            country = "VN",
            requiresNetwork = false,
        ),
        RawSystemVoice(
            name = "ja-jp-x-jab-network",
            localeTag = "ja-JP",
            language = "ja",
            country = "JP",
            requiresNetwork = true,
        ),
        RawSystemVoice(
            name = "vi-vn-x-gft-local",
            localeTag = "vi-VN",
            language = "vi",
            country = "VN",
            requiresNetwork = false,
        ),
    )

    @Test
    fun selectAvailableVoicesFiltersOutNetworkVoicesWhenFallbackDisabled() {
        val offlineOnly = GoogleOfflineTtsVoiceSelector.selectAvailableVoices(
            rawVoices = sampleVoices,
            allowNetworkFallback = false,
        )

        assertEquals(3, offlineOnly.size)
        assertTrue(offlineOnly.all { it.isOffline })
        assertFalse(offlineOnly.any { it.name.contains("network") })

        // Vietnamese offline voices sorted first alphabetically, followed by non-Vietnamese offline voices
        assertEquals(
            listOf("vi-vn-x-gft-local", "vi-vn-x-vic-local", "en-us-x-sfg-local"),
            offlineOnly.map { it.name },
        )
        assertTrue(offlineOnly.first().displayName.contains("(Offline)"))
    }

    @Test
    fun selectAvailableVoicesIncludesNetworkAfterOfflineWithinLocalePriorityWhenFallbackEnabled() {
        val withFallback = GoogleOfflineTtsVoiceSelector.selectAvailableVoices(
            rawVoices = sampleVoices,
            allowNetworkFallback = true,
        )

        assertEquals(5, withFallback.size)
        assertEquals(
            listOf(
                "vi-vn-x-gft-local",
                "vi-vn-x-vic-local",
                "vi-vn-x-gft-network",
                "en-us-x-sfg-local",
                "ja-jp-x-jab-network",
            ),
            withFallback.map { it.name },
        )
        assertTrue(withFallback[2].displayName.contains("(Online)"))
        assertFalse(withFallback[2].isOffline)
    }

    @Test
    fun pickBestVoiceFollowsPreferredThenViOfflineThenOfflineThenFirstAvailable() {
        val filtered = GoogleOfflineTtsVoiceSelector.selectAvailableVoices(
            rawVoices = sampleVoices,
            allowNetworkFallback = false,
        )

        // Exact match on preferred voice
        assertEquals(
            "en-us-x-sfg-local",
            GoogleOfflineTtsVoiceSelector.pickBestVoice(filtered, "en-us-x-sfg-local")?.name,
        )

        // Preferred voice unavailable (network voice filtered out) -> falls back to first vi offline
        assertEquals(
            "vi-vn-x-gft-local",
            GoogleOfflineTtsVoiceSelector.pickBestVoice(filtered, "vi-vn-x-gft-network")?.name,
        )

        // Null preferred -> first vi offline
        assertEquals(
            "vi-vn-x-gft-local",
            GoogleOfflineTtsVoiceSelector.pickBestVoice(filtered, null)?.name,
        )

        // No Vietnamese offline voice -> falls back to first offline voice
        val enOnlyOffline = filtered.filter { it.localeTag == "en-US" }
        assertEquals(
            "en-us-x-sfg-local",
            GoogleOfflineTtsVoiceSelector.pickBestVoice(enOnlyOffline, null)?.name,
        )

        // Only online voices (when fallback enabled) -> falls back to first available
        val onlineOnly = GoogleOfflineTtsVoiceSelector.selectAvailableVoices(
            rawVoices = sampleVoices.filter { it.requiresNetwork },
            allowNetworkFallback = true,
        )
        assertEquals(
            "vi-vn-x-gft-network",
            GoogleOfflineTtsVoiceSelector.pickBestVoice(onlineOnly, null)?.name,
        )

        assertNull(GoogleOfflineTtsVoiceSelector.pickBestVoice(emptyList(), "vi-vn-x-gft-local"))
    }

    @Test
    fun selectAvailableVoicesFiltersStrictlyToPreferredLanguage() {
        val viOnlyOffline = GoogleOfflineTtsVoiceSelector.selectAvailableVoices(
            rawVoices = sampleVoices,
            allowNetworkFallback = false,
            preferredLanguageCode = "vi",
        )
        assertEquals(listOf("vi-vn-x-gft-local", "vi-vn-x-vic-local"), viOnlyOffline.map { it.name })

        val enOnlyOffline = GoogleOfflineTtsVoiceSelector.selectAvailableVoices(
            rawVoices = sampleVoices,
            allowNetworkFallback = false,
            preferredLanguageCode = "en",
        )
        assertEquals(listOf("en-us-x-sfg-local"), enOnlyOffline.map { it.name })

        val jaWithFallback = GoogleOfflineTtsVoiceSelector.selectAvailableVoices(
            rawVoices = sampleVoices,
            allowNetworkFallback = true,
            preferredLanguageCode = "ja",
        )
        assertEquals(listOf("ja-jp-x-jab-network"), jaWithFallback.map { it.name })

        val regionalChineseVoices = listOf(
            RawSystemVoice("cmn-tw-x-ctc-local", "zh-TW", "zh", "TW", requiresNetwork = false),
            RawSystemVoice("cmn-cn-x-ccc-local", "zh-CN", "zh", "CN", requiresNetwork = false),
            RawSystemVoice("vi-vn-x-gft-local", "vi-VN", "vi", "VN", requiresNetwork = false),
        )
        val zhCnFiltered = GoogleOfflineTtsVoiceSelector.selectAvailableVoices(
            rawVoices = regionalChineseVoices,
            allowNetworkFallback = false,
            preferredLanguageCode = "zh-CN",
        )
        assertEquals(listOf("cmn-cn-x-ccc-local", "cmn-tw-x-ctc-local"), zhCnFiltered.map { it.name })
    }

    @Test
    fun resolvePreferredLanguageCodeUsesSubtitleLanguageAndFallsBackToVietnamese() {
        assertEquals("vi", GoogleOfflineTtsVoiceSelector.resolvePreferredLanguageCode(null))
        assertEquals("vi", GoogleOfflineTtsVoiceSelector.resolvePreferredLanguageCode(""))
        assertEquals("vi", GoogleOfflineTtsVoiceSelector.resolvePreferredLanguageCode("forced"))
        assertEquals("vi", GoogleOfflineTtsVoiceSelector.resolvePreferredLanguageCode("none"))
        assertEquals("vi", GoogleOfflineTtsVoiceSelector.resolvePreferredLanguageCode("device"))
        assertEquals("en", GoogleOfflineTtsVoiceSelector.resolvePreferredLanguageCode("forced", "en"))
        assertEquals("vi", GoogleOfflineTtsVoiceSelector.resolvePreferredLanguageCode("vi"))
        assertEquals("zh-CN", GoogleOfflineTtsVoiceSelector.resolvePreferredLanguageCode("zh_CN"))
    }
}
