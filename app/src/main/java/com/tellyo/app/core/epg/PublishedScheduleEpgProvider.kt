package com.tellyo.app.core.epg

import com.tellyo.app.core.model.Program
import com.tellyo.app.core.network.readLimitedBytes
import java.io.IOException
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Public schedule metadata only. Failed pages do not declare replacement coverage. */
abstract class PublishedScheduleEpgProvider(private val httpClient: OkHttpClient) : EpgProvider {
    override val priority: Int = 10
    override val minimumRefreshInterval: Duration = Duration.ofHours(6)
    protected abstract val pages: List<String>
    protected abstract fun parsePage(url: String, html: String): List<Program>

    override suspend fun fetchXml(): String = fetch().xml

    override suspend fun fetch(): EpgFetchResult = withContext(Dispatchers.IO) {
        val programs = mutableListOf<Program>()
        val failures = mutableListOf<Exception>()
        for (url in pages) {
            currentCoroutineContext().ensureActive()
            try {
                val request = okhttp3.Request.Builder().url(url)
                    .header("Accept", "text/html")
                    .header("User-Agent", "Tellyo AndroidTV").build()
                val parsed = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Schedule returned HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Schedule returned no body")
                    parsePage(url, body.readLimitedBytes(8L * 1024 * 1024).toString(Charsets.UTF_8))
                }
                require(parsed.isNotEmpty()) { "Schedule contained no valid programmes: $url" }
                programs += parsed
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += error
            }
        }
        if (programs.isEmpty()) throw IOException("No published schedules available for $id", failures.firstOrNull())
        val unique = programs.distinctBy(Program::id).sortedWith(compareBy<Program> { it.channelId }.thenBy { it.start })
        EpgFetchResult(
            xml = unique.toScheduleXmlTv(),
            replacementWindows = unique.groupBy(Program::channelId).map { (channelId, records) ->
                EpgReplacementWindow(channelId, records.minOf { it.start }, records.maxOf { it.end })
            },
        )
    }
}

internal fun List<Program>.toScheduleXmlTv(): String = buildString {
    val timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss xx").withZone(ZoneOffset.UTC)
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>")
    for (program in this@toScheduleXmlTv) {
        append("<programme channel=\"").append(program.channelId.scheduleXmlEscape())
        append("\" start=\"").append(timestamp.format(program.start))
        append("\" stop=\"").append(timestamp.format(program.end)).append("\">")
        append("<title>").append(program.title.scheduleXmlEscape()).append("</title>")
        program.description?.let { append("<desc>").append(it.scheduleXmlEscape()).append("</desc>") }
        program.category?.let { append("<category>").append(it.scheduleXmlEscape()).append("</category>") }
        program.artworkUrl?.let { append("<icon src=\"").append(it.scheduleXmlEscape()).append("\"/>") }
        append("</programme>")
    }
    append("</tv>")
}

private fun String.scheduleXmlEscape(): String = replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
