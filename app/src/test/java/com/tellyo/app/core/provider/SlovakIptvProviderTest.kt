package com.tellyo.app.core.provider

import com.tellyo.app.core.model.StreamResolution
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlovakIptvProviderTest {
    @Test
    fun retainsEighteenChannelsAndPreservesTheExistingStvrRoutes() = runTest {
        val file = File("src/main/assets/slovak-iptv.m3u").let {
            if (it.exists()) it else File("app/src/main/assets/slovak-iptv.m3u")
        }
        val entries = M3uPlaylistParser.parse(file.readText())
        val provider = SlovakIptvProvider(object : SlovakPlaylistRepository {
            override suspend fun getEntries() = entries
        })
        val channels = provider.getChannels()
        assertEquals(18, channels.size)
        assertFalse(channels.any { it.id == "iptv-Jednotka.sk" })
        assertFalse(channels.any { it.id == "iptv-Dvojka.sk" })
        assertFalse(channels.any { it.id == "iptv-24.sk" })
        assertFalse(channels.any { it.id == "iptv-Sport.sk" })
        assertEquals(listOf("slovak-iptv"), channels.map { it.providerId }.distinct())
    }

    @Test
    fun removedChannelsCannotReturnOnRefreshOrResolveFromSavedSelections() = runTest {
        val file = File("src/main/assets/slovak-iptv.m3u").let {
            if (it.exists()) it else File("app/src/main/assets/slovak-iptv.m3u")
        }
        val entries = M3uPlaylistParser.parse(file.readText())
        // Original removed range plus the four subsequently removed channels.
        val removed = entries.filter { it.channel.number in 126..153 || it.channel.number in setOf(101, 111, 112, 113) }
        assertEquals(32, removed.size)
        var snapshot = entries - removed.toSet()
        val provider = SlovakIptvProvider(object : SlovakPlaylistRepository {
            override suspend fun getEntries() = snapshot
        })
        val before = provider.getChannels()
        snapshot = entries
        assertEquals(before, provider.getChannels())
        for (entry in removed) {
            assertTrue(provider.resolveStream(entry.channel) is StreamResolution.Unavailable)
        }
        // A new upstream quality suffix must not restore a removed channel either.
        snapshot = removed.map { it.copy(channel = it.channel.copy(id = "${it.channel.id}@HD")) }
        assertEquals(emptyList<com.tellyo.app.core.model.Channel>(), provider.getChannels())
        for (entry in snapshot) {
            assertTrue(provider.resolveStream(entry.channel) is StreamResolution.Unavailable)
        }
    }
}
