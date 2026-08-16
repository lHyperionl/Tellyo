package com.tellyo.app.core.model

import android.net.Uri

sealed interface StreamResolution {
    data class Playable(val source: StreamSource) : StreamResolution

    data class ExternalApp(
        val packageName: String?,
        val deepLink: Uri?,
    ) : StreamResolution

    data class Unavailable(val reason: String) : StreamResolution
}
