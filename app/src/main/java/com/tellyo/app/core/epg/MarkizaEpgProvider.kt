package com.tellyo.app.core.epg

import com.tellyo.app.core.model.Program
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

@Singleton
class MarkizaEpgProvider @Inject constructor(
    httpClient: OkHttpClient,
    private val parser: MarkizaScheduleParser,
) : PublishedScheduleEpgProvider(httpClient) {
    override val id: String = "markiza-published-schedule"
    override val pages: List<String> = listOf("https://www.markiza.sk/program")
    override fun parsePage(url: String, html: String): List<Program> = parser.parse(html)
}

class MarkizaScheduleParser @Inject constructor() {
    fun parse(html: String): List<Program> {
        // Read the JSON assignment without executing the page's JavaScript.
        val data = Jsoup.parse(html).select("script").asSequence().map { it.data() }
            .firstOrNull { PAGE_DATA.containsMatchIn(it) } ?: return emptyList()
        val assignment = PAGE_DATA.find(data) ?: return emptyList()
        val root = Json.parseToJsonElement(data.substring(assignment.range.last + 1).trim().removeSuffix(";").trim())
            as? JsonObject ?: return emptyList()
        return buildList {
            for (day in root["program"] as? JsonArray ?: JsonArray(emptyList())) {
                for (channel in (day as? JsonObject)?.get("channels") as? JsonArray ?: continue) {
                    val channelData = channel as? JsonObject ?: continue
                    val channelId = CHANNEL_IDS[channelData.string("id")] ?: continue
                    for (segment in channelData["segments"] as? JsonArray ?: continue) {
                        for (item in (segment as? JsonObject)?.get("items") as? JsonArray ?: continue) {
                            val record = item as? JsonObject ?: continue
                            val title = record.string("title")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                            val start = runCatching { OffsetDateTime.parse(record.string("start_at")).toInstant() }.getOrNull() ?: continue
                            val end = runCatching { OffsetDateTime.parse(record.string("end_at")).toInstant() }.getOrNull() ?: continue
                            if (!end.isAfter(start)) continue
                            add(Program(
                                id = "${channelId}_${start.toEpochMilli()}", channelId = channelId, title = title,
                                description = record.string("description")?.takeIf { it.isNotBlank() }
                                    ?: record.string("short_description")?.takeIf { it.isNotBlank() },
                                start = start, end = end, category = null,
                                artworkUrl = record.string("image")?.takeIf { it.startsWith("https://") },
                            ))
                        }
                    }
                }
            }
        }.distinctBy(Program::id)
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content?.takeUnless { it == "null" }

    private companion object {
        val PAGE_DATA = Regex("\\b(?:var|let|const)\\s+PageData\\s*=\\s*")
        val CHANNEL_IDS = mapOf("markiza" to "markiza", "doma" to "markiza-doma", "dajto" to "markiza-dajto",
            "krimi" to "markiza-krimi", "klasik" to "markiza-klasik")
    }
}
