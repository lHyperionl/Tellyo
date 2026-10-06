package com.tellyo.app.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTelemetryStoreTest {
    @Test
    fun lateAnalyticsCannotRepopulateTelemetryAfterStop() {
        val store = PlaybackTelemetryStore()
        store.begin("old-channel", "HLS")
        store.reset(null)
        store.recordBandwidthEstimate(5_000_000)
        store.recordDroppedFrames(5)
        store.recordFormat(C.TRACK_TYPE_VIDEO, Format.Builder().setWidth(1920).setHeight(1080).build())
        val player = java.lang.reflect.Proxy.newProxyInstance(
            androidx.media3.common.Player::class.java.classLoader,
            arrayOf(androidx.media3.common.Player::class.java),
        ) { _, _, _ -> error("Stopped telemetry must not read the retained player") } as androidx.media3.common.Player
        store.sample(player)
        assertEquals(PlaybackTelemetry(), store.state.value)
    }

    @Test
    fun accumulatesPlaybackAndSelectedFormatTelemetry() {
        val store = PlaybackTelemetryStore()
        store.begin("stvr-jednotka", "HLS")
        store.recordBandwidthEstimate(5_500_000)
        store.recordDroppedFrames(2)
        store.recordDroppedFrames(3)
        store.recordFormat(
            C.TRACK_TYPE_VIDEO,
            Format.Builder()
                .setWidth(1920)
                .setHeight(1080)
                .setAverageBitrate(4_800_000)
                .setSampleMimeType("video/avc")
                .build(),
        )

        val telemetry = store.state.value
        assertEquals("stvr-jednotka", telemetry.channelId)
        assertEquals("HLS", telemetry.protocol)
        assertEquals(1920, telemetry.videoWidth)
        assertEquals(1080, telemetry.videoHeight)
        assertEquals(4_800_000L, telemetry.selectedVideoBitrate)
        assertEquals(5_500_000L, telemetry.bandwidthEstimate)
        assertEquals(5L, telemetry.droppedFrames)
        assertEquals("video/avc", telemetry.videoMimeType)
    }
}
