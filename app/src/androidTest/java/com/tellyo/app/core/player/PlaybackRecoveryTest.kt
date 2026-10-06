package com.tellyo.app.core.player

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.tellyo.app.core.model.StreamSource
import com.tellyo.app.core.model.StreamType
import com.tellyo.app.core.model.VideoQualityPreference
import com.tellyo.app.core.network.NetworkMonitor
import com.tellyo.app.core.network.NetworkStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var player: OfflinePlayer
    private lateinit var client: Media3PlaybackClient
    private val telemetry = PlaybackTelemetryStore()
    private val network = object : NetworkMonitor {
        override val status = MutableStateFlow(NetworkStatus.Available)
    }
    private val source = StreamSource("offline", Uri.parse("https://example.com/live.m3u8"), StreamType.HLS)

    @After fun cleanUp() = main {
        if (::client.isInitialized) client.stop()
        if (::player.isInitialized) player.release()
    }

    @Test fun retryCannotRestartPlaybackUntilHostReturns() {
        main {
            connect()
            runBlocking { client.play(source) }
            player.fail()
            client.onHostStopped()
        }
        SystemClock.sleep(1300)
        main {
            assertEquals(1, player.playCalls)
            client.onHostStarted()
            assertEquals(2, player.playCalls)
        }
    }

    @Test fun stoppedHostBlocksPendingControllerConnectionAndResumesOnce() {
        val connection = CompletableDeferred<Player>()
        main {
            createPlayer()
            client = Media3PlaybackClient(connection, object : PlaybackLogger {
                override fun record(event: PlaybackLogEvent) = Unit
            }, telemetry, network)
            CoroutineScope(Dispatchers.Main.immediate).launch { client.play(source) }
            client.onHostStopped()
            connection.complete(player)
            assertEquals(0, player.playCalls)
            assertFalse(connection.isCancelled)
            client.onHostStarted()
            assertEquals(1, player.playCalls)
        }
    }

    @Test fun closeDuringRetryClearsMediaAndRemainsIdle() {
        main {
            connect()
            runBlocking { client.play(source) }
            player.fail()
            client.stop()
        }
        SystemClock.sleep(1300)
        main {
            assertEquals(1, player.playCalls)
            assertNull(player.currentMediaItem)
            assertEquals(PlaybackState.Idle, client.state.value)
            assertEquals(PlaybackTelemetry(), telemetry.state.value)
        }
    }

    @Test fun switchingAwayFromStableClearsBitrateCapForAutoAndHigh() = main {
        connect()
        client.setVideoQuality(VideoQualityPreference.STABLE)
        assertEquals(3_000_000, player.trackSelectionParameters.maxVideoBitrate)
        client.setVideoQuality(VideoQualityPreference.AUTO)
        assertEquals(androidx.media3.common.MimeTypes.VIDEO_H264,
            player.trackSelectionParameters.preferredVideoMimeTypes.first())
        assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoBitrate)
        assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoWidth)
        client.setVideoQuality(VideoQualityPreference.STABLE)
        client.setVideoQuality(VideoQualityPreference.HIGH)
        assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoBitrate)
        assertTrue(player.trackSelectionParameters.forceHighestSupportedBitrate)
        assertTrue(player.trackSelectionParameters.preferredVideoMimeTypes.isEmpty())
    }

    @Test fun pendingRetryCannotPlayOfflineAndFreshRequestRecovers() {
        main {
            connect()
            runBlocking { client.play(source) }
            player.fail()
            network.status.value = NetworkStatus.Unavailable
        }
        SystemClock.sleep(1300)
        main {
            assertEquals(1, player.playCalls)
            player.fail() // A late loader error must not schedule another offline retry.
            network.status.value = NetworkStatus.Available
            runBlocking { client.play(source.copy(uri = Uri.parse("https://example.com/fresh.m3u8"))) }
            assertEquals(2, player.playCalls)
            assertEquals("https://example.com/fresh.m3u8", player.currentMediaItem?.localConfiguration?.uri.toString())
        }
    }

    @Test fun hostResumeDoesNotStartOfflinePlayback() {
        main {
            connect()
            runBlocking { client.play(source) }
            client.onHostStopped()
            network.status.value = NetworkStatus.Unavailable
            client.onHostStarted()
            assertEquals(1, player.playCalls)
            network.status.value = NetworkStatus.Available
            client.onHostStarted()
            assertEquals(2, player.playCalls)
        }
    }

    @Test fun manualPauseDuringRetryPreventsAutomaticRestart() {
        main {
            connect()
            runBlocking { client.play(source) }
            player.fail()
            player.pause()
            assertEquals(PlaybackState.Paused(source.channelId), client.state.value)
        }
        SystemClock.sleep(1300)
        main { assertEquals(1, player.playCalls) }
    }

    @Test fun manualPauseDuringBufferingIsReportedAsPaused() = main {
        connect()
        runBlocking { client.play(source) }
        player.buffer()
        player.pause()
        assertEquals(PlaybackState.Paused(source.channelId), client.state.value)
        client.onHostStopped()
        client.onHostStarted()
        assertEquals(1, player.playCalls)
    }

    @Test fun pauseBeforeControllerConnectsPreventsPendingPlay() = main {
        val connection = CompletableDeferred<Player>()
        createPlayer()
        client = Media3PlaybackClient(connection, object : PlaybackLogger {
            override fun record(event: PlaybackLogEvent) = Unit
        }, telemetry, network)
        CoroutineScope(Dispatchers.Main.immediate).launch { client.play(source) }
        client.pause()
        connection.complete(player)
        assertEquals(0, player.playCalls)
        assertEquals(PlaybackState.Paused(source.channelId), client.state.value)
    }

    private fun connect() {
        createPlayer()
        client = Media3PlaybackClient(CompletableDeferred(player), object : PlaybackLogger {
            override fun record(event: PlaybackLogEvent) = Unit
        }, telemetry, network)
    }

    private fun createPlayer() {
        player = OfflinePlayer(ExoPlayer.Builder(instrumentation.targetContext).build())
    }

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    /** Real Media3 parameters/media items with deterministic state and no network preparation. */
    private class OfflinePlayer(player: Player) : ForwardingPlayer(player) {
        var playCalls = 0
        private var state = Player.STATE_IDLE
        private var listener: Player.Listener? = null
        override fun addListener(listener: Player.Listener) { this.listener = listener }
        override fun getPlaybackState() = state
        override fun isPlaying() = state == Player.STATE_READY && playWhenReady
        override fun prepare() { state = Player.STATE_READY; listener?.onPlaybackStateChanged(state) }
        override fun play() { playCalls++; super.play(); listener?.onIsPlayingChanged(isPlaying) }
        override fun pause() {
            super.pause()
            listener?.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            listener?.onIsPlayingChanged(false)
        }
        fun buffer() { state = Player.STATE_BUFFERING; listener?.onPlaybackStateChanged(state) }
        override fun stop() { state = Player.STATE_IDLE; super.stop(); listener?.onPlaybackStateChanged(state) }
        fun fail() {
            state = Player.STATE_IDLE
            listener?.onPlayerError(PlaybackException("offline failure", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        }
    }
}
