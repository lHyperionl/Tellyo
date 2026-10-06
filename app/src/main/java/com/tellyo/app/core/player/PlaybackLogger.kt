package com.tellyo.app.core.player

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/** Redacted, structured playback event suitable for Logcat and later export. */
data class PlaybackLogEvent(
    val name: String,
    val channelId: String? = null,
    val streamType: String? = null,
    val attempt: Int? = null,
    val detail: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
) {
    fun toStructuredLine(): String = buildString {
        append("event=").append(sanitize(name))
        append(" ts=").append(timestampMillis)
        channelId?.let { append(" channel=").append(sanitize(it)) }
        streamType?.let { append(" type=").append(sanitize(it)) }
        attempt?.let { append(" attempt=").append(it) }
        detail?.let { append(" detail=").append(sanitize(it)) }
    }

    private fun sanitize(value: String): String = value
        // Removing punctuation alone leaves credentials readable. Redact before
        // flattening fields so URL query tokens and headers never reach Logcat.
        .replace(URL, "redacted_url")
        .replace(SENSITIVE_HEADER) { "${it.groupValues[1]}:redacted" }
        .replace(SENSITIVE_VALUE) { "${it.groupValues[1]}:redacted" }
        .replace(Regex("\\s+"), "_")
        .replace(Regex("[^A-Za-z0-9_.:-]"), "")
        .take(MAX_FIELD_LENGTH)

    private companion object {
        const val MAX_FIELD_LENGTH = 180
        val URL = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
        val SENSITIVE_HEADER = Regex("\\b(authorization|cookie|set-cookie)\\s*[:=]\\s*[^\\r\\n]+", RegexOption.IGNORE_CASE)
        val SENSITIVE_VALUE = Regex("\\b(token|access_token|refresh_token|api[_-]?key|password|secret|signature|sig)\\s*[:=]\\s*[^\\s,;&]+", RegexOption.IGNORE_CASE)
    }
}

interface PlaybackLogger {
    fun record(event: PlaybackLogEvent)
}

@Singleton
class AndroidPlaybackLogger @Inject constructor() : PlaybackLogger {
    override fun record(event: PlaybackLogEvent) {
        Log.i(TAG, event.toStructuredLine())
    }

    private companion object {
        const val TAG = "TellyoPlayback"
    }
}
