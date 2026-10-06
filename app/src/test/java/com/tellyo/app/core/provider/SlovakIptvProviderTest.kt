package com.tellyo.app.core.provider

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SlovakIptvProviderTest {
    @Test
    fun addsFiftyChannelsAndPreservesTheExistingStvrRoutes() = runTest {
        val file = File("src/main/assets/slovak-iptv.m3u").let {
            if (it.exists()) it else File("app/src/main/assets/slovak-iptv.m3u")
        }
        val entries = M3uPlaylistParser.parse(file.readText())
        val provider = SlovakIptvProvider(object : SlovakPlaylistRepository {
            override suspend fun getEntries() = entries
        })
        val channels = provider.getChannels()
        assertEquals(50, channels.size)
        assertFalse(channels.any { it.id == "iptv-Jednotka.sk" })
        assertFalse(channels.any { it.id == "iptv-Dvojka.sk" })
        assertFalse(channels.any { it.id == "iptv-24.sk" })
        assertFalse(channels.any { it.id == "iptv-Sport.sk" })
        assertEquals(listOf("slovak-iptv"), channels.map { it.providerId }.distinct())
    }
}
