package com.tellyo.app.core.provider

import com.tellyo.app.core.model.StreamResolution
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class Ta3TvProviderTest {
    @Test
    fun exposesTa3FromTheSharedCatalogue() = runTest {
        val channels = Ta3TvProvider(
            InMemoryChannelCatalogRepository(ChannelCatalogFixtures.ta3()),
        ).getChannels()

        assertEquals(listOf(Ta3TvProvider.TA3_ID), channels.map { it.id })
    }

    @Test
    fun keepsTheOfficialTa3DestinationInTheProviderBoundary() = runTest {
        assertEquals("https://www.ta3.com/live", Ta3TvProvider.OFFICIAL_LIVE_URL)
    }

    @Test
    fun livePlayerOpensInsideTellyo() = runTest {
        val provider = Ta3TvProvider(InMemoryChannelCatalogRepository(ChannelCatalogFixtures.ta3()))
        assertEquals(StreamResolution.OfficialEmbed(Ta3TvProvider.OFFICIAL_LIVE_URL),
            provider.resolveStream(provider.getChannels().single()))
    }
}
