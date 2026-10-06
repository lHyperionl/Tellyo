package com.tellyo.app.core.provider

import kotlinx.coroutines.test.runTest
import com.tellyo.app.core.model.StreamResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JojTvProviderTest {
    @Test
    fun providerExposesTheCurrentJojGroupCatalogue() = runTest {
        val channels = JojTvProvider(
            InMemoryChannelCatalogRepository(ChannelCatalogFixtures.joj()),
        ).getChannels()

        assertEquals(4, channels.size)
        assertEquals("JOJ Plus", channels.first { it.id == JojTvProvider.PLUS_ID }.name)
        assertEquals("JOJ Krimi (WAU)", channels.first { it.id == JojTvProvider.KRIMI_ID }.name)
        assertTrue(channels.all { it.providerId == JojTvProvider.PROVIDER_ID && it.enabled })
    }

    @Test
    fun providerKeepsOfficialDestinationMappingOutsideTheUi() {
        val channel = ChannelCatalogFixtures.joj().first { it.id == JojTvProvider.PLUS_ID }

        assertEquals("https://www.joj.sk/live/2-plus", JojTvProvider.officialLiveUrl(channel))
    }

    @Test
    fun everyJojChannelHasItsOwnInAppOfficialPlayer() = runTest {
        val channels = ChannelCatalogFixtures.joj()
        val provider = JojTvProvider(InMemoryChannelCatalogRepository(channels))
        val destinations = channels.map { channel ->
            val resolution = provider.resolveStream(channel)
            assertTrue("${channel.name} must stay inside Tellyo", resolution is StreamResolution.OfficialEmbed)
            (resolution as StreamResolution.OfficialEmbed).url
        }
        assertEquals(4, destinations.toSet().size)
        assertTrue(destinations.contains(JojTvProvider.NEWS_24_EMBED_URL))
    }
}
