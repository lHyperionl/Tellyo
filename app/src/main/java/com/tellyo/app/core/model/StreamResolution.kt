package com.tellyo.app.core.model

import android.net.Uri

sealed interface StreamResolution {
    data class Playable(val source: StreamSource) : StreamResolution

    /**
     * Broadcaster-owned web player, either a published embed or its live page.
     *
     * This is intentionally distinct from [Playable]: Tellyo does not copy a
     * signed manifest, suppress broadcaster advertising, or own the web
     * player's authentication/session lifecycle.
     */
    data class OfficialEmbed(val url: String) : StreamResolution

    data class ExternalApp(
        val packageName: String?,
        val deepLink: Uri?,
    ) : StreamResolution

    data class Unavailable(val reason: String) : StreamResolution
}
