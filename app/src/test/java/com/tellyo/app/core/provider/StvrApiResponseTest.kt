package com.tellyo.app.core.provider

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StvrApiResponseTest {
    @Test fun liveResponseWithoutContentLengthIsBounded() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(object : ResponseBody() {
                    private val data = Buffer().write(ByteArray(2 * 1024 * 1024 + 1))
                    override fun contentType() = null
                    override fun contentLength() = -1L
                    override fun source() = data
                }).build()
        }.build()
        assertThrows(IOException::class.java) {
            runBlocking { HttpStvrOfficialApi(client).fetchLive(ChannelCatalogFixtures.stvr().first()) }
        }
    }

    @Test fun scheduleCancellationDoesNotFetchSubsequentDays() {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor {
            calls++
            throw CancellationException("Cancelled")
        }.build()
        assertThrows(CancellationException::class.java) {
            runBlocking { HttpStvrOfficialApi(client).fetchSchedule(LocalDate.of(2026, 10, 6), 2) }
        }
        assertEquals(1, calls)
    }
}
