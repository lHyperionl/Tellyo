package com.tellyo.app.core.provider

import android.net.Uri
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.StreamResolution
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {
    @Test
    fun providerRegistryReturnsProviderByStableId() {
        val provider = FakeProvider("preview")
        val registry = ProviderRegistry(mapOf(provider.id to provider))

        assertEquals(provider, registry.providerFor("preview"))
        assertEquals(1, registry.all().size)
    }

    @Test
    fun providerBoundaryCanReturnUnavailableWithoutAStreamUrl() = runTest {
        val provider = FakeProvider("preview")
        val resolution = provider.resolveStream(provider.channels.first())

        assertTrue(resolution is StreamResolution.Unavailable)
    }

    private class FakeProvider(override val id: String) : TvProvider {
        val channels = listOf(
            Channel(
                id = "preview-1",
                number = 1,
                name = "Preview",
                shortName = "Preview",
                logoUrl = null,
                providerId = id,
                category = ChannelCategory.GENERAL,
                enabled = true,
            ),
        )

        override suspend fun getChannels(): List<Channel> = channels

        override suspend fun resolveStream(channel: Channel): StreamResolution =
            StreamResolution.Unavailable("Preview provider contains no real streams")
    }
}
