package com.tellyo.app.core.provider

import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.StreamType
import java.net.URI

data class M3uChannel(
    val channel: Channel,
    val url: String,
    val type: StreamType,
    val headers: Map<String, String>,
)

/** Extended M3U metadata and the per-stream HTTP options used by VLC playlists. */
object M3uPlaylistParser {
    private val attribute = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(raw: String, providerId: String = SlovakIptvProvider.PROVIDER_ID): List<M3uChannel> {
        require(raw.trimStart('\uFEFF', ' ', '\r', '\n', '\t').startsWith("#EXTM3U")) {
            "Response is not an extended M3U playlist"
        }
        var metadata: Map<String, String>? = null
        var displayName = ""
        val headers = linkedMapOf<String, String>()
        val entries = linkedMapOf<String, M3uChannel>()
        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF:") -> {
                    metadata = attribute.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    // Commas inside quoted attributes are not the display-name separator.
                    var quoted = false
                    val separator = line.indexOfFirst { char ->
                        if (char == '"') quoted = !quoted
                        char == ',' && !quoted
                    }
                    displayName = if (separator >= 0) line.substring(separator + 1).trim() else ""
                    headers.clear()
                    metadata?.forEach { (option, value) ->
                        val key = httpHeaderName(option)
                        if (key != null && value.isNotBlank() && '\r' !in value && '\n' !in value) {
                            headers[key] = value
                        }
                    }
                }
                line.startsWith("#EXTVLCOPT:") -> {
                    val option = line.removePrefix("#EXTVLCOPT:")
                    val key = httpHeaderName(option.substringBefore('='))
                    val value = option.substringAfter('=', "")
                    if (key != null && value.isNotBlank() && '\r' !in value && '\n' !in value) {
                        headers[key] = value
                    }
                }
                line.isNotBlank() && !line.startsWith('#') -> {
                    val attributes = metadata
                    metadata = null
                    val uri = runCatching { URI(line) }.getOrNull()
                    val type = when {
                        uri?.path?.endsWith(".m3u8", ignoreCase = true) == true -> StreamType.HLS
                        uri?.path?.endsWith(".mpd", ignoreCase = true) == true -> StreamType.DASH
                        else -> null
                    }
                    if (attributes != null && type != null &&
                        uri?.scheme?.lowercase() in setOf("http", "https") &&
                        !uri?.host.isNullOrBlank() && uri?.userInfo == null
                    ) {
                        val upstreamId = attributes["tvg-id"]?.takeIf(String::isNotBlank)
                            ?: uri.toString()
                        val channelId = "iptv-$upstreamId"
                        val name = attributes["tvg-name"]?.takeIf(String::isNotBlank) ?: displayName
                        if (name.isNotBlank() && channelId !in entries) {
                            val category = when (attributes["group-title"]?.lowercase()) {
                                "news", "legislative" -> ChannelCategory.NEWS
                                "sports" -> ChannelCategory.SPORTS
                                "culture", "documentary", "religious" -> ChannelCategory.CULTURE
                                else -> ChannelCategory.GENERAL
                            }
                            entries[channelId] = M3uChannel(
                                channel = Channel(
                                    id = channelId,
                                    number = 100 + entries.size,
                                    name = name,
                                    shortName = name,
                                    logoUrl = attributes["tvg-logo"]?.takeIf {
                                        runCatching { URI(it) }.getOrNull()?.scheme == "https"
                                    },
                                    providerId = providerId,
                                    category = category,
                                    enabled = true,
                                ),
                                url = line,
                                type = type,
                                headers = headers.toMap(),
                            )
                        }
                    }
                    headers.clear()
                }
            }
        }
        require(entries.isNotEmpty()) { "Playlist contains no supported HLS/DASH channels" }
        return entries.values.toList()
    }

    private fun httpHeaderName(option: String): String? = when (option.lowercase()) {
        "http-user-agent" -> "User-Agent"
        "http-referrer", "http-referer" -> "Referer"
        "http-origin" -> "Origin"
        else -> null
    }
}
