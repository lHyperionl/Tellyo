package com.tellyo.app.core.provider

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tellyo.app.core.model.Channel
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

interface SlovakPlaylistRepository {
    suspend fun getEntries(): List<M3uChannel>
}

private val Context.slovakPlaylistStore by preferencesDataStore(name = "slovak_iptv")

/** Local-first reads with a background refresh at most every six hours. */
@Singleton
class CachedSlovakPlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    httpClient: OkHttpClient,
) : SlovakPlaylistRepository {
    private val client = httpClient.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    private val cache = LocalFirstCache(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        refreshIntervalNanos = REFRESH_NANOS,
        loadLocal = ::loadLocalEntries,
        refresh = ::refreshEntries,
    )

    override suspend fun getEntries(): List<M3uChannel> = withContext(Dispatchers.IO) {
        cache.get()
    }

    private suspend fun loadLocalEntries(): List<M3uChannel> {
        val cached = context.slovakPlaylistStore.data.catch {
            if (it is IOException) emit(emptyPreferences()) else throw it
        }.first()[CACHED_PLAYLIST]
        return cached?.let { runCatching { M3uPlaylistParser.parse(it) }.getOrNull() }
            ?: context.assets.open(BUNDLED_FILE).bufferedReader().use {
                M3uPlaylistParser.parse(it.readText())
            }
    }

    private suspend fun refreshEntries(): List<M3uChannel> {
        val raw = client.newCall(Request.Builder().url(PLAYLIST_URL).build()).execute().use {
            check(it.isSuccessful) { "Playlist HTTP ${it.code}" }
            requireNotNull(it.body).readLimitedBytes(MAX_PLAYLIST_BYTES).toString(Charsets.UTF_8)
        }
        val parsed = M3uPlaylistParser.parse(raw)
        context.slovakPlaylistStore.edit { it[CACHED_PLAYLIST] = raw }
        return parsed
    }

    companion object {
        const val PLAYLIST_URL = "https://dearbulut.github.io/iptv/playlists/language/slk.m3u"
        const val BUNDLED_FILE = "slovak-iptv.m3u"
        private val CACHED_PLAYLIST = stringPreferencesKey("playlist")
        private val REFRESH_NANOS = TimeUnit.HOURS.toNanos(6)
        private const val MAX_PLAYLIST_BYTES = 2L * 1024 * 1024
    }
}

@Singleton
class SlovakIptvProvider @Inject constructor(
    private val playlistRepository: SlovakPlaylistRepository,
) : TvProvider {
    override val id = PROVIDER_ID

    override suspend fun getChannels(): List<Channel> = playlistRepository.getEntries()
        .filterNot { isExcludedChannel(it.channel.id) }
        .map { entry ->
            val channel = entry.channel
            channel.copy(number = CHANNEL_NUMBERS_AFTER_TA3[channel.id] ?: channel.number)
        }

    override suspend fun resolveStream(channel: Channel): StreamResolution {
        if (channel.providerId != id || !channel.enabled || isExcludedChannel(channel.id)) {
            return StreamResolution.Unavailable("Kanál nie je dostupný v slovenskom IPTV zozname.")
        }
        val entry = playlistRepository.getEntries().firstOrNull { it.channel.id == channel.id }
            ?: return StreamResolution.Unavailable("Kanál už nie je v aktuálnom IPTV zozname.")
        return StreamResolution.Playable(
            StreamSource(
                channel.id,
                Uri.parse(entry.url),
                entry.type,
                headers = entry.headers,
                // Sport2 is a single-rendition ~3.5 Mbps feed. Its 4–6 second
                // segments can take longer than their duration to arrive. Keep
                // more of the ~50 second playlist ahead of the playback point.
                targetLiveOffsetMs = if (channel.id.substringBefore('@') == "iptv-Sport2.cz") {
                    40_000L
                } else {
                    30_000L
                },
            ),
        )
    }

    companion object {
        const val PROVIDER_ID = "slovak-iptv"
        // Stable positions after TA3 (18), independent of playlist refresh order.
        private val CHANNEL_NUMBERS_AFTER_TA3 = mapOf(
            "iptv-PrimaSK.sk" to 19,
            "iptv-PrimaCoolSK.sk" to 20,
            "iptv-PrimaKrimiSK.sk" to 21,
            "iptv-PrimaloveSK.sk" to 22,
            "iptv-Sport1.cz" to 23,
            "iptv-Sport2.cz" to 24,
        )
        private val EXISTING_STVR_IDS = setOf(
            "iptv-Jednotka.sk", "iptv-Dvojka.sk", "iptv-24.sk", "iptv-Sport.sk",
        )
        // User-removed channels stay excluded from bundled, cached and refreshed playlists.
        private val REMOVED_CHANNEL_IDS = setOf(
            "iptv-AntikInfoTV.sk", "iptv-JimJamEurope.uk", "iptv-LifeTv.sk", "iptv-ManaCirkevOnline.pt",
            "iptv-TeleviziaMocenok.sk", "iptv-TurboTV.sk", "iptv-TV8.sk", "iptv-TV9.sk",
            "iptv-TVBrezova.sk", "iptv-TVCentral.sk", "iptv-TVDK.sk", "iptv-TVLiptov.sk",
            "iptv-TVLocAll.sk", "iptv-TVLux.sk", "iptv-TVMistral.sk", "iptv-TVMyjava.sk",
            "iptv-TVNitricka.sk", "iptv-TVNRSR.sk", "iptv-TVPanorama.sk", "iptv-TVPovazie.sk",
            "iptv-TVRaj.sk", "iptv-TVRomana.sk", "iptv-TVRuzinov.sk", "iptv-TVSen.sk",
            "iptv-TVSeverka.sk", "iptv-TVSkalica.sk", "iptv-TVStaraTura.sk", "iptv-TVT.sk",
            "iptv-TVVega.sk", "iptv-TVVita.sk", "iptv-VIOTV.sk", "iptv-ZapadoslovenskaTV.sk",
        )

        private fun isExcludedChannel(channelId: String): Boolean =
            channelId.substringBefore('@').let { it in EXISTING_STVR_IDS || it in REMOVED_CHANNEL_IDS }
    }
}
