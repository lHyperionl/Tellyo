package com.tellyo.app.core.epg

import com.tellyo.app.core.provider.AuthorizedProviderConfigRepository
import com.tellyo.app.core.network.readLimited
import com.tellyo.app.core.network.readLimitedBytes
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Uses an explicitly authorized aggregate XMLTV feed when configured.
 *
 * The feed is expected to use Tellyo channel IDs. Once configured, failures do not fall back to
 * page parsing; Room retains the last valid guide instead. With no configured feed, the legacy
 * STVR-only source remains available for development builds.
 */
@Singleton
class ConfiguredXmlTvEpgProvider @Inject constructor(
    private val providerConfig: AuthorizedProviderConfigRepository,
    private val httpClient: OkHttpClient,
    private val stvrFallback: StvrEpgProvider,
) : EpgProvider {
    override val id: String = "configured-xmltv"
    override val priority: Int = 100
    private var usingConfiguredFeed = false
    override val minimumRefreshInterval: java.time.Duration
        get() = if (usingConfiguredFeed) java.time.Duration.ofHours(6) else stvrFallback.minimumRefreshInterval

    override suspend fun fetchXml(): String = fetch().xml

    override suspend fun fetch(): EpgFetchResult {
        val configured = providerConfig.getEpg()
        usingConfiguredFeed = configured != null
        if (configured == null) return stvrFallback.fetch()
        val request = Request.Builder()
            .url(configured.uri)
            .header("Accept", "application/xml, application/gzip")
            .header("User-Agent", "Tellyo AndroidTV")
            .build()

        val xml = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Authorized XMLTV endpoint returned HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Authorized XMLTV endpoint returned no body")
                val compressed = body.readLimitedBytes(MAX_COMPRESSED_BYTES.toLong())
                val xmlBytes = if (compressed.isGzip()) {
                    GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readLimited(MAX_XML_BYTES.toLong()) }
                } else {
                    if (compressed.size > MAX_XML_BYTES) throw IOException("Authorized XMLTV response is too large")
                    compressed
                }
                xmlBytes.toString(Charsets.UTF_8)
            }
        }
        return EpgFetchResult(xml)
    }

    private fun ByteArray.isGzip(): Boolean = size >= 2 &&
        this[0] == GZIP_MAGIC_FIRST && this[1] == GZIP_MAGIC_SECOND

    private companion object {
        const val MAX_COMPRESSED_BYTES = 8 * 1024 * 1024
        const val MAX_XML_BYTES = 32 * 1024 * 1024
        const val GZIP_MAGIC_FIRST: Byte = 0x1f
        const val GZIP_MAGIC_SECOND: Byte = 0x8b.toByte()
    }
}
