package com.tellyo.app.core.provider

import android.net.Uri
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.StreamSource
import com.tellyo.app.core.model.StreamType
import com.tellyo.app.core.network.readLimitedBytes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Boundary around STVR's public live metadata and published programme catalogue.
 *
 * The endpoint is the same one used by STVR's official live player. It returns
 * a short-lived HLS/DASH source, so Tellyo resolves it at playback time rather
 * than storing an expiring media URL in the application.
 */
interface StvrOfficialApi {
    suspend fun fetchLive(channel: Channel): StvrLiveData

    /**
     * Returns the published STVR schedule for the requested local dates.
     * Implementations may return an empty list when a schedule source is not
     * available; callers must retain the live-program fallback in that case.
     */
    suspend fun fetchSchedule(from: LocalDate, days: Int): List<StvrScheduleProgramme> =
        emptyList()
}

data class StvrLiveData(
    val programTitle: String?,
    val description: String?,
    val start: Instant?,
    val end: Instant?,
    val sources: List<StreamSource>,
    val artworkUrl: String? = null,
)

data class StvrScheduleProgramme(
    val channelId: String,
    val title: String,
    val description: String?,
    val category: String?,
    val start: Instant,
    val artworkUrl: String?,
)

@Singleton
class HttpStvrOfficialApi @Inject constructor(
    private val httpClient: OkHttpClient,
) : StvrOfficialApi {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun fetchLive(channel: Channel): StvrLiveData {
        val officialChannelId = StvrTvProvider.officialChannelId(channel)
            ?: error("No STVR live channel mapping for ${channel.id}")
        val request = Request.Builder()
            .url("$LIVE_ENDPOINT?c=$officialChannelId&b=androidtv&p=android&f=0&d=1")
            .header("Accept", "application/json")
            .build()

        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("STVR live endpoint returned HTTP ${response.code}")
                }
                val body = response.body?.readLimitedBytes(MAX_LIVE_BYTES)?.toString(Charsets.UTF_8).orEmpty()
                val clip = json.decodeFromString<StvrLiveResponse>(body).clip
                    ?: error("STVR live endpoint returned no programme")
                StvrLiveData(
                    programTitle = clip.titleorig?.takeIf { it.isNotBlank() }
                        ?: clip.title?.takeIf { it.isNotBlank() },
                    description = clip.description?.takeIf { it.isNotBlank() },
                    start = parseSourceTime(clip.dateTimeStart),
                    end = parseSourceTime(clip.dateTimeStop),
                    sources = clip.sources.mapNotNull { it.toStreamSource(channel.id) },
                    artworkUrl = clip.image?.takeIf { it.startsWith("https://") },
                )
            }
        }
    }

    override suspend fun fetchSchedule(from: LocalDate, days: Int): List<StvrScheduleProgramme> {
        require(days > 0) { "Schedule must contain at least one day" }
        return withContext(Dispatchers.IO) {
            val parser = StvrScheduleParser()
            val pages = (0 until days).mapNotNull { offset ->
                currentCoroutineContext().ensureActive()
                val date = from.plusDays(offset.toLong())
                val request = Request.Builder()
                    .url("$PROGRAM_ENDPOINT?date=$date")
                    .header("Accept", "text/html")
                    .header("User-Agent", "Tellyo/0.1 Android TV")
                    .build()

                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            error("STVR programme endpoint returned HTTP ${response.code}")
                        }
                        parser.parse(response.body?.readLimitedBytes(MAX_SCHEDULE_BYTES)?.toString(Charsets.UTF_8).orEmpty(), date)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
            pages.flatten()
                .sortedWith(compareBy<StvrScheduleProgramme> { it.channelId }.thenBy { it.start })
                .distinctBy { programme -> "${programme.channelId}:${programme.start}" }
        }
    }

    private fun parseSourceTime(raw: String?): Instant? = raw
        ?.takeIf { it.isNotBlank() }
        ?.let {
            runCatching {
                LocalDateTime.parse(it)
                    .atZone(SOURCE_ZONE)
                    .toInstant()
            }.getOrNull()
        }

    private fun StvrLiveSource.toStreamSource(channelId: String): StreamSource? {
        val rawUri = src?.takeIf { it.startsWith("https://") } ?: return null
        val uri = Uri.parse(rawUri)
        val streamType = when {
            type.equals("application/x-mpegurl", ignoreCase = true) ||
                rawUri.contains(".m3u8", ignoreCase = true) -> StreamType.HLS
            type.equals("application/dash+xml", ignoreCase = true) ||
                rawUri.contains(".mpd", ignoreCase = true) -> StreamType.DASH
            else -> return null
        }
        return StreamSource(
            channelId = channelId,
            uri = uri,
            type = streamType,
        )
    }

    companion object {
        private const val MAX_LIVE_BYTES = 2L * 1024 * 1024
        private const val MAX_SCHEDULE_BYTES = 8L * 1024 * 1024
        const val LIVE_ENDPOINT = "https://www.stvr.sk/json/live5f.json"
        const val PROGRAM_ENDPOINT = "https://www.stvr.sk/program"
        private val SOURCE_ZONE: ZoneId = ZoneId.of("Europe/Bratislava")
    }
}

/**
 * Parses the structured programme cards from STVR's published TV catalogue.
 * This parser consumes programme metadata only; stream resolution remains in
 * the live JSON boundary above.
 */
internal class StvrScheduleParser {
    fun parse(
        html: String,
        date: LocalDate,
        now: Instant = Instant.now(),
    ): List<StvrScheduleProgramme> {
        val document = Jsoup.parse(html, HttpStvrOfficialApi.PROGRAM_ENDPOINT)
        return document
            .select("[data-content-list=list]")
            .flatMap { section ->
                val channelId = channelIdFor(section.classNames()) ?: return@flatMap emptyList()
                buildList {
                    var previousStart: Instant? = null
                    section.select("[data-list-item]").forEach { item ->
                        val isNowPlaying = "event-present" in item.classNames()
                        val parsedStart = parseStart(
                            date = date,
                            raw = item.selectFirst(".programm.time--start")?.text().orEmpty(),
                            isNowPlaying = isNowPlaying,
                            now = now,
                        ) ?: return@forEach
                        val start = if (isNowPlaying || previousStart == null) {
                            parsedStart
                        } else {
                            generateSequence(parsedStart) { candidate ->
                                candidate.plus(java.time.Duration.ofDays(1))
                            }.first { candidate -> candidate.isAfter(previousStart) }
                        }
                        val title = item.selectFirst("h5.title")?.text()?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: return@forEach
                        add(
                            StvrScheduleProgramme(
                                channelId = channelId,
                                title = title,
                                description = item.selectFirst(".perex")?.text()?.trim()
                                    ?.takeIf { it.isNotBlank() },
                                category = item.selectFirst(".genre")?.text()?.trim()
                                    ?.takeIf { it.isNotBlank() },
                                start = start,
                                artworkUrl = item.selectFirst("picture img")?.attr("src")
                                    ?.trim()
                                    ?.takeIf { it.startsWith("https://") },
                            ),
                        )
                        previousStart = start
                    }
                }
            }
            .distinctBy { programme -> "${programme.channelId}:${programme.start}" }
    }

    private fun channelIdFor(classes: Set<String>): String? = when {
        "tv--jednotka" in classes -> StvrTvProvider.JEDNOTKA_ID
        "tv--dvojka" in classes -> StvrTvProvider.DVOJKA_ID
        "tv--24" in classes -> StvrTvProvider.NEWS_24_ID
        "tv--sport" in classes -> StvrTvProvider.SPORT_ID
        else -> null
    }

    private fun parseStart(
        date: LocalDate,
        raw: String,
        isNowPlaying: Boolean,
        now: Instant,
    ): Instant? {
        val match = Regex("(\\d{1,2}):(\\d{2})").find(raw) ?: return null
        val parsedHour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        val hour = if (parsedHour == 24) 0 else parsedHour
        if (hour !in 0..23 || minute !in 0..59) return null
        val baseDate = if (parsedHour == 24) date.plusDays(1) else date
        val candidateDates = if (isNowPlaying) {
            listOf(baseDate.minusDays(1), baseDate, baseDate.plusDays(1))
        } else {
            listOf(baseDate)
        }
        return candidateDates
            .map { candidateDate ->
                java.time.LocalDateTime.of(
                    candidateDate,
                    java.time.LocalTime.of(hour, minute),
                ).atZone(SOURCE_ZONE).toInstant()
            }
            .minByOrNull { candidate ->
                java.time.Duration.between(candidate, now).abs().toMillis()
            }
    }

    private companion object {
        val SOURCE_ZONE: ZoneId = ZoneId.of("Europe/Bratislava")
    }
}

@Serializable
private data class StvrLiveResponse(
    val clip: StvrLiveClip? = null,
)

@Serializable
private data class StvrLiveClip(
    val title: String? = null,
    val titleorig: String? = null,
    val description: String? = null,
    val image: String? = null,
    val dateTimeStart: String? = null,
    val dateTimeStop: String? = null,
    val sources: List<StvrLiveSource> = emptyList(),
)

@Serializable
private data class StvrLiveSource(
    val src: String? = null,
    val type: String? = null,
)
