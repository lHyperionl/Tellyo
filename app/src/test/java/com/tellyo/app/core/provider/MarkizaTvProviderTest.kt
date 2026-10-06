package com.tellyo.app.core.provider

import com.tellyo.app.core.model.StreamResolution
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkizaTvProviderTest {
    @Test
    fun providerExposesMarkizaAndItsSubchannels() = runTest {
        val channels = MarkizaTvProvider(
            InMemoryChannelCatalogRepository(ChannelCatalogFixtures.markiza()),
        ).getChannels()

        assertEquals(5, channels.size)
        assertEquals("Doma", channels.first { it.id == MarkizaTvProvider.DOMA_ID }.name)
        assertEquals("Dajto", channels.first { it.id == MarkizaTvProvider.DAJTO_ID }.name)
        assertTrue(channels.all { it.providerId == MarkizaTvProvider.PROVIDER_ID && it.enabled })
    }

    @Test
    fun providerKeepsOfficialDestinationMappingOutsideTheUi() {
        val channel = ChannelCatalogFixtures.markiza().first { it.id == MarkizaTvProvider.DAJTO_ID }

        assertEquals("https://www.markiza.sk/live/2-dajto", MarkizaTvProvider.officialLiveUrl(channel))
    }

    @Test
    fun mainChannelUsesPublishedOfficialEmbed() = runTest {
        val provider = MarkizaTvProvider(
            InMemoryChannelCatalogRepository(ChannelCatalogFixtures.markiza()),
        )
        val channel = provider.getChannels().first { it.id == MarkizaTvProvider.MARKIZA_ID }

        val resolution = provider.resolveStream(channel)

        assertTrue(resolution is StreamResolution.OfficialEmbed)
        assertEquals(
            MarkizaTvProvider.MARKIZA_EMBED_URL,
            (resolution as StreamResolution.OfficialEmbed).url,
        )
    }

    @Test
    fun domaUsesPublishedOfficialEmbed() = runTest {
        val provider = MarkizaTvProvider(
            InMemoryChannelCatalogRepository(ChannelCatalogFixtures.markiza()),
        )
        val channel = provider.getChannels().first { it.id == MarkizaTvProvider.DOMA_ID }

        val resolution = provider.resolveStream(channel)

        assertTrue(resolution is StreamResolution.OfficialEmbed)
        assertEquals(
            MarkizaTvProvider.DOMA_EMBED_URL,
            (resolution as StreamResolution.OfficialEmbed).url,
        )
    }

    @Test
    fun addedChannelsUseTheirOwnPublishedPlayers() = runTest {
        val provider = MarkizaTvProvider(InMemoryChannelCatalogRepository(ChannelCatalogFixtures.markiza()))
        val expected = mapOf(
            MarkizaTvProvider.DAJTO_ID to MarkizaTvProvider.DAJTO_EMBED_URL,
            MarkizaTvProvider.KRIMI_ID to MarkizaTvProvider.KRIMI_EMBED_URL,
            MarkizaTvProvider.KLASIK_ID to MarkizaTvProvider.KLASIK_EMBED_URL,
        )
        expected.forEach { (id, url) ->
            val channel = provider.getChannels().single { it.id == id }
            assertEquals(StreamResolution.OfficialEmbed(url), provider.resolveStream(channel))
        }
        val klasik = provider.getChannels().single { it.id == MarkizaTvProvider.KLASIK_ID }
        assertEquals("https://www.markiza.sk/live/44-klasik", MarkizaTvProvider.officialLiveUrl(klasik))
        assertEquals(3, expected.values.toSet().size)
    }
}
