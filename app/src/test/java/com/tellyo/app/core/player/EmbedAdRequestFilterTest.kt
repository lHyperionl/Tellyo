package com.tellyo.app.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbedAdRequestFilterTest {
    @Test fun blocksPublishedPrerollAdHostsAndSubdomains() {
        listOf(
            "https://securepubads.g.doubleclick.net/gampad/ads?output=vast",
            "https://videoapi.smartadserver.com/ac?out=vast4.2",
            "https://adapi.smartadserver.com/",
            "https://a.teads.tv/analytics/tag.js",
            "https://DOUBLECLICK.NET./ad",
        ).forEach { assertTrue(it, EmbedAdRequestFilter.shouldBlock(it, false, true)) }
    }

    @Test fun leavesPlayerLoginConsentAndMediaRequestsAlone() {
        listOf(
            "https://player-theo.cms.markiza.sk/embed.js",
            "https://imasdk.googleapis.com/js/sdkloader/ima3.js",
            "https://media.joj.sk/embed/player",
            "https://www.joj.sk/live/1-joj",
            "https://sdk.tiv.io/player.js",
            "https://api.tiv.io/login",
            "https://cdn.example.com/live.m3u8?ad=1",
            "https://cdn.example.com/segment.ts",
            "https://sdk.privacy-center.org/consent.js",
            "https://doubleclick.net.example.com/ad",
            "https://notdoubleclick.net/ad",
            "https://example.com/?url=https://doubleclick.net/ads",
            "blob:https://doubleclick.net/video",
            "malformed url",
        ).forEach { assertFalse(it, EmbedAdRequestFilter.shouldBlock(it, false, true)) }
    }

    @Test fun respectsDisableSettingAndDoesNotBlockTopLevelNavigation() {
        val url = "https://securepubads.g.doubleclick.net/gampad/ads"
        assertFalse(EmbedAdRequestFilter.shouldBlock(url, false, false))
        assertFalse(EmbedAdRequestFilter.shouldBlock(url, true, true))
    }
}
