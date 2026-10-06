package com.tellyo.app.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackLoggerTest {
    @Test
    fun formatsRedactedStructuredFields() {
        val line = PlaybackLogEvent(
            name = "player error",
            channelId = "stvr-jednotka",
            streamType = "HLS",
            attempt = 2,
            detail = "HTTP 403? token=secret",
            timestampMillis = 123L,
        ).toStructuredLine()

        assertEquals(
            "event=player_error ts=123 channel=stvr-jednotka type=HLS attempt=2 detail=HTTP_403_token:redacted",
            line,
        )
        assertFalse(line.contains("token="))
        assertFalse(line.contains("secret"))
    }

    @Test fun signedUrlsAndAuthenticationHeadersAreRedactedBeforeFormatting() {
        val line = PlaybackLogEvent(name = "error", detail =
            "Request https://user:password@example.com/live?sig=signed-value Authorization: Bearer credential-value")
            .toStructuredLine()
        assertFalse(line.contains("password"))
        assertFalse(line.contains("signed-value"))
        assertFalse(line.contains("credential-value"))
        org.junit.Assert.assertTrue(line.contains("redacted_url"))
    }
}
