package com.tellyo.app.core.provider

import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.StreamResolution
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelRepositoryTest {
    @Test
    fun explicitlyDisabledIptvProviderDoesNotFallBackToEnabledPlaylist() = runTest {
        val channel = Channel("iptv-review", 100, "Review", "Review", null,
            SlovakIptvProvider.PROVIDER_ID, ChannelCategory.GENERAL, true)
        val provider = SlovakIptvProvider(object : SlovakPlaylistRepository {
            override suspend fun getEntries() = listOf(
                M3uChannel(channel, "https://example.com/live.m3u8",
                    com.tellyo.app.core.model.StreamType.HLS, emptyMap()),
            )
        })
        val registry = ProviderRegistry(mapOf(provider.id to provider))
        assertTrue(DefaultChannelRepository(registry,
            InMemoryChannelCatalogRepository(listOf(channel.copy(enabled = false))))
            .getChannels().isEmpty())
        // An unconfigured dynamic provider must still expose its playlist.
        assertEquals(listOf(channel), DefaultChannelRepository(registry,
            InMemoryChannelCatalogRepository(emptyList())).getChannels())
    }

    @Test
    fun repositoryAggregatesChannelsFromRegisteredProviders() = runTest {
        val repository = DefaultChannelRepository(
            ProviderRegistry(mapOf("preview" to PreviewTvProvider())),
            InMemoryChannelCatalogRepository(PreviewTvProvider.previewChannels),
        )

        val channels = repository.getChannels()

        assertEquals(PreviewTvProvider.previewChannels, channels)
    }

    @Test
    fun repositoryReturnsUnavailableForAnUnknownProvider() = runTest {
        val repository = DefaultChannelRepository(
            ProviderRegistry(),
            InMemoryChannelCatalogRepository(emptyList()),
        )
        val channel = Channel(
            id = "unknown-channel",
            number = 99,
            providerId = "missing-provider",
            name = "Unknown",
            shortName = "UNKNOWN",
            logoUrl = null,
            category = ChannelCategory.GENERAL,
            enabled = true,
        )

        val result = repository.resolveStream(channel)

        assertTrue(result is StreamResolution.Unavailable)
    }
}
