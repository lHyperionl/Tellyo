package com.tellyo.app.core.epg

import com.tellyo.app.core.model.Program
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

@Singleton
class Ta3EpgProvider @Inject constructor(
    httpClient: OkHttpClient,
    private val parser: Ta3ScheduleParser,
) : PublishedScheduleEpgProvider(httpClient) {
    override val id: String = "ta3-published-schedule"
    override val pages: List<String> = listOf("https://www.ta3.com/program")
    override fun parsePage(url: String, html: String): List<Program> = parser.parse(html)
}

class Ta3ScheduleParser @Inject constructor() {
    fun parse(html: String): List<Program> = Jsoup.parse(html)
        .select(".program_list .program_item[data-timestart][data-timeend]").mapNotNull { item ->
            val start = item.attr("data-timestart").toLongOrNull()?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() }
                ?: return@mapNotNull null
            val end = item.attr("data-timeend").toLongOrNull()?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() }
                ?: return@mapNotNull null
            val title = item.selectFirst(".program_name")?.text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!end.isAfter(start)) return@mapNotNull null
            Program(id = "ta3_${start.toEpochMilli()}", channelId = "ta3", title = title,
                description = item.selectFirst(".program_description")?.text()?.takeIf { it.isNotBlank() },
                start = start, end = end, category = null, artworkUrl = null)
        }.distinctBy(Program::id)
}
