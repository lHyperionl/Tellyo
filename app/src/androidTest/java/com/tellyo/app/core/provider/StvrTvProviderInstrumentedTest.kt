package com.tellyo.app.core.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tellyo.app.core.model.StreamSource
import com.tellyo.app.core.model.StreamResolution
import com.tellyo.app.core.model.StreamType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class StvrTvProviderInstrumentedTest {
    @Test
    fun providerUsesPlayableOfficialSourceBoundary() = runTest {
        val channel = com.tellyo.app.core.model.Channel(
            id = StvrTvProvider.JEDNOTKA_ID,
            number = 1,
            name = "Jednotka",
            shortName = "Jednotka",
            logoUrl = null,
            providerId = StvrTvProvider.PROVIDER_ID,
            category = com.tellyo.app.core.model.ChannelCategory.GENERAL,
            enabled = true,
        )
        val result = StvrTvProvider(
            FakeStvrOfficialApi(),
            InMemoryChannelCatalogRepository(listOf(channel)),
        ).resolveStream(channel)

        assertTrue(result is StreamResolution.Playable)
        result as StreamResolution.Playable
        assertEquals(StreamType.HLS, result.source.type)
        assertEquals("https://example.invalid/stvr.m3u8", result.source.uri.toString())
    }

    private class FakeStvrOfficialApi : StvrOfficialApi {
        override suspend fun fetchLive(channel: com.tellyo.app.core.model.Channel): StvrLiveData =
            StvrLiveData(
                programTitle = "Test programme",
                description = null,
                start = Instant.parse("2026-08-15T16:00:00Z"),
                end = Instant.parse("2026-08-15T17:00:00Z"),
                sources = listOf(
                    StreamSource(
                        channelId = channel.id,
                        uri = android.net.Uri.parse("https://example.invalid/stvr.m3u8"),
                        type = StreamType.HLS,
                    ),
                ),
            )
    }
}
