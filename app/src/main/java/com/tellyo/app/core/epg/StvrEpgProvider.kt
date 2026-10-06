package com.tellyo.app.core.epg

import com.tellyo.app.core.provider.StvrOfficialApi
import com.tellyo.app.core.provider.StvrScheduleProgramme
import com.tellyo.app.core.provider.StvrTvProvider
import com.tellyo.app.core.provider.ChannelCatalogRepository
import com.tellyo.app.core.model.Channel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Remote STVR EPG source combining the published schedule with live metadata. */
@Singleton
class StvrEpgProvider @Inject constructor(
    private val officialApi: StvrOfficialApi,
    private val channelCatalog: ChannelCatalogRepository,
) : EpgProvider {
    override val id: String = PROVIDER_ID
    override val minimumRefreshInterval: Duration = Duration.ofMinutes(5)
    private val fetchMutex = Mutex()
    private var scheduleDate: LocalDate? = null
    private var scheduleFetchedAt: Instant? = null
    private var cachedSchedule: List<RemoteProgramme> = emptyList()

    override suspend fun fetchXml(): String = fetch().xml

    override suspend fun fetch(): EpgFetchResult = fetchMutex.withLock {
        val today = LocalDate.now(SOURCE_ZONE)
        val now = Instant.now()
        val scheduleIsFresh = scheduleDate == today &&
            scheduleFetchedAt?.let { Duration.between(it, now) < Duration.ofHours(6) } == true
        if (!scheduleIsFresh) {
            try {
                val updated = officialApi.fetchSchedule(today, days = 2).toRemoteProgrammes()
                if (updated.isNotEmpty()) {
                    cachedSchedule = updated
                    scheduleDate = today
                    scheduleFetchedAt = now
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the schedule while live metadata is still available.
            }
        }
        val scheduledRecords = cachedSchedule

        val stvrChannels = channelCatalog.getChannels()
            .filter { it.providerId == StvrTvProvider.PROVIDER_ID }
            .filter(Channel::enabled)
        val liveRecords = buildList {
            for (channel in stvrChannels) {
                currentCoroutineContext().ensureActive()
                try {
                    val live = officialApi.fetchLive(channel)
                    val start = live.start
                    val end = live.end
                    val title = live.programTitle
                    if (start != null && end != null && end.isAfter(start) && !title.isNullOrBlank()) {
                        add(
                            RemoteProgramme(
                                channelId = channel.id,
                                title = title,
                                description = live.description,
                                category = null,
                                start = start,
                                end = end,
                                artworkUrl = live.artworkUrl,
                            ),
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // A single unavailable channel must not hide the other live channels.
                }
            }
        }
        val records = mergeLiveMetadata(scheduledRecords, liveRecords)
        require(records.isNotEmpty()) { "STVR returned no current programme data" }

        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<tv generator-info-name=\"Tellyo official STVR programme sources\">")
            stvrChannels.forEach { channel ->
                append("<channel id=\"")
                append(xmlEscape(channel.id))
                append("\"><display-name>")
                append(xmlEscape(channel.name))
                append("</display-name></channel>")
            }
            records.forEach { programme ->
                append("<programme channel=\"")
                append(xmlEscape(programme.channelId))
                append("\" start=\"")
                append(TIMESTAMP_FORMATTER.format(programme.start))
                append("\" stop=\"")
                append(TIMESTAMP_FORMATTER.format(programme.end))
                append("\"><title>")
                append(xmlEscape(programme.title))
                append("</title>")
                programme.description?.takeIf { it.isNotBlank() }?.let { description ->
                    append("<desc>")
                    append(xmlEscape(description))
                    append("</desc>")
                }
                programme.category?.takeIf { it.isNotBlank() }?.let { category ->
                    append("<category>")
                    append(xmlEscape(category))
                    append("</category>")
                }
                programme.artworkUrl?.takeIf { it.isNotBlank() }?.let { artworkUrl ->
                    append("<icon src=\"")
                    append(xmlEscape(artworkUrl))
                    append("\"/>")
                }
                append("</programme>")
            }
            append("</tv>")
        }
        EpgFetchResult(
            xml = xml,
            replacementWindows = scheduledRecords.groupBy { it.channelId }.map { (id, programs) ->
                EpgReplacementWindow(id, programs.minOf { it.start }, programs.maxOf { it.end })
            },
        )
    }

    private fun List<StvrScheduleProgramme>.toRemoteProgrammes(): List<RemoteProgramme> =
        groupBy { it.channelId }
            .flatMap { (channelId, channelPrograms) ->
                val sorted = channelPrograms.sortedBy { it.start }
                sorted.mapIndexed { index, programme ->
                    val nextStart = sorted.getOrNull(index + 1)?.start
                    RemoteProgramme(
                        channelId = channelId,
                        title = programme.title,
                        description = programme.description,
                        category = programme.category,
                        start = programme.start,
                        end = nextStart?.takeIf { it.isAfter(programme.start) }
                            ?: programme.start.plus(Duration.ofHours(2)),
                        artworkUrl = programme.artworkUrl,
                    )
                }
            }

    private fun mergeLiveMetadata(
        scheduled: List<RemoteProgramme>,
        live: List<RemoteProgramme>,
    ): List<RemoteProgramme> {
        val merged = scheduled.toMutableList()
        live.forEach { liveProgramme ->
            val currentIndex = merged.indexOfFirst { scheduledProgramme ->
                scheduledProgramme.channelId == liveProgramme.channelId &&
                    scheduledProgramme.start <= liveProgramme.start &&
                    scheduledProgramme.end.isAfter(liveProgramme.start)
            }
            if (currentIndex >= 0) {
                val scheduledProgramme = merged[currentIndex]
                merged[currentIndex] = scheduledProgramme.copy(
                    title = liveProgramme.title,
                    description = liveProgramme.description ?: scheduledProgramme.description,
                    start = liveProgramme.start,
                    end = liveProgramme.end,
                    artworkUrl = liveProgramme.artworkUrl ?: scheduledProgramme.artworkUrl,
                )
            } else {
                merged += liveProgramme
            }
        }
        return merged
            .sortedWith(compareBy<RemoteProgramme> { it.channelId }.thenBy { it.start })
            .distinctBy { programme -> "${programme.channelId}:${programme.start}" }
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private data class RemoteProgramme(
        val channelId: String,
        val title: String,
        val description: String?,
        val category: String?,
        val start: Instant,
        val end: Instant,
        val artworkUrl: String?,
    )

    companion object {
        const val PROVIDER_ID = "stvr-live"
        private val SOURCE_ZONE: ZoneId = ZoneId.of("Europe/Bratislava")
        private val TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss xx")
            .withZone(ZoneOffset.UTC)
    }
}
