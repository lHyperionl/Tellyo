package com.tellyo.app.core.epg

import com.tellyo.app.core.database.ProgramDao
import com.tellyo.app.core.database.ProgramEntity
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomEpgRepositoryTest {
    private val now = Instant.now()

    @Test
    fun liveOnlyCorrectionRetainsFutureScheduleAndRemovesOldCurrentId() = runTest {
        val dao = MemoryDao()
        val future = program("future", now.plusSeconds(3600), now.plusSeconds(7200))
        val old = program("old", now.minusSeconds(600), now.plusSeconds(1200))
        val corrected = program("corrected", now.minusSeconds(480), now.plusSeconds(1200))
        val provider = Provider(EpgFetchResult(xml(old, future)))
        val repository = RoomEpgRepository(dao, setOf(provider), XmlTvParser())
        repository.refresh()
        provider.result = EpgFetchResult(xml(corrected), emptyList())
        repository.refresh()
        assertEquals(setOf("future", "corrected"), dao.programs.map { it.title }.toSet())
    }

    @Test
    fun partialDayReplacementKeepsUnavailableFutureDayAndDeletesCancelledShowsInCoveredRange() = runTest {
        val dao = MemoryDao()
        val future = program("future", now.plusSeconds(86_400), now.plusSeconds(90_000))
        val cancelled = program("cancelled", now.plusSeconds(1800), now.plusSeconds(3600))
        val current = program("current", now.minusSeconds(600), now.plusSeconds(1200))
        val provider = Provider(EpgFetchResult(xml(current, cancelled, future)))
        val repository = RoomEpgRepository(dao, setOf(provider), XmlTvParser())
        repository.refresh()
        provider.result = EpgFetchResult(xml(current), listOf(
            EpgReplacementWindow("channel", now.minusSeconds(3600), now.plusSeconds(7200)),
        ))
        repository.refresh()
        assertEquals(setOf("current", "future"), dao.programs.map { it.title }.toSet())
    }

    @Test
    fun frequentRefreshCallsDoNotDownloadOrRewriteThrottledFeed() = runTest {
        val dao = MemoryDao()
        val provider = Provider(EpgFetchResult(xml(program("show", now, now.plusSeconds(3600)))), Duration.ofHours(6))
        val repository = RoomEpgRepository(dao, setOf(provider), XmlTvParser())
        repeat(5) { repository.refresh() }
        assertEquals(1, provider.fetches)
        assertEquals(1, dao.inserts)
    }

    @Test
    fun throttledHigherPrioritySnapshotStillProtectsItsChannels() = runTest {
        val dao = MemoryDao()
        val lower = Provider(EpgFetchResult(xml(program("built-in", now, now.plusSeconds(3600)))), id = "lower")
        val higher = Provider(EpgFetchResult(xml(program("authorized", now, now.plusSeconds(3600)))),
            Duration.ofHours(6), id = "higher", priority = 100)
        val repository = RoomEpgRepository(dao, setOf(lower, higher), XmlTvParser())
        repository.refresh()
        repository.refresh()
        assertEquals(listOf("authorized"), dao.programs.map { it.title })
        assertEquals(1, higher.fetches)
        assertEquals(1, dao.inserts)
    }

    @Test
    fun higherPriorityWindowProtectsCachedRecordsWhenItsNextFetchFails() = runTest {
        val dao = MemoryDao()
        val current = program("authorized", now, now.plusSeconds(3600))
        val future = program("built-in future", now.plusSeconds(7200), now.plusSeconds(10800))
        val lower = Provider(EpgFetchResult(xml(program("built-in", now, now.plusSeconds(3600)), future)), id = "lower")
        val higher = Provider(EpgFetchResult(xml(current), listOf(
            EpgReplacementWindow("channel", now, now.plusSeconds(3600)),
        )), id = "higher", priority = 100)
        val repository = RoomEpgRepository(dao, setOf(lower, higher), XmlTvParser())
        repository.refresh()
        higher.fail = true
        repository.refresh()
        assertEquals(setOf("authorized", "built-in future"), dao.programs.map { it.title }.toSet())
    }

    @Test
    fun repositoryRestartRetainsPriorityWhenAuthorizedFeedIsUnavailable() = runTest {
        val dao = MemoryDao()
        val lower = Provider(EpgFetchResult(xml(program("built-in", now, now.plusSeconds(3600)))), id = "lower")
        val higher = Provider(EpgFetchResult(xml(program("authorized", now, now.plusSeconds(3600)))),
            id = "higher", priority = 100)
        RoomEpgRepository(dao, setOf(lower, higher), XmlTvParser()).refresh()
        higher.fail = true
        RoomEpgRepository(dao, setOf(lower, higher), XmlTvParser()).refresh()
        assertEquals(listOf("authorized"), dao.programs.map { it.title })
    }

    private fun program(title: String, start: Instant, end: Instant) = ProgramEntity(
        title, "channel", title, null, start.toEpochMilli(), end.toEpochMilli(), null, null,
    )

    private fun xml(vararg programs: ProgramEntity): String {
        val format = DateTimeFormatter.ofPattern("yyyyMMddHHmmss xx").withZone(ZoneOffset.UTC)
        return "<tv>" + programs.joinToString("") {
            "<programme channel=\"channel\" start=\"${format.format(Instant.ofEpochMilli(it.startEpochMillis))}\" " +
                "stop=\"${format.format(Instant.ofEpochMilli(it.endEpochMillis))}\"><title>${it.title}</title></programme>"
        } + "</tv>"
    }

    private class Provider(var result: EpgFetchResult, override val minimumRefreshInterval: Duration = Duration.ZERO,
        override val id: String = "test", override val priority: Int = 0) : EpgProvider {
        var fetches = 0
        var fail = false
        override suspend fun fetchXml() = result.xml
        override suspend fun fetch(): EpgFetchResult { fetches++; check(!fail); return result }
    }

    private class MemoryDao : ProgramDao {
        val programs = mutableListOf<ProgramEntity>()
        var inserts = 0
        override fun observeWindow(channelId: String, from: Long, to: Long) =
            observeChannelsWindow(listOf(channelId), from, to)
        override fun observeChannelsWindow(channelIds: List<String>, from: Long, to: Long) =
            flowOf(programs.filter { it.channelId in channelIds && it.endEpochMillis > from && it.startEpochMillis < to })
        override suspend fun insertAll(programs: List<ProgramEntity>) {
            inserts++
            this.programs.removeAll { old -> programs.any { it.id == old.id } }
            this.programs.addAll(programs)
        }
        override suspend fun deleteAll() { programs.clear() }
        override suspend fun deleteChannels(channelIds: List<String>, maximumPriority: Int) {
            programs.removeAll { it.channelId in channelIds && it.sourcePriority <= maximumPriority }
        }
        override suspend fun deleteOverlapping(channelId: String, from: Long, to: Long, maximumPriority: Int) {
            programs.removeAll { it.channelId == channelId && it.endEpochMillis > from && it.startEpochMillis < to && it.sourcePriority <= maximumPriority }
        }
        override suspend fun higherPriorityPrograms(channelIds: List<String>, priority: Int) =
            programs.filter { it.channelId in channelIds && it.sourcePriority > priority }
        override suspend fun deleteExpired(before: Long) { programs.removeAll { it.endEpochMillis < before } }
    }
}
