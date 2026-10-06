package com.tellyo.app.core.network

import com.tellyo.app.core.model.StreamSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class StreamHealthResult(
    val isReachable: Boolean,
    val statusCode: Int?,
    val durationMillis: Long,
    val detail: String,
)

/** Performs a small, non-playback probe against a resolved manifest URL. */
interface StreamHealthChecker {
    suspend fun check(source: StreamSource): StreamHealthResult
}

@Singleton
class OkHttpStreamHealthChecker @Inject constructor(
    private val httpClient: OkHttpClient,
) : StreamHealthChecker {
    override suspend fun check(source: StreamSource): StreamHealthResult = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val request = Request.Builder()
            .url(source.uri.toString())
            // A range request avoids downloading a media segment while still
            // validating that the manifest endpoint is reachable.
            .header("Range", "bytes=0-4095")
            .header("Accept", "application/vnd.apple.mpegurl, application/dash+xml, */*")
            .apply {
                source.headers.forEach { (name, value) -> header(name, value) }
            }
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val elapsed = (System.nanoTime() - startedAt) / 1_000_000L
                StreamHealthResult(
                    isReachable = response.isSuccessful,
                    statusCode = response.code,
                    durationMillis = elapsed,
                    detail = "${source.type.name} · HTTP ${response.code}",
                )
            }
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            StreamHealthResult(
                isReachable = false,
                statusCode = null,
                durationMillis = (System.nanoTime() - startedAt) / 1_000_000L,
                detail = error.message ?: "Zdroj sa nepodarilo overiť",
            )
        }
    }
}
