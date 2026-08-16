package com.tellyo.app.core.provider

import com.tellyo.app.core.model.StreamResolution
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTvProviderTest {
    @Test
    fun previewProviderExposesPreviewChannelsAndPublicHlsTestSource() = runTest {
        val provider = PreviewTvProvider()
        val channel = provider.getChannels().first()

        assertEquals("preview", channel.providerId)
        assertTrue(provider.getChannels().size >= 5)
        assertTrue(PreviewTvProvider.TEST_HLS_URI.endsWith(".m3u8"))
    }

    @Test
    fun previewProviderRejectsChannelsOwnedByAnotherProvider() = runTest {
        val provider = PreviewTvProvider()
        val channel = provider.getChannels().first().copy(providerId = "other")

        assertTrue(provider.resolveStream(channel) is StreamResolution.Unavailable)
    }
}
