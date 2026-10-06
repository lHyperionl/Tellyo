package com.tellyo.app.feature.player

import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** No broadcaster/network dependency: exercises media events and Compose/lifecycle cleanup. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class NativePlaybackKeepScreenOnTest {
    @get:Rule val rule = createComposeRule()
    private val players = mutableListOf<StatePlayer>()
    private lateinit var hostView: View
    private lateinit var owner: TestOwner
    private val visible = mutableStateOf(true)
    private val selectedPlayer = mutableStateOf<Player?>(null)

    @After fun releasePlayers() {
        rule.runOnIdle {
            visible.value = false
        }
        rule.waitForIdle()
        rule.runOnIdle { players.forEach { it.release() } }
    }

    @Test fun playbackAndBufferingStayAwakeButPauseStopEndAndSuppressionRelease() {
        val player = mount()
        rule.runOnIdle {
            assertFalse(hostView.keepScreenOn)
            player.change(Player.STATE_BUFFERING, true)
            assertTrue(hostView.keepScreenOn)
            player.change(Player.STATE_READY, true)
            assertTrue(hostView.keepScreenOn)
            player.change(Player.STATE_BUFFERING, true)
            assertTrue("Rebuffering must not allow ambient mode", hostView.keepScreenOn)
            player.change(Player.STATE_BUFFERING, false)
            assertFalse(hostView.keepScreenOn)
            player.change(Player.STATE_READY, false)
            assertFalse(hostView.keepScreenOn)
            player.change(Player.STATE_READY, true)
            assertTrue(hostView.keepScreenOn)
            player.change(Player.STATE_READY, true, Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
            assertFalse(hostView.keepScreenOn)
            player.change(Player.STATE_READY, true)
            assertTrue(hostView.keepScreenOn)
            player.change(Player.STATE_ENDED, true)
            assertFalse(hostView.keepScreenOn)
            player.change(Player.STATE_READY, true)
            player.change(Player.STATE_IDLE, true)
            assertFalse("Stopped/error player must release the screen", hostView.keepScreenOn)
        }
    }

    @Test fun backgroundAndLeavingPlayerReleaseWhileReturnReacquires() {
        val player = mount()
        rule.runOnIdle {
            player.change(Player.STATE_READY, true)
            assertTrue(hostView.keepScreenOn)
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            assertFalse(hostView.keepScreenOn)
            player.change(Player.STATE_BUFFERING, true)
            assertFalse("Media events must not keep a background host awake", hostView.keepScreenOn)
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            assertTrue(hostView.keepScreenOn)
            visible.value = false
        }
        rule.runOnIdle {
            assertFalse(hostView.keepScreenOn)
            assertTrue(player.listeners.isEmpty())
            player.change(Player.STATE_READY, true)
            assertFalse("A disposed player's events must have no effect", hostView.keepScreenOn)
        }
    }

    @Test fun replacingOrDisconnectingPlayerRemovesOldListener() {
        val old = mount()
        lateinit var replacement: StatePlayer
        rule.runOnIdle {
            old.change(Player.STATE_READY, true)
            replacement = newPlayer()
            selectedPlayer.value = replacement
        }
        rule.runOnIdle {
            assertTrue(old.listeners.isEmpty())
            assertFalse(hostView.keepScreenOn)
            old.change(Player.STATE_BUFFERING, true)
            assertFalse(hostView.keepScreenOn)
            replacement.change(Player.STATE_READY, true)
            assertTrue(hostView.keepScreenOn)
            selectedPlayer.value = null
        }
        rule.runOnIdle {
            assertFalse(hostView.keepScreenOn)
            assertTrue(replacement.listeners.isEmpty())
        }
    }

    @Test fun alreadyPlayingPlayerAcquiresImmediately() {
        mount(initiallyPlaying = true)
        rule.runOnIdle { assertTrue(hostView.keepScreenOn) }
    }

    private fun mount(initiallyPlaying: Boolean = false): StatePlayer {
        lateinit var player: StatePlayer
        rule.runOnIdle {
            owner = TestOwner().apply { lifecycle.currentState = Lifecycle.State.STARTED }
            player = newPlayer()
            if (initiallyPlaying) player.change(Player.STATE_READY, true)
            selectedPlayer.value = player
        }
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (visible.value) NativePlaybackKeepScreenOn(selectedPlayer.value)
            }
        }
        rule.waitForIdle()
        return player
    }

    private fun newPlayer() = StatePlayer(
        ExoPlayer.Builder(InstrumentationRegistry.getInstrumentation().targetContext).build(),
    ).also(players::add)

    private class TestOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }

    private class StatePlayer(delegate: Player) : ForwardingPlayer(delegate) {
        val listeners = mutableSetOf<Player.Listener>()
        private var state = Player.STATE_IDLE
        private var requested = false
        private var suppression = Player.PLAYBACK_SUPPRESSION_REASON_NONE
        override fun addListener(listener: Player.Listener) { listeners.add(listener) }
        override fun removeListener(listener: Player.Listener) { listeners.remove(listener) }
        override fun getPlaybackState() = state
        override fun getPlayWhenReady() = requested
        override fun getPlaybackSuppressionReason() = suppression

        fun change(state: Int, playWhenReady: Boolean, suppression: Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
            this.state = state
            requested = playWhenReady
            this.suppression = suppression
            val events = Player.Events(FlagSet.Builder().addAll(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
            ).build())
            listeners.toList().forEach { it.onEvents(this, events) }
        }
    }
}
