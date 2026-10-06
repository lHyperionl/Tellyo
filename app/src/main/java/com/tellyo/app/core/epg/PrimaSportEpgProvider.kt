package com.tellyo.app.core.epg

import com.tellyo.app.core.model.Program
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

/** Aktuality's published listings for the Slovak Prima editions and Sport 1/2. */
@Singleton
class PrimaSportEpgProvider @Inject constructor(
    httpClient: OkHttpClient,
    private val parser: AktualityScheduleParser,
) : PublishedScheduleEpgProvider(httpClient) {
    override val id: String = "prima-sport-published-schedule"
    override val pages: List<String> = STATIONS.keys.toList()
    override fun parsePage(url: String, html: String): List<Program> = parser.parse(html, STATIONS.getValue(url))

    companion object {
        val STATIONS: Map<String, ScheduleStation> = listOf(
            ScheduleStation("prima-sk", "iptv-PrimaSK.sk", "Prima SK"),
            ScheduleStation("prima-cool-sk", "iptv-PrimaCoolSK.sk", "Prima COOL SK"),
            ScheduleStation("prima-krimi-sk", "iptv-PrimaKrimiSK.sk", "Prima KRIMI SK"),
            ScheduleStation("prima-love-sk", "iptv-PrimaloveSK.sk", "Prima LOVE SK"),
            ScheduleStation("sport1", "iptv-Sport1.cz", "Sport1"),
            ScheduleStation("sport2", "iptv-Sport2.cz", "Sport2"),
        ).associateBy { "https://tv-program.aktuality.sk/stanica/${it.slug}/" }
    }
}

data class ScheduleStation(val slug: String, val channelId: String, val name: String)

class AktualityScheduleParser @Inject constructor() {
    fun parse(html: String, station: ScheduleStation): List<Program> {
        val page = Jsoup.parse(html)
        // Unknown station URLs redirect to a multi-channel homepage. Never import that as this station.
        if (page.selectFirst(".channel-title")?.text()?.trim() != station.name) return emptyList()
        return page.select("script").flatMap { script ->
            RECORD.findAll(script.data()).mapNotNull { record ->
                val values = FIELD.findAll(record.groupValues[1]).associate {
                    it.groupValues[1] to decodeJsString(it.groupValues[2])
                }
                if (values["channel_title"] != station.name) return@mapNotNull null
                val title = values["title"]?.plainText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val start = values["start_time"]?.toLongOrNull()?.let {
                    runCatching { Instant.ofEpochSecond(it) }.getOrNull()
                } ?: return@mapNotNull null
                val minutes = values["length"]?.toLongOrNull()?.takeIf { it in 1..1440 } ?: return@mapNotNull null
                val end = runCatching { start.plusSeconds(minutes * 60) }.getOrNull() ?: return@mapNotNull null
                Program(id = "${station.channelId}_${start.toEpochMilli()}", channelId = station.channelId,
                    title = title, description = values["desc"]?.plainText()?.takeIf { it.isNotBlank() },
                    start = start, end = end, category = values["category"]?.plainText()?.takeIf { it.isNotBlank() },
                    artworkUrl = values["image"]?.takeIf { it.startsWith("https://") })
            }.toList()
        }.distinctBy(Program::id).sortedBy(Program::start)
    }

    private fun String.plainText(): String =
        Jsoup.parseBodyFragment(org.jsoup.parser.Parser.unescapeEntities(this, false)).text()

    /** Decode string literals as data; never evaluate the publisher's JavaScript. */
    private fun decodeJsString(value: String): String = ESCAPE.replace(value) { match ->
        val escape = match.groupValues[1]
        when {
            escape == "n" -> "\n"
            escape == "r" -> "\r"
            escape == "t" -> "\t"
            escape == "b" -> "\b"
            escape == "f" -> "\u000C"
            escape.startsWith("u") || escape.startsWith("x") -> escape.substring(1).toInt(16).toChar().toString()
            else -> escape
        }
    }

    private companion object {
        val RECORD = Regex("program_desc\\[\\d+\\]\\s*=\\s*\\{(.*?)\\};", RegexOption.DOT_MATCHES_ALL)
        val FIELD = Regex("^\\s*(\\w+)\\s*:\\s*'((?:\\\\.|[^'\\\\])*)'\\s*,?\\s*$", RegexOption.MULTILINE)
        val ESCAPE = Regex("\\\\(u[0-9a-fA-F]{4}|x[0-9a-fA-F]{2}|.)")
    }
}
