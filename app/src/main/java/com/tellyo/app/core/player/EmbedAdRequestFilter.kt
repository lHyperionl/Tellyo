package com.tellyo.app.core.player

import java.net.URI
import java.util.Locale

/**
 * A small, local network filter for separate advertising requests, not the
 * uBlock Origin extension or its filter engine. Keep player SDKs, consent,
 * authentication and media delivery working; never match arbitrary URL text.
 */
internal object EmbedAdRequestFilter {
    // Markiza publishes a DoubleClick VAST pre-roll; JOJ publishes Smart AdServer
    // VAST tags. These are dedicated advertising hosts, not stream CDNs.
    private val adDomains = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "smartadserver.com",
        "teads.tv",
    )

    fun shouldBlock(url: String, isMainFrame: Boolean, enabled: Boolean): Boolean {
        if (!enabled || isMainFrame) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return false
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        return adDomains.any { host == it || host.endsWith(".$it") }
    }
}
