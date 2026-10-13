package com.tellyo.app.core.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Streams to private cache, caps the response, and deletes incomplete downloads. */
class ApkDownloader(httpClient: OkHttpClient, private val directory: File) : UpdateDownloader {
    private val client = httpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .followSslRedirects(false)
        .build()

    override suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            require(update.sizeBytes in 1..MAX_UPDATE_BYTES)
            require(update.sha256.matches(Regex("[a-fA-F0-9]{64}")))
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create update cache")
            val target = File(directory, "tellyo-${update.versionCode}.apk")
            val partial = File(directory, "tellyo-${update.versionCode}.part")
            target.delete()
            val call = client.newCall(Request.Builder().url(update.apkUrl).build())
            try {
                call.awaitResponse().use { response ->
                    if (!response.isSuccessful) throw IOException("APK download: HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty APK response")
                    if (body.contentLength() > update.sizeBytes) throw IOException("APK too large")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    var lastPercent = -1
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > update.sizeBytes) throw IOException("APK too large")
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                val percent = (total * 100 / update.sizeBytes).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(total.toFloat() / update.sizeBytes)
                                }
                            }
                        }
                    }
                    if (total != update.sizeBytes) throw IOException("Incomplete APK")
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!hash.equals(update.sha256, ignoreCase = true)) throw IOException("APK checksum mismatch")
                }
                currentCoroutineContext().ensureActive()
                if (!partial.renameTo(target)) throw IOException("Cannot save APK")
                target
            } finally {
                call.cancel()
                partial.delete()
            }
        }
}
