package com.tellyo.app.core.config

import com.tellyo.app.BuildConfig
import com.tellyo.app.core.network.readLimitedBytes
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * HTTPS-only boundary for the metadata endpoint described by the implementation plan.
 *
 * The endpoint is intentionally build-configured rather than user-entered. This prevents
 * an untrusted UI value from turning the app into a player for arbitrary media sources.
 */
@Singleton
class RemoteConfigEndpoint @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    private val baseUrl: HttpUrl? = BuildConfig.TELLYO_CONFIG_BASE_URL
        .trim()
        .takeIf(String::isNotEmpty)
        ?.let(::validatedBaseUrl)

    val isConfigured: Boolean
        get() = baseUrl != null

    suspend fun fetch(path: String, maxBytes: Long = DEFAULT_MAX_BYTES): String? {
        val endpoint = baseUrl ?: return null
        val url = endpoint.resolve(path) ?: error("Invalid remote-config path: $path")
        require(url.scheme == "https") { "Remote configuration must use HTTPS" }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Tellyo/${BuildConfig.VERSION_NAME} AndroidTV")
            .build()

        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Remote configuration returned HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Remote configuration returned no body")
                val bytes = body.readLimitedBytes(maxBytes)
                bytes.toString(Charsets.UTF_8)
            }
        }
    }

    private fun validatedBaseUrl(raw: String): HttpUrl {
        val parsed = raw.toHttpUrlOrNull() ?: error("TELLYO_CONFIG_BASE_URL is not a valid URL")
        require(parsed.scheme == "https") { "TELLYO_CONFIG_BASE_URL must use HTTPS" }
        return parsed.newBuilder()
            .encodedPath(parsed.encodedPath.trimEnd('/') + "/")
            .query(null)
            .fragment(null)
            .build()
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 2L * 1024L * 1024L
    }
}
