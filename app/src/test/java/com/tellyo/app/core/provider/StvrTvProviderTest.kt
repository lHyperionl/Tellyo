package com.tellyo.app.core.provider

import com.tellyo.app.core.model.StreamResolution
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StvrTvProviderTest {
    @Test
    fun providerExposesOfficialStvrChannels() = runTest {
        val channels = StvrTvProvider(FakeStvrOfficialApi()).getChannels()

        assertEquals(StvrTvProvider.PROVIDER_ID, channels.first().providerId)
        assertEquals(4, channels.size)
        assertEquals("Jednotka", channels.first().name)
    }

    @Test
    fun providerRejectsChannelsOwnedByAnotherProvider() = runTest {
        val channel = StvrTvProvider.channels.first().copy(providerId = "other")

        assertTrue(StvrTvProvider(FakeStvrOfficialApi()).resolveStream(channel) is StreamResolution.Unavailable)
    }

    private class FakeStvrOfficialApi : StvrOfficialApi {
        override suspend fun fetchLive(channel: com.tellyo.app.core.model.Channel): StvrLiveData =
            StvrLiveData(
                programTitle = null,
                description = null,
                start = null,
                end = null,
                sources = emptyList(),
            )
    }
}
