package com.tellyo.app.core.update

import androidx.core.content.pm.PackageInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidUpdateDownloaderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val installedVersion = PackageInfoCompat.getLongVersionCode(
        context.packageManager.getPackageInfo(context.packageName, 0),
    ).toInt()

    @Test fun acceptsApkMatchingInstalledPackageVersionAndSigner(): Unit = runBlocking {
        val bytes = File(context.applicationInfo.sourceDir).readBytes()
        val apk = downloader(bytes).download(update(bytes)) {}
        assertEquals(bytes.size.toLong(), apk.length())
        apk.delete()
    }

    @Test fun rejectsApkThatDoesNotMatchAdvertisedVersion() {
        val bytes = File(context.applicationInfo.sourceDir).readBytes()
        assertThrows(IOException::class.java) {
            runBlocking { downloader(bytes).download(update(bytes).copy(versionCode = installedVersion + 1)) {} }
        }
        assertFalse(File(context.cacheDir, "updates/tellyo-${installedVersion + 1}.apk").exists())
    }

    @Test fun rejectsAnotherPackageEvenWhenItsBytesMatchTheChecksum() {
        val bytes = File(instrumentation.context.applicationInfo.sourceDir).readBytes()
        assertThrows(IOException::class.java) {
            runBlocking { downloader(bytes).download(update(bytes)) {} }
        }
        assertFalse(File(context.cacheDir, "updates/tellyo-$installedVersion.apk").exists())
    }

    private fun update(bytes: ByteArray) = AppUpdate(installedVersion, "test",
        "https://github.com/lHyperionl/Tellyo/releases/download/test/tellyo.apk",
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        bytes.size.toLong())

    private fun downloader(bytes: ByteArray): AndroidUpdateDownloader {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("fixture").body(bytes.toResponseBody()).build()
        }.build()
        return AndroidUpdateDownloader(context, client)
    }
}
