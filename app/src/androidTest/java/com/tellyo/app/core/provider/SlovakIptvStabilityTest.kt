package com.tellyo.app.core.provider

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.tellyo.app.MainActivity
import com.tellyo.app.core.player.PlaybackState
import com.tellyo.app.feature.home.HomeAction
import com.tellyo.app.feature.home.HomeViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Sustained checks use the real Activity, TV video surface and MediaSession buffering policy. */
class SlovakIptvStabilityTest {
    @Test fun playlistChannelsKeepPlayingAcrossMultipleManifestRefreshes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("liveIptvStability") == "true")
        val ids = arguments.getString("stabilityIds")?.split(',') ?: listOf(
            "iptv-ArenaSport1.sk", "iptv-HAHATV.sk", "iptv-TurboTV.sk",
        )
        val durationMs = (arguments.getString("stabilitySeconds")?.toLongOrNull() ?: 90L) * 1_000L
        val report = JSONArray()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            lateinit var model: HomeViewModel
            scenario.onActivity {
                activity = it
                model = ViewModelProvider(it)[HomeViewModel::class.java]
            }
            assertTrue(waitUntil(30_000) { !model.uiState.value.isLoading })
            for (id in ids) {
                val hasPlayed = AtomicBoolean(false)
                val rebuffers = AtomicInteger(0)
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) hasPlayed.set(true)
                        if (state == Player.STATE_BUFFERING && hasPlayed.get()) rebuffers.incrementAndGet()
                    }
                }
                instrumentation.runOnMainSync { model.onAction(HomeAction.PlayChannel(id)) }
                assertTrue("$id did not start: ${model.uiState.value.playbackState}", waitUntil(45_000) {
                    model.uiState.value.playbackState is PlaybackState.Playing
                })
                hasPlayed.set(true)
                var startPosition = 0L
                instrumentation.runOnMainSync {
                    val player = requireNotNull(activity.playbackClient.player.value)
                    if (arguments.getString("diagnosticAudioOnly") == "true") {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
                    }
                    player.addListener(listener)
                    startPosition = player.currentPosition
                }
                try {
                    val deadline = SystemClock.elapsedRealtime() + durationMs
                    var minimumBuffer = Long.MAX_VALUE
                    var previousPosition = startPosition
                    var advancedMs = 0L
                    val samples = JSONArray()
                    var nextSample = 0L
                    while (SystemClock.elapsedRealtime() < deadline) {
                        SystemClock.sleep(500)
                        instrumentation.runOnMainSync {
                            val player = requireNotNull(activity.playbackClient.player.value)
                            // HLS currentPosition is relative to a sliding window:
                            // count progression between samples, excluding window shifts and seeks.
                            advancedMs += (player.currentPosition - previousPosition).coerceIn(0L, 1_000L)
                            previousPosition = player.currentPosition
                            minimumBuffer = minOf(minimumBuffer,
                                player.totalBufferedDuration)
                            if (SystemClock.elapsedRealtime() >= nextSample) {
                                val sample = JSONObject().put("bufferMs", player.totalBufferedDuration)
                                    .put("liveOffsetMs", player.currentLiveOffset)
                                    .put("targetOffsetMs", player.currentMediaItem?.liveConfiguration?.targetOffsetMs)
                                samples.put(sample)
                                android.util.Log.i("IPTVStability", "$id $sample")
                                nextSample = SystemClock.elapsedRealtime() + 5_000
                            }
                        }
                    }
                    report.put(JSONObject().put("channelId", id).put("observedMs", durationMs)
                        .put("videoEnabled", arguments.getString("diagnosticAudioOnly") != "true")
                        .put("advancedMs", advancedMs).put("rebuffers", rebuffers.get())
                        .put("minimumBufferMs", minimumBuffer).put("samples", samples))
                    assertTrue("$id must advance during sustained playback: $report", advancedMs > durationMs * 0.8)
                    assertTrue("$id repeatedly rebuffered: $report", rebuffers.get() <= 1)
                } finally {
                    instrumentation.runOnMainSync {
                        activity.playbackClient.player.value?.removeListener(listener)
                        activity.playbackClient.player.value?.let { player ->
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
                        }
                        model.onAction(HomeAction.ClosePlayer)
                    }
                    File(instrumentation.targetContext.filesDir, "iptv-stability-report.json")
                        .writeText(report.toString(2))
                }
            }
        }
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }
}
