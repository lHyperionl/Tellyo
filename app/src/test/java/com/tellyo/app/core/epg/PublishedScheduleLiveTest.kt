package com.tellyo.app.core.epg

import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt in with TELLYO_LIVE_EPG=true; verifies current source schemas and tomorrow's coverage. */
class PublishedScheduleLiveTest {
    @Test fun publishedSourcesCoverAllTwelveRequestedChannels() = runBlocking {
        assumeTrue(System.getenv("TELLYO_LIVE_EPG") == "true")
        val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
        val providers = listOf(
            MarkizaEpgProvider(http, MarkizaScheduleParser()) to
                setOf("markiza", "markiza-doma", "markiza-dajto", "markiza-krimi", "markiza-klasik"),
            Ta3EpgProvider(http, Ta3ScheduleParser()) to setOf("ta3"),
            PrimaSportEpgProvider(http, AktualityScheduleParser()) to
                PrimaSportEpgProvider.STATIONS.values.map { it.channelId }.toSet(),
        )
        val now = Instant.now()
        for ((provider, expectedChannels) in providers) {
            val result = provider.fetch()
            val byChannel = XmlTvParser().parse(result.xml).groupBy { it.channelId }
            assertTrue("${provider.id}: missing channels ${expectedChannels - byChannel.keys}",
                byChannel.keys.containsAll(expectedChannels))
            for (channel in expectedChannels) {
                val records = byChannel.getValue(channel)
                println("$channel: ${records.size} programmes, ${records.first().start} to ${records.last().end}")
                assertTrue("$channel has no current programme", records.any { it.start <= now && it.end > now })
                assertTrue("$channel has no programme tomorrow", records.any { it.start > now.plus(Duration.ofDays(1)) })
                assertTrue(result.replacementWindows!!.any { it.channelId == channel })
            }
        }
    }
}
