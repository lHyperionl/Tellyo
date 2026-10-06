package com.tellyo.app.core.epg

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class PublishedScheduleEpgProviderTest {
    @Test fun markizaMapsAllFiveChannelsAndKeepsExplicitDstOffsets() {
        val channels = listOf("markiza", "doma", "dajto", "krimi", "klasik", "nova-sport-1")
        val records = channels.joinToString(",") { channel ->
            """{"id":"$channel","segments":[{"items":[
                {"title":"Film & seriál","description":"Synopsis","image":"https://example.com/art",
                 "start_at":"2026-10-25T02:30:00+02:00","end_at":"2026-10-25T02:30:00+01:00"},
                {"title":"Invalid","start_at":"bad","end_at":"bad"},
                {"title":"Backwards","start_at":"2026-10-25T06:00:00+01:00","end_at":"2026-10-25T05:00:00+01:00"}
            ]}]}"""
        }
        val html = "<script>var PageData = {\"program\":[{\"channels\":[$records]}]};</script>"
        val programs = MarkizaScheduleParser().parse(html)
        assertEquals(setOf("markiza", "markiza-doma", "markiza-dajto", "markiza-krimi", "markiza-klasik"),
            programs.map { it.channelId }.toSet())
        assertEquals(5, programs.size)
        assertEquals(Duration.ofHours(1), Duration.between(programs.first().start, programs.first().end))
        assertEquals(programs, XmlTvParser().parse(programs.toScheduleXmlTv()))
        assertTrue(MarkizaScheduleParser().parse("<html>No schedule</html>").isEmpty())
    }

    @Test fun ta3UsesEpochTimesAndRejectsMalformedOrRepeatedRows() {
        val row = """<li class="program_item" data-timestart="1791237600" data-timeend="1791239400">
            <span class="program_name">Správy &amp; šport</span><p class="program_description">Aktuálne správy.</p></li>"""
        val programs = Ta3ScheduleParser().parse("""<ul class="program_list">$row$row
            <li class="program_item" data-timestart="bad" data-timeend="1791239400"><span class="program_name">Bad</span></li>
            <li class="program_item" data-timestart="1791239400" data-timeend="1791237600"><span class="program_name">Backwards</span></li></ul>""")
        assertEquals(1, programs.size)
        assertEquals("ta3", programs.single().channelId)
        assertEquals("Správy & šport", programs.single().title)
        assertEquals(Instant.ofEpochSecond(1791237600), programs.single().start)
        assertEquals(programs, XmlTvParser().parse(programs.toScheduleXmlTv()))
    }

    @Test fun primaAndSportUseExactPlaylistIdsAndDoNotAcceptAnotherStation() {
        val parser = AktualityScheduleParser()
        assertEquals(setOf("iptv-PrimaSK.sk", "iptv-PrimaCoolSK.sk", "iptv-PrimaKrimiSK.sk",
            "iptv-PrimaloveSK.sk", "iptv-Sport1.cz", "iptv-Sport2.cz"),
            PrimaSportEpgProvider.STATIONS.values.map { it.channelId }.toSet())
        val playlistIds = com.tellyo.app.core.provider.M3uPlaylistParser.parse(
            java.io.File("src/main/assets/slovak-iptv.m3u").readText(),
        ).map { it.channel.id }.toSet()
        assertTrue(playlistIds.containsAll(PrimaSportEpgProvider.STATIONS.values.map { it.channelId }))
        for (station in PrimaSportEpgProvider.STATIONS.values) {
            val programs = parser.parse(aktualityFixture(station), station)
            assertEquals(station.channelId, programs.single().channelId)
            assertEquals("Show's & zápas", programs.single().title)
            assertEquals("Line one Line two", programs.single().description)
            assertEquals(Duration.ofMinutes(90), Duration.between(programs.single().start, programs.single().end))
            assertEquals(programs, XmlTvParser().parse(programs.toScheduleXmlTv()))
            assertTrue(parser.parse(aktualityFixture(station).replace(station.name, "Other station"), station).isEmpty())
            assertTrue(parser.parse(aktualityFixture(station).replace("length:'90'", "length:'0'"), station).isEmpty())
        }
    }

    @Test fun failedStationsHaveNoReplacementWindowAndDoNotHideSuccessfulStations() = runBlocking {
        val successfulStation = PrimaSportEpgProvider.STATIONS.values.first()
        val successfulUrl = PrimaSportEpgProvider.STATIONS.keys.first()
        var requests = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            val successful = chain.request().url.toString() == successfulUrl
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (successful) 200 else 503).message("fixture")
                .body((if (successful) aktualityFixture(successfulStation) else "Unavailable").toResponseBody())
                .build()
        }.build()
        val provider = PrimaSportEpgProvider(http, AktualityScheduleParser())
        val result = provider.fetch()
        val programs = XmlTvParser().parse(result.xml)
        assertEquals(6, requests)
        assertEquals(Duration.ofHours(6), provider.minimumRefreshInterval)
        assertEquals(listOf(EpgReplacementWindow(successfulStation.channelId, programs.single().start,
            programs.single().end)), result.replacementWindows)
    }

    @Test fun whollyUnavailableSourceFailsWithoutAnEmptyReplacement() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("<html>Maintenance</html>".toResponseBody()).build()
        }.build()
        try {
            Ta3EpgProvider(http, Ta3ScheduleParser()).fetch()
            fail("An invalid source must fail so Room retains its cache")
        } catch (expected: java.io.IOException) {
            assertTrue(expected.message!!.contains("No published schedules"))
        }
    }

    private fun aktualityFixture(station: ScheduleStation): String = """
        <span class="channel-title"><b>${station.name}</b></span>
        <script>
        program_desc[123] = {
            title:'Show\'s &amp; z\u00e1pas',
            desc:'Line one\n&lt;b&gt;Line two&lt;/b&gt;',
            length:'90',
            category:'sport',
            image:'https://example.com/art',
            channel_title:'${station.name}',
            start_time:'1791325800'
        };
        </script>
    """.trimIndent()
}
