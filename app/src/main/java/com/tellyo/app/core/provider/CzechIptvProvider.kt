package com.tellyo.app.core.provider

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelLineup
import com.tellyo.app.core.model.StreamResolution
import com.tellyo.app.core.model.StreamSource
import com.tellyo.app.core.network.readLimitedBytes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface CzechPlaylistRepository {
    suspend fun getEntries(): List<M3uChannel>
}

private val Context.czechPlaylistStore by preferencesDataStore(name = "czech_iptv")

/** The bundled selection defines channel identities, names and order across refreshes. */
internal class CzechPlaylistCatalog(private val bundled: List<M3uChannel>) {
    fun merge(raw: String): List<M3uChannel> {
        val updated = M3uPlaylistParser.parse(raw, CzechIptvProvider.PROVIDER_ID)
            .associateBy { it.channel.id }
        require(bundled.any { it.channel.id in updated }) { "No selected Czech feeds in playlist" }
        return bundled.map { original ->
            updated[original.channel.id]?.copy(channel = original.channel) ?: original
        }
    }
}

@Singleton
class CachedCzechPlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    httpClient: OkHttpClient,
) : CzechPlaylistRepository {
    private val client = httpClient.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    private val catalog by lazy {
        context.assets.open(BUNDLED_FILE).bufferedReader().use {
            CzechPlaylistCatalog(M3uPlaylistParser.parse(it.readText(), CzechIptvProvider.PROVIDER_ID))
        }
    }
    private val cache = LocalFirstCache(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        refreshIntervalNanos = TimeUnit.HOURS.toNanos(6),
        loadLocal = ::loadLocalEntries,
        refresh = ::refreshEntries,
    )

    override suspend fun getEntries(): List<M3uChannel> = withContext(Dispatchers.IO) { cache.get() }

    private suspend fun loadLocalEntries(): List<M3uChannel> {
        val cached = context.czechPlaylistStore.data.catch {
            if (it is IOException) emit(emptyPreferences()) else throw it
        }.first()[CACHED_PLAYLIST]
        return cached?.let { runCatching { catalog.merge(it) }.getOrNull() }
            ?: context.assets.open(BUNDLED_FILE).bufferedReader().use { catalog.merge(it.readText()) }
    }

    private suspend fun refreshEntries(): List<M3uChannel> {
        val raw = client.newCall(Request.Builder().url(PLAYLIST_URL).build()).execute().use {
            check(it.isSuccessful) { "Playlist HTTP ${it.code}" }
            requireNotNull(it.body).readLimitedBytes(MAX_PLAYLIST_BYTES).toString(Charsets.UTF_8)
        }
        val parsed = catalog.merge(raw)
        context.czechPlaylistStore.edit { it[CACHED_PLAYLIST] = raw }
        return parsed
    }

    companion object {
        const val PLAYLIST_URL = "https://iptv-org.github.io/iptv/countries/cz.m3u"
        const val BUNDLED_FILE = "czech-iptv.m3u"
        private val CACHED_PLAYLIST = stringPreferencesKey("playlist")
        private const val MAX_PLAYLIST_BYTES = 2L * 1024 * 1024
    }
}

@Singleton
class CzechIptvProvider @Inject constructor(
    private val playlistRepository: CzechPlaylistRepository,
    private val slovakPlaylistRepository: SlovakPlaylistRepository,
) : TvProvider {
    override val id = PROVIDER_ID

    override suspend fun getChannels(): List<Channel> {
        val existing = slovakPlaylistRepository.getEntries().map { it.channel.id }.toSet()
        return playlistRepository.getEntries().mapIndexedNotNull { index, entry ->
            val channelId = appChannelId(entry.channel.id)
            if (channelId in existing || isRemovedChannel(channelId)) null else entry.channel.copy(
                id = channelId,
                number = ChannelLineup.CHILDREN_NUMBERS[channelId] ?: (200 + index),
                providerId = id,
            )
        }
    }

    override suspend fun resolveStream(channel: Channel): StreamResolution {
        if (channel.providerId != id || !channel.enabled || isRemovedChannel(channel.id)) {
            return StreamResolution.Unavailable("Kanál nie je dostupný v českom IPTV zozname.")
        }
        val entry = playlistRepository.getEntries().firstOrNull { appChannelId(it.channel.id) == channel.id }
            ?: return StreamResolution.Unavailable("Kanál už nie je v aktuálnom IPTV zozname.")
        return StreamResolution.Playable(StreamSource(
            channelId = channel.id,
            uri = Uri.parse(entry.url),
            type = entry.type,
            headers = entry.headers,
            targetLiveOffsetMs = 30_000L,
        ))
    }

    companion object {
        const val PROVIDER_ID = "czech-iptv"

        private fun isRemovedChannel(channelId: String): Boolean =
            channelId.substringBefore('@') == "iptv-DisneyChannel.hu"

        // Retain both requested AXN White feeds; single-feed channels reuse existing IDs.
        internal fun appChannelId(playlistId: String): String =
            if (playlistId.startsWith("iptv-AXNWhite.us@")) playlistId else playlistId.substringBefore('@')
    }
}
