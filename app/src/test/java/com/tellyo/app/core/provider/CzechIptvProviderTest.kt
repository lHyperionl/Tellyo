package com.tellyo.app.core.provider

import com.tellyo.app.core.model.StreamResolution
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CzechIptvProviderTest {
    private fun bundled(file: String, providerId: String = SlovakIptvProvider.PROVIDER_ID): List<M3uChannel> {
        val path = File("src/main/assets/$file").let {
            if (it.exists()) it else File("app/src/main/assets/$file")
        }
        return M3uPlaylistParser.parse(path.readText(), providerId)
    }

    private fun provider(czech: List<M3uChannel>, slovak: List<M3uChannel>): CzechIptvProvider =
        CzechIptvProvider(object : CzechPlaylistRepository {
            override suspend fun getEntries() = czech
        }, object : SlovakPlaylistRepository {
            override suspend fun getEntries() = slovak
        })

    @Test
    fun addsRequestedFeedsWithoutDuplicatingExistingChannels() = runTest {
        val czech = bundled("czech-iptv.m3u", CzechIptvProvider.PROVIDER_ID)
        val slovak = bundled("slovak-iptv.m3u")
        val channels = provider(czech, slovak).getChannels()
        assertEquals(50, czech.size)
        assertEquals(46, channels.size)
        assertEquals("ČT1", channels.first().name)
        assertEquals("ČT Déčko", channels[3].name)
        assertEquals("Óčko", channels[23].name)
        assertTrue(channels.all { it.providerId == "czech-iptv" })
        assertEquals(channels.size, channels.map { it.number }.distinct().size)
        val existingIds = slovak.map { it.channel.id }.toSet()
        assertFalse(channels.any { it.id in existingIds })
        assertEquals(2, channels.count { it.id.startsWith("iptv-AXNWhite.us@") })
        assertTrue(channels.any { it.id == "iptv-DisneyChannel.cz" })
        assertFalse(channels.any { it.id == "iptv-DisneyChannel.hu" })
        assertFalse(channels.any { it.id == "iptv-Spektrum.cz" })
        assertFalse(channels.any { it.id == "iptv-SpektrumHome.cz" })
        assertFalse(channels.any { it.id == "iptv-CanalPlusActionEurope.nl" })
    }

    @Test
    fun missingSlovakDuplicatesUseCzechFeedsWithTheSameStableIdsAndNumbers() = runTest {
        val czech = bundled("czech-iptv.m3u", CzechIptvProvider.PROVIDER_ID)
        val withoutDuplicates = provider(czech, bundled("slovak-iptv.m3u")).getChannels()
        val fallback = provider(czech, emptyList()).getChannels()
        assertEquals(49, fallback.size)
        assertTrue(fallback.any { it.id == "iptv-Spektrum.cz" })
        assertEquals(withoutDuplicates.map { it.id to it.number },
            fallback.filter { channel -> channel.id in withoutDuplicates.map { it.id } }
                .map { it.id to it.number })
    }

    @Test
    fun rejectsForeignDisabledAndUnknownChannels() = runTest {
        val czech = bundled("czech-iptv.m3u", CzechIptvProvider.PROVIDER_ID)
        val provider = provider(czech, emptyList())
        val channel = provider.getChannels().first()
        assertTrue(provider.resolveStream(channel.copy(providerId = "slovak-iptv")) is StreamResolution.Unavailable)
        assertTrue(provider.resolveStream(channel.copy(enabled = false)) is StreamResolution.Unavailable)
        assertTrue(provider.resolveStream(channel.copy(id = "iptv-Unknown.cz")) is StreamResolution.Unavailable)
        val removed = czech.single { it.channel.id == "iptv-DisneyChannel.hu@SD" }.channel
        assertTrue(provider.resolveStream(removed) is StreamResolution.Unavailable)
        assertTrue(provider.resolveStream(removed.copy(id = "iptv-DisneyChannel.hu")) is StreamResolution.Unavailable)
        assertTrue(provider.resolveStream(removed.copy(id = "iptv-DisneyChannel.hu@HD")) is StreamResolution.Unavailable)
    }

    @Test
    fun childrensChannelsFollowSport2InTheCombinedLineup() = runTest {
        val slovak = bundled("slovak-iptv.m3u")
        val slovakProvider = SlovakIptvProvider(object : SlovakPlaylistRepository {
            override suspend fun getEntries() = slovak
        })
        val czechProvider = provider(bundled("czech-iptv.m3u", CzechIptvProvider.PROVIDER_ID), slovak)
        val repository = DefaultChannelRepository(
            ProviderRegistry(mapOf(slovakProvider.id to slovakProvider, czechProvider.id to czechProvider)),
            InMemoryChannelCatalogRepository(emptyList()),
        )
        val channels = repository.getChannels()
        assertEquals(64, channels.size)
        val sport2 = channels.indexOfFirst { it.id == "iptv-Sport2.cz" }
        assertEquals(listOf("Disney Channel (CZ)", "Disney Junior", "Minimax CEE", "Nick Jr.", "Nickelodeon", "Nicktoons"),
            channels.drop(sport2 + 1).take(6).map { it.name })
        assertEquals((25..30).toList(), channels.drop(sport2 + 1).take(6).map { it.number })
    }

    @Test
    fun refreshOnlyChangesSelectedStreamsAndPreservesNamesOrderAndMissingFeeds() {
        val bundled = bundled("czech-iptv.m3u", CzechIptvProvider.PROVIDER_ID)
        val result = CzechPlaylistCatalog(bundled).merge("""
            #EXTM3U
            #EXTINF:-1 tvg-id="Unrequested.cz@SD",Unrequested
            https://example.com/extra.m3u8
            #EXTINF:-1 tvg-id="CT1.cz@SD" http-user-agent="Updated Agent",Changed upstream name
            https://example.com/updated.m3u8
        """.trimIndent())
        assertEquals(bundled.map { it.channel }, result.map { it.channel })
        assertEquals("https://example.com/updated.m3u8", result.first().url)
        assertEquals("Updated Agent", result.first().headers["User-Agent"])
        assertEquals(bundled.drop(1), result.drop(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRefreshesWithNoSelectedFeeds() {
        CzechPlaylistCatalog(bundled("czech-iptv.m3u", CzechIptvProvider.PROVIDER_ID)).merge("""
            #EXTM3U
            #EXTINF:-1 tvg-id="Unrequested.cz@SD",Unrequested
            https://example.com/extra.m3u8
        """.trimIndent())
    }
}
