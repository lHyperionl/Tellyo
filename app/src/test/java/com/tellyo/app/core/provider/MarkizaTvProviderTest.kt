package com.tellyo.app.core.provider

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkizaTvProviderTest {
    @Test
    fun providerExposesMarkizaAndItsSubchannels() = runTest {
        val channels = MarkizaTvProvider().getChannels()

        assertEquals(5, channels.size)
        assertEquals("Doma", channels.first { it.id == MarkizaTvProvider.DOMA_ID }.name)
        assertEquals("Dajto", channels.first { it.id == MarkizaTvProvider.DAJTO_ID }.name)
        assertTrue(channels.all { it.providerId == MarkizaTvProvider.PROVIDER_ID && it.enabled })
    }

    @Test
    fun providerKeepsOfficialDestinationMappingOutsideTheUi() {
        val channel = MarkizaTvProvider.channels.first { it.id == MarkizaTvProvider.DAJTO_ID }

        assertEquals("https://www.markiza.sk/live/2-dajto", MarkizaTvProvider.officialLiveUrl(channel))
    }
}
