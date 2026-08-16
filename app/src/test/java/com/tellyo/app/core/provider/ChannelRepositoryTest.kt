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
    fun repositoryAggregatesChannelsFromRegisteredProviders() = runTest {
        val repository = DefaultChannelRepository(
            ProviderRegistry(mapOf("preview" to PreviewTvProvider())),
        )

        val channels = repository.getChannels()

        assertEquals(PreviewTvProvider.previewChannels, channels)
    }

    @Test
    fun repositoryReturnsUnavailableForAnUnknownProvider() = runTest {
        val repository = DefaultChannelRepository(ProviderRegistry())
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
