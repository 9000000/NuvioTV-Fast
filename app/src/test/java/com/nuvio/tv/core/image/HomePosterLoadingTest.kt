package com.nuvio.tv.core.image

import android.app.ActivityManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePosterLoadingTest {

    private val gib = 1024L * 1024L * 1024L

    @Test
    fun `forDevice returns low-RAM profile for 0L, 1_5GB, and 2_0GB`() {
        val expectedLowRam = HomeImageLoadingProfile(
            isLowRam = true,
            okHttpMaxRequests = 32,
            okHttpMaxRequestsPerHost = 16,
            connectionPoolMaxIdle = 12,
            bitmapDecodeParallelism = 4,
            catalogConcurrency = 4,
            eagerCatalogRows = 4,
            dripAheadRows = 3,
            lazyPrefetchAheadModern = 3,
            lazyPrefetchAheadClassic = 2,
            verticalPrefetchRows = 2,
            verticalPrefetchItems = 6,
            horizontalPrefetchAhead = 4,
            horizontalPrefetchBehind = 2
        )

        val inputs = listOf(
            0L,
            (1.5 * gib).toLong(),
            2L * gib
        )

        for (ramBytes in inputs) {
            val profile = HomeImageLoadingProfile.forDevice(ramBytes)
            assertTrue("Expected low-RAM profile for $ramBytes bytes", profile.isLowRam)
            assertEquals(expectedLowRam, profile)
        }
    }

    @Test
    fun `forDevice returns standard profile for 3_0GB and 4_0GB plus`() {
        val expectedStandard = HomeImageLoadingProfile(
            isLowRam = false,
            okHttpMaxRequests = 48,
            okHttpMaxRequestsPerHost = 32,
            connectionPoolMaxIdle = 20,
            bitmapDecodeParallelism = 6,
            catalogConcurrency = 6,
            eagerCatalogRows = 6,
            dripAheadRows = 6,
            lazyPrefetchAheadModern = 4,
            lazyPrefetchAheadClassic = 3,
            verticalPrefetchRows = 3,
            verticalPrefetchItems = 8,
            horizontalPrefetchAhead = 6,
            horizontalPrefetchBehind = 2
        )

        val inputs = listOf(
            3L * gib,
            4L * gib,
            8L * gib
        )

        for (ramBytes in inputs) {
            val profile = HomeImageLoadingProfile.forDevice(ramBytes)
            assertFalse("Expected standard profile for $ramBytes bytes", profile.isLowRam)
            assertEquals(expectedStandard, profile)
        }
    }

    @Test
    fun `forContext reads ActivityManager memory safely and falls back to low-RAM on error`() {
        val failingContext = mockk<Context>()
        every { failingContext.getSystemService(Context.ACTIVITY_SERVICE) } throws SecurityException("denied")
        val fallbackProfile = HomeImageLoadingProfile.forContext(failingContext)
        assertTrue(fallbackProfile.isLowRam)
        assertEquals(HomeImageLoadingProfile.forDevice(0L), fallbackProfile)

        val highRamAm = mockk<ActivityManager>()
        every { highRamAm.getMemoryInfo(any()) } answers {
            firstArg<ActivityManager.MemoryInfo>().totalMem = 4L * gib
        }
        val highRamContext = mockk<Context>()
        every { highRamContext.getSystemService(Context.ACTIVITY_SERVICE) } returns highRamAm
        val highRamProfile = HomeImageLoadingProfile.forContext(highRamContext)
        assertFalse(highRamProfile.isLowRam)
        assertEquals(HomeImageLoadingProfile.forDevice(4L * gib), highRamProfile)
    }

    @Test
    fun `existing HomePosterLoading helpers keep expected behavior`() {
        assertEquals(4, HOME_POSTER_BITMAP_PARALLELISM)
        assertEquals(
            "https://image.tmdb.org/t/p/w342/poster.jpg",
            sizedHomeArtworkUrl("https://image.tmdb.org/t/p/original/poster.jpg", width = 240, height = 360)
        )
        assertEquals(10L * 1024 * 1024, homeArtworkMemoryCacheBytes(10L * 1024 * 1024, 2L * gib))
    }
}
