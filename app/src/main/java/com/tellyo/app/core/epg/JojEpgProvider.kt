package com.tellyo.app.core.epg

import com.tellyo.app.core.model.Program
import com.tellyo.app.core.network.readLimitedBytes
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Official public JOJ programme API, used only for EPG metadata. */
@Singleton
class JojEpgProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    private val parser: JojProgrammeApiParser,
) : EpgProvider {
    override val id: String = "joj-program-api"
    override val priority: Int = 20
    override val minimumRefreshInterval: Duration = CACHE_DURATION

    private val cacheMutex = Mutex()
    private var cachedDate: LocalDate? = null
    private var cachedAt: Instant? = null
    private var cachedResult: EpgFetchResult? = null

    override suspend fun fetchXml(): String = fetch().xml

    override suspend fun fetch(): EpgFetchResult = cacheMutex.withLock {
        val today = LocalDate.now(SOURCE_ZONE)
        val now = Instant.now()
        val validCache = cachedDate == today &&
            cachedAt?.let { Duration.between(it, now) < CACHE_DURATION } == true
        if (validCache) return@withLock requireNotNull(cachedResult)

        val windows = mutableListOf<EpgReplacementWindow>()
        val programs = withContext(Dispatchers.IO) {
            buildList {
                repeat(GUIDE_DAYS) { dayOffset ->
                    currentCoroutineContext().ensureActive()
                    val date = today.plusDays(dayOffset.toLong())
                    try {
                        val dayPrograms = parser.parse(fetchDate(date))
                        addAll(dayPrograms)
                        dayPrograms.map(Program::channelId).distinct().forEach { channelId ->
                            windows += EpgReplacementWindow(
                                channelId,
                                date.atStartOfDay(SOURCE_ZONE).toInstant(),
                                date.plusDays(1).atStartOfDay(SOURCE_ZONE).toInstant(),
                            )
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (dayOffset == 0) throw error
                        // A missing future day must not hide today's valid guide.
                    }
                }
            }
        }.distinctBy(Program::id)
            .sortedWith(compareBy<Program> { it.channelId }.thenBy(Program::start))

        require(programs.isNotEmpty()) { "JOJ programme API returned no valid programmes" }
        val xml = programs.toXmlTv()
        cachedDate = today
        cachedAt = now
        EpgFetchResult(xml, windows).also { cachedResult = it }
    }

    private fun fetchDate(date: LocalDate): String {
        val url = API_URL.toHttpUrl().newBuilder()
            .addQueryParameter("date", date.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Tellyo AndroidTV")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("JOJ programme API returned HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("JOJ programme API returned no body")
            val bytes = body.readLimitedBytes(MAX_RESPONSE_BYTES.toLong())
            bytes.toString(Charsets.UTF_8)
        }
    }

    private fun List<Program>.toXmlTv(): String {
        val programmes = this
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<tv generator-info-name=\"Official JOJ programme API\">")
            CHANNEL_NAMES.forEach { (channelId, channelName) ->
                append("<channel id=\"")
                append(channelId.xmlEscape())
                append("\"><display-name>")
                append(channelName.xmlEscape())
                append("</display-name></channel>")
            }
            programmes.forEach { programme ->
                append("<programme channel=\"")
                append(programme.channelId.xmlEscape())
                append("\" start=\"")
                append(TIMESTAMP_FORMATTER.format(programme.start))
                append("\" stop=\"")
                append(TIMESTAMP_FORMATTER.format(programme.end))
                append("\"><title>")
                append(programme.title.xmlEscape())
                append("</title>")
                programme.description?.takeIf(String::isNotBlank)?.let {
                    append("<desc>")
                    append(it.xmlEscape())
                    append("</desc>")
                }
                programme.artworkUrl?.let {
                    append("<icon src=\"")
                    append(it.xmlEscape())
                    append("\"/>")
                }
                append("</programme>")
            }
            append("</tv>")
        }
    }

    private fun String.xmlEscape(): String = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private companion object {
        const val API_URL = "https://www.joj.sk/api/v1/program/details"
        const val GUIDE_DAYS = 9
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        val SOURCE_ZONE: ZoneId = ZoneId.of("Europe/Bratislava")
        val CACHE_DURATION: Duration = Duration.ofHours(6)
        val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss xx")
            .withZone(ZoneOffset.UTC)
        val CHANNEL_NAMES: Map<String, String> = linkedMapOf(
            "joj" to "JOJ",
            "joj-plus" to "JOJ Plus",
            "joj-krimi" to "JOJ Krimi",
            "joj-24" to "JOJ 24",
        )
    }
}

@Singleton
class JojProgrammeApiParser @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): List<Program> {
        val programmesByChannel = json.decodeFromString<Map<String, List<JojProgrammeDto>>>(payload)
        return buildList {
            programmesByChannel.forEach { (apiChannelId, programmes) ->
                val tellyoChannelId = CHANNEL_ID_MAP[apiChannelId] ?: return@forEach
                programmes.forEach { dto ->
                    val title = dto.title.trim().takeIf(String::isNotEmpty) ?: return@forEach
                    val start = dto.startAt.toInstantOrNull() ?: return@forEach
                    val end = dto.endAt.toInstantOrNull() ?: return@forEach
                    if (!end.isAfter(start)) return@forEach
                    add(
                        Program(
                            id = "${tellyoChannelId}_${start.toEpochMilli()}",
                            channelId = tellyoChannelId,
                            title = title,
                            description = dto.description?.trim()?.takeIf(String::isNotEmpty)
                                ?: dto.shortDescription?.trim()?.takeIf(String::isNotEmpty),
                            start = start,
                            end = end,
                            category = null,
                            artworkUrl = dto.image?.trim()?.takeIf { it.startsWith("https://") },
                        ),
                    )
                }
            }
        }.distinctBy(Program::id)
    }

    private fun String.toInstantOrNull(): Instant? = runCatching {
        OffsetDateTime.parse(this).toInstant()
    }.getOrNull()

    companion object {
        val CHANNEL_ID_MAP: Map<String, String> = mapOf(
            "1" to "joj",
            "2" to "joj-plus",
            "3" to "joj-krimi",
            "16" to "joj-24",
        )
    }
}

@Serializable
private data class JojProgrammeDto(
    val id: Long,
    val title: String,
    val description: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String,
    val image: String? = null,
)
