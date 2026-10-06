package com.tellyo.app.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player

/** Prevent TV ambient mode while the foreground native player is playing or buffering. */
@Composable
internal fun NativePlaybackKeepScreenOn(player: Player?) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(player, view, lifecycleOwner) {
        val previousKeepScreenOn = view.keepScreenOn
        fun update() {
            val activePlayback = player != null && player.playWhenReady &&
                player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
            view.keepScreenOn = previousKeepScreenOn ||
                (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && activePlayback)
        }

        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = update()
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        player?.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        update()

        onDispose {
            player?.removeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.keepScreenOn = previousKeepScreenOn
        }
    }
}
