package com.tellyo.app.core.model

import android.net.Uri
import java.time.Instant

data class StreamSource(
    val channelId: String,
    val uri: Uri,
    val type: StreamType,
    val headers: Map<String, String> = emptyMap(),
    val expiresAt: Instant? = null,
    val isTimeshiftable: Boolean = false,
)

enum class StreamType {
    HLS,
    DASH,
}
