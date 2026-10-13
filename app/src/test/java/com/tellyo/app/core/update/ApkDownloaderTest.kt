package com.tellyo.app.core.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = byteArrayOf(1, 2, 3, 4)

    @Test fun streamsVerifiedApkAndReportsCompletion() = runBlocking {
        val directory = temporary.newFolder()
        val progress = mutableListOf<Float>()
        val file = downloader(directory).download(update(), progress::add)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(1f, progress.last())
        assertEquals(listOf("tellyo-1001.apk"), directory.list()!!.toList())
    }

    @Test fun checksumMismatchDeletesPartialFile() {
        rejects(update().copy(sha256 = "0".repeat(64)))
    }

    @Test fun truncatedBodyDeletesPartialFile() {
        rejects(update().copy(sizeBytes = 5))
    }

    @Test fun chunkedResponseCannotExceedDeclaredSize() {
        rejects(update().copy(sizeBytes = 3))
    }

    @Test fun httpFailureDoesNotLeaveAnApk() {
        rejects(update(), status = 500)
    }

    private fun rejects(update: AppUpdate, status: Int = 200) {
        val directory = temporary.newFolder()
        assertThrows(IOException::class.java) {
            runBlocking { downloader(directory, status).download(update) {} }
        }
        assertFalse(File(directory, "tellyo-1001.part").exists())
        assertFalse(File(directory, "tellyo-1001.apk").exists())
    }

    private fun update() = AppUpdate(1001, "0.1.1001", "https://github.com/lHyperionl/Tellyo/releases/download/build-1001/tellyo.apk",
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, bytes.size.toLong())

    private fun downloader(directory: File, status: Int = 200): ApkDownloader {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("fixture").body(object : ResponseBody() {
                    private val buffer = Buffer().write(bytes)
                    override fun contentType() = null
                    override fun contentLength() = -1L
                    override fun source() = buffer
                }).build()
        }.build()
        return ApkDownloader(client, directory)
    }
}
