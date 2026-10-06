package com.tellyo.app.core.provider

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tellyo.app.core.config.RemoteConfigEndpoint
import com.tellyo.app.core.model.StreamSource
import com.tellyo.app.core.model.StreamType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Configuration supplied by a broadcaster/rights-holder authorized metadata service. */
@Serializable
data class ProviderConfigDocument(
    val schemaVersion: Int,
    val streams: List<AuthorizedStreamEntry> = emptyList(),
    val epg: AuthorizedEpgEntry? = null,
)

@Serializable
data class AuthorizedStreamEntry(
    val channelId: String,
    val uri: String,
    val type: String,
    val expiresAt: String? = null,
    val enabled: Boolean = true,
)

@Serializable
data class AuthorizedEpgEntry(
    val uri: String,
    val format: String = "XMLTV",
    val enabled: Boolean = true,
)

object ProviderConfigCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun decode(raw: String): ProviderConfigDocument {
        val document = json.decodeFromString<ProviderConfigDocument>(raw)
        require(document.schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported provider configuration schema ${document.schemaVersion}"
        }
        document.streams.forEach(::validateStream)
        document.epg?.let(::validateEpg)
        return document
    }

    private fun validateStream(entry: AuthorizedStreamEntry) {
        require(entry.channelId.isNotBlank()) { "Configured stream has no channel ID" }
        requireHttps(entry.uri, "stream")
        require(runCatching { StreamType.valueOf(entry.type.uppercase()) }.isSuccess) {
            "Unsupported stream type ${entry.type}"
        }
        entry.expiresAt?.let { require(runCatching { Instant.parse(it) }.isSuccess) {
            "Invalid stream expiry for ${entry.channelId}"
        } }
    }

    private fun validateEpg(entry: AuthorizedEpgEntry) {
        require(entry.format.equals("XMLTV", ignoreCase = true)) { "Only XMLTV EPG is supported" }
        requireHttps(entry.uri, "EPG")
    }

    private fun requireHttps(raw: String, label: String) {
        val uri = runCatching { java.net.URI(raw) }.getOrNull()
        require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
            "Configured $label URL must use HTTPS"
        }
    }

    private const val CURRENT_SCHEMA_VERSION = 1
}

interface AuthorizedProviderConfigRepository {
    suspend fun getStream(channelId: String): StreamSource?
    suspend fun getEpg(): AuthorizedEpgEntry?
    suspend fun refreshRemoteConfig(): Boolean
}

private val Context.tellyoProviderConfigDataStore by preferencesDataStore(
    name = "tellyo_provider_config",
)

@Singleton
class DataStoreAuthorizedProviderConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteEndpoint: RemoteConfigEndpoint,
) : AuthorizedProviderConfigRepository {
    private val mutex = Mutex()
    private var inMemoryDocument: ProviderConfigDocument? = null

    override suspend fun getStream(channelId: String): StreamSource? {
        val current = document()
        current.activeStream(channelId)?.let { return it }
        val hasExpiredRoute = current.streams.any { entry ->
            entry.channelId == channelId && entry.enabled &&
                entry.expiresAt?.let(Instant::parse)?.isBefore(Instant.now()) == true
        }
        if (hasExpiredRoute && remoteEndpoint.isConfigured && refreshRemoteConfig()) {
            return document().activeStream(channelId)
        }
        return null
    }

    override suspend fun getEpg(): AuthorizedEpgEntry? = document().epg
        ?.takeIf(AuthorizedEpgEntry::enabled)

    override suspend fun refreshRemoteConfig(): Boolean = withContext(Dispatchers.IO) {
        val raw = try {
            remoteEndpoint.fetch(REMOTE_CONFIG_PATH)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return@withContext false
        val document = runCatching { ProviderConfigCodec.decode(raw) }.getOrNull()
            ?: return@withContext false
        mutex.withLock {
            cache(raw)
            inMemoryDocument = document
        }
        true
    }

    private suspend fun document(): ProviderConfigDocument = withContext(Dispatchers.IO) {
        mutex.withLock {
            inMemoryDocument ?: loadDocument().also { inMemoryDocument = it }
        }
    }

    private suspend fun loadDocument(): ProviderConfigDocument {
        val cachedRaw = context.tellyoProviderConfigDataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .first()[Keys.cachedDocument]
        val cached = cachedRaw?.let { runCatching { ProviderConfigCodec.decode(it) }.getOrNull() }
        if (cached != null) return cached

        val bundled = context.assets.open(BUNDLED_CONFIG_FILE).bufferedReader().use { it.readText() }
        return ProviderConfigCodec.decode(bundled)
    }

    private suspend fun cache(raw: String) {
        context.tellyoProviderConfigDataStore.edit { preferences ->
            preferences[Keys.cachedDocument] = raw
        }
    }

    private fun ProviderConfigDocument.activeStream(channelId: String): StreamSource? = streams
        .firstOrNull { it.channelId == channelId && it.enabled }
        ?.takeUnless { entry -> entry.expiresAt?.let(Instant::parse)?.isBefore(Instant.now()) == true }
        ?.let { entry ->
            StreamSource(
                channelId = entry.channelId,
                uri = Uri.parse(entry.uri),
                type = StreamType.valueOf(entry.type.uppercase()),
                expiresAt = entry.expiresAt?.let(Instant::parse),
            )
        }

    private object Keys {
        val cachedDocument = stringPreferencesKey("cached_document")
    }

    private companion object {
        const val BUNDLED_CONFIG_FILE = "providers.json"
        const val REMOTE_CONFIG_PATH = "config/v1/providers.json"
    }
}

object EmptyAuthorizedProviderConfigRepository : AuthorizedProviderConfigRepository {
    override suspend fun getStream(channelId: String): StreamSource? = null
    override suspend fun getEpg(): AuthorizedEpgEntry? = null
    override suspend fun refreshRemoteConfig(): Boolean = false
}

class InMemoryAuthorizedProviderConfigRepository(
    private val document: ProviderConfigDocument,
) : AuthorizedProviderConfigRepository {
    override suspend fun getStream(channelId: String): StreamSource? = document.streams
        .firstOrNull { it.channelId == channelId && it.enabled }
        ?.takeUnless { entry -> entry.expiresAt?.let(Instant::parse)?.isBefore(Instant.now()) == true }
        ?.let { entry ->
            StreamSource(
                channelId = channelId,
                uri = Uri.parse(entry.uri),
                type = StreamType.valueOf(entry.type.uppercase()),
                expiresAt = entry.expiresAt?.let(Instant::parse),
            )
        }

    override suspend fun getEpg(): AuthorizedEpgEntry? = document.epg?.takeIf { it.enabled }
    override suspend fun refreshRemoteConfig(): Boolean = false
}
