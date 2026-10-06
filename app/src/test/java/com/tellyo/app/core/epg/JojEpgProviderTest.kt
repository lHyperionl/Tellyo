package com.tellyo.app.core.epg

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class JojEpgProviderTest {
    @Test fun failedFutureDaysDoNotDeclareCoverageAndCachedGuideDoesNotRedownload() = runBlocking {
        val zone = ZoneId.of("Europe/Bratislava")
        val today = LocalDate.now(zone)
        val start = today.atTime(12, 0).atZone(zone).toOffsetDateTime()
        val end = start.plusHours(1)
        var requests = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            val isToday = chain.request().url.queryParameter("date") == today.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (isToday) 200 else 503).message("fixture")
                .body(if (isToday) """{"1":[{"id":1,"title":"Today's show","start_at":"$start","end_at":"$end"}]}""".toResponseBody()
                    else "Unavailable".toResponseBody()).build()
        }.build()
        val provider = JojEpgProvider(http, JojProgrammeApiParser())
        val result = provider.fetch()
        assertEquals(1, XmlTvParser().parse(result.xml).size)
        assertEquals(listOf(EpgReplacementWindow("joj", today.atStartOfDay(zone).toInstant(),
            today.plusDays(1).atStartOfDay(zone).toInstant())), result.replacementWindows)
        assertEquals(9, requests)
        assertEquals(result, provider.fetch())
        assertEquals(9, requests)
    }
}
