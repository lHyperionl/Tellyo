package com.tellyo.app.core.provider

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tellyo.app.core.config.RemoteConfigEndpoint
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The serializable shape of the local channel catalogue.
 *
 * Provider classes still own stream-resolution rules, but channel metadata is
 * kept in one configuration document so the UI order is independent of
 * provider registration order.
 */
@Serializable
data class ChannelCatalogDocument(
    val schemaVersion: Int,
    val channels: List<ChannelCatalogEntry>,
)

@Serializable
data class ChannelCatalogEntry(
    val id: String,
    val number: Int,
    val name: String,
    val shortName: String,
    val logoUrl: String? = null,
    @SerialName("provider")
    val providerId: String,
    val category: String = ChannelCategory.GENERAL.name,
    val enabled: Boolean = true,
)

fun ChannelCatalogEntry.toChannel(): Channel = Channel(
    id = id,
    number = number,
    name = name,
    shortName = shortName,
    logoUrl = logoUrl,
    providerId = providerId,
    category = runCatching { ChannelCategory.valueOf(category.uppercase()) }
        .getOrDefault(ChannelCategory.GENERAL),
    enabled = enabled,
)

/** Pure codec used by the Android repository and unit tests. */
object ChannelCatalogCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun decode(raw: String): List<Channel> {
        val document = json.decodeFromString<ChannelCatalogDocument>(raw)
        require(document.schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported channel catalogue schema ${document.schemaVersion}"
        }
        require(document.channels.map(ChannelCatalogEntry::id).distinct().size == document.channels.size) {
            "Channel catalogue contains duplicate IDs"
        }
        document.channels.forEach { entry ->
            require(entry.id.isNotBlank() && entry.providerId.isNotBlank()) {
                "Channel catalogue contains an incomplete entry"
            }
            entry.logoUrl?.let { logoUrl ->
                val uri = runCatching { java.net.URI(logoUrl) }.getOrNull()
                require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
                    "Channel logo URL must use HTTPS"
                }
            }
        }
        return document.channels
            .map(ChannelCatalogEntry::toChannel)
            .distinctBy(Channel::id)
            .sortedBy(Channel::number)
    }

    fun encode(channels: List<Channel>): String = json.encodeToString(
        ChannelCatalogDocument(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            channels = channels.sortedBy(Channel::number).map { channel ->
                ChannelCatalogEntry(
                    id = channel.id,
                    number = channel.number,
                    name = channel.name,
                    shortName = channel.shortName,
                    logoUrl = channel.logoUrl,
                    providerId = channel.providerId,
                    category = channel.category.name,
                    enabled = channel.enabled,
                )
            },
        ),
    )

    private const val CURRENT_SCHEMA_VERSION = 1
}

interface ChannelCatalogRepository {
    /** Returns the best available local catalogue. */
    suspend fun getChannels(): List<Channel>

    /**
     * Stores a validated remote document for a future remote-config fetcher.
     * The app does not fetch an unconfigured endpoint yet.
     */
    suspend fun cacheRemoteConfig(raw: String): Boolean

    /** Fetches and stores the authorized remote catalogue when configured. */
    suspend fun refreshRemoteConfig(): Boolean = false
}

private val Context.tellyoChannelCatalogDataStore by preferencesDataStore(
    name = "tellyo_channel_catalog",
)

/**
 * Configuration fallback order:
 * remote document saved by cacheRemoteConfig -> DataStore cache -> bundled JSON.
 */
@Singleton
class DataStoreChannelCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteEndpoint: RemoteConfigEndpoint,
) : ChannelCatalogRepository {
    private val mutex = Mutex()
    private var inMemoryChannels: List<Channel>? = null

    override suspend fun getChannels(): List<Channel> = withContext(Dispatchers.IO) {
        mutex.withLock {
            inMemoryChannels ?: loadChannels().also { inMemoryChannels = it }
        }
    }

    override suspend fun cacheRemoteConfig(raw: String): Boolean = withContext(Dispatchers.IO) {
        val channels = runCatching { ChannelCatalogCodec.decode(raw) }.getOrNull()
            ?: return@withContext false
        mutex.withLock {
            store(raw)
            inMemoryChannels = channels
        }
        true
    }

    override suspend fun refreshRemoteConfig(): Boolean {
        // Download outside the read lock so a slow endpoint cannot stall navigation.
        val raw = try {
            remoteEndpoint.fetch(REMOTE_CATALOG_PATH)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return false
        return cacheRemoteConfig(raw)
    }

    private suspend fun loadChannels(): List<Channel> {
        val cachedRaw = context.tellyoChannelCatalogDataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .first()[Keys.cachedDocument]
        val cached = cachedRaw?.let { runCatching { ChannelCatalogCodec.decode(it) }.getOrNull() }
        if (cached != null) return cached

        val bundled = context.assets.open(BUNDLED_CATALOG_FILE).bufferedReader().use { it.readText() }
        return ChannelCatalogCodec.decode(bundled)
    }

    private suspend fun store(raw: String) {
        context.tellyoChannelCatalogDataStore.edit { preferences ->
            preferences[Keys.cachedDocument] = raw
        }
    }

    private object Keys {
        val cachedDocument = stringPreferencesKey("cached_document")
    }

    private companion object {
        const val BUNDLED_CATALOG_FILE = "channels.json"
        const val REMOTE_CATALOG_PATH = "config/v1/channels.json"
    }
}

/** Small in-memory adapter for tests and the development-only preview provider. */
class InMemoryChannelCatalogRepository(
    private val channels: List<Channel>,
) : ChannelCatalogRepository {
    override suspend fun getChannels(): List<Channel> = channels.sortedBy(Channel::number)

    override suspend fun cacheRemoteConfig(raw: String): Boolean = false
}
