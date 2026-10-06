package com.tellyo.app.core.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LimitedResponseTest {
    @Test
    fun unknownLengthBodyAcceptsExactLimitAndRejectsOversize() {
        assertArrayEquals(ByteArray(1024), body(1024).use { it.readLimitedBytes(1024) })
        assertThrows(IOException::class.java) { body(1025).use { it.readLimitedBytes(1024) } }
    }

    @Test
    fun oversizedStreamStopsAfterLimitPlusOne() {
        var consumed = 0
        val endless = object : InputStream() {
            override fun read(): Int { consumed++; return 0 }
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                consumed += len
                return len
            }
        }
        assertThrows(IOException::class.java) { endless.readLimited(1024) }
        assertEquals(1025, consumed)
    }

    @Test
    fun gzipExpansionIsBoundedIndependentlyOfDownloadSize() {
        val compressed = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(ByteArray(32_000)) }
        }.toByteArray()
        assertThrows(IOException::class.java) {
            GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readLimited(1024) }
        }
    }

    private fun body(size: Int) = object : ResponseBody() {
        private val buffer = Buffer().write(ByteArray(size))
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = -1
        override fun source() = buffer
    }
}
