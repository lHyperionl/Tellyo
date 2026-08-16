package com.tellyo.app.core.provider

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JojTvProviderTest {
    @Test
    fun providerExposesTheCurrentJojGroupCatalogue() = runTest {
        val channels = JojTvProvider().getChannels()

        assertEquals(8, channels.size)
        assertEquals("JOJ Plus", channels.first { it.id == JojTvProvider.PLUS_ID }.name)
        assertEquals("JOJ Krimi (WAU)", channels.first { it.id == JojTvProvider.KRIMI_ID }.name)
        assertTrue(channels.all { it.providerId == JojTvProvider.PROVIDER_ID && it.enabled })
    }

    @Test
    fun providerKeepsOfficialDestinationMappingOutsideTheUi() {
        val channel = JojTvProvider.channels.first { it.id == JojTvProvider.PLUS_ID }

        assertEquals("https://www.joj.sk/live/1-jojplus", JojTvProvider.officialLiveUrl(channel))
    }
}
