package com.tellyo.app.core.epg

import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.provider.StvrLiveData
import com.tellyo.app.core.provider.StvrOfficialApi
import com.tellyo.app.core.provider.StvrScheduleProgramme
import com.tellyo.app.core.provider.StvrTvProvider
import com.tellyo.app.core.provider.ChannelCatalogFixtures
import com.tellyo.app.core.provider.InMemoryChannelCatalogRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StvrEpgProviderTest {
    @Test
    fun scheduleIsCachedWhileLiveOnlyFetchDeclaresIncrementalCoverage() = runTest {
        var scheduleCalls = 0
        var liveCalls = 0
        val now = Instant.now()
        val api = object : StvrOfficialApi {
            override suspend fun fetchSchedule(from: java.time.LocalDate, days: Int): List<StvrScheduleProgramme> {
                scheduleCalls++
                return listOf(StvrScheduleProgramme(StvrTvProvider.JEDNOTKA_ID, "Cached", null, null, now, null))
            }
            override suspend fun fetchLive(channel: Channel): StvrLiveData {
                liveCalls++
                return StvrLiveData("Live", null, now, now.plusSeconds(3600), emptyList())
            }
        }
        val catalog = InMemoryChannelCatalogRepository(ChannelCatalogFixtures.stvr())
        val provider = StvrEpgProvider(api, catalog)
        provider.fetch()
        provider.fetch()
        assertEquals(1, scheduleCalls)
        assertEquals(8, liveCalls)
        val liveOnly = StvrEpgProvider(object : StvrOfficialApi {
            override suspend fun fetchLive(channel: Channel) = api.fetchLive(channel)
        }, catalog).fetch()
        assertTrue(liveOnly.replacementWindows!!.isEmpty())
        assertTrue(XmlTvParser().parse(liveOnly.xml).isNotEmpty())
    }

    @Test
    fun exportsCurrentAndUpcomingProgrammes() = runTest {
        val currentStart = Instant.parse("2026-08-15T21:10:00Z")
        val currentEnd = Instant.parse("2026-08-15T22:38:31Z")
        val futureStart = Instant.parse("2026-08-15T22:40:00Z")
        val xml = StvrEpgProvider(
            FakeStvrOfficialApi(
                currentStart = currentStart,
                currentEnd = currentEnd,
                schedule = listOf(
                    StvrScheduleProgramme(
                        channelId = StvrTvProvider.JEDNOTKA_ID,
                        title = "Sieň slávy",
                        description = null,
                        category = "zábava",
                        start = currentStart,
                        artworkUrl = null,
                    ),
                    StvrScheduleProgramme(
                        channelId = StvrTvProvider.JEDNOTKA_ID,
                        title = "Hymna SR",
                        description = null,
                        category = "doplnkové vysielanie",
                        start = futureStart,
                        artworkUrl = null,
                    ),
                ),
            ),
            InMemoryChannelCatalogRepository(ChannelCatalogFixtures.stvr()),
        ).fetchXml()

        val programmes = XmlTvParser().parse(xml)
            .filter { it.channelId == StvrTvProvider.JEDNOTKA_ID }

        assertEquals(2, programmes.size)
        assertEquals("Sieň slávy", programmes[0].title)
        assertEquals("Hymna SR", programmes[1].title)
        assertEquals(currentEnd, programmes[0].end)
        assertTrue(programmes[1].start.isAfter(programmes[0].start))
    }

    private class FakeStvrOfficialApi(
        private val currentStart: Instant,
        private val currentEnd: Instant,
        private val schedule: List<StvrScheduleProgramme>,
    ) : StvrOfficialApi {
        override suspend fun fetchLive(channel: Channel): StvrLiveData = StvrLiveData(
            programTitle = "Sieň slávy",
            description = null,
            start = currentStart,
            end = currentEnd,
            sources = emptyList(),
        )

        override suspend fun fetchSchedule(from: java.time.LocalDate, days: Int): List<StvrScheduleProgramme> =
            schedule
    }
}
