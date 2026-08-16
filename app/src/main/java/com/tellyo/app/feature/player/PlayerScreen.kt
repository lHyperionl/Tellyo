package com.tellyo.app.feature.player

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tellyo.app.R
import com.tellyo.app.core.player.PlaybackState
import com.tellyo.app.core.ui.components.LiveBadge
import com.tellyo.app.core.ui.components.TellyoButton
import com.tellyo.app.core.ui.components.TellyoIcon
import com.tellyo.app.core.ui.components.TellyoLogo
import com.tellyo.app.core.ui.theme.TellyoColors

@Composable
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
fun PlayerScreen(
    channelName: String,
    player: Player?,
    playbackState: PlaybackState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val status = playbackStatus(playbackState, player)
    val cleanFullscreen = playbackState is PlaybackState.Playing
    val actionFocus = remember { FocusRequester() }
    val actionNeedsFocus = playbackState is PlaybackState.External ||
        playbackState is PlaybackState.Error ||
        playbackState is PlaybackState.Unavailable

    LaunchedEffect(playbackState) {
        if (actionNeedsFocus) {
            actionFocus.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Keep the Android media surface in a deterministic 16:9 measurement
        // box. On API 34, an unconstrained PlayerView hosted by AndroidView can
        // retain stale surface dimensions and render only the top-left portion
        // of the stream even when resizeMode is FIT.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                factory = {
                    (LayoutInflater.from(context).inflate(R.layout.tellyo_player_view, null) as PlayerView).apply {
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        // Android 14/API 34 can retain an incorrect surface crop when
                        // PlayerView is hosted by Compose AndroidView. Media3 exposes
                        // this workaround specifically for that interop case.
                        setEnableComposeSurfaceSyncWorkaround(true)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setControllerHideOnTouch(true)
                        setControllerShowTimeoutMs(2_500)
                        // Keep Media3's subtitle selector available in the
                        // remote-friendly player controls. Audio-track
                        // selection is exposed by the same settings menu when
                        // a source advertises multiple audio renditions.
                        setShowSubtitleButton(true)
                    }
                },
                update = { view ->
                    // Keep the source aspect ratio after the stream reports its video size.
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    if (view.player !== player) {
                        view.player = player
                        view.requestLayout()
                    }
                    if (cleanFullscreen) view.hideController()
                },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .alpha(if (cleanFullscreen) 0f else 1f)
                .padding(start = 28.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            TellyoLogo(iconSize = 26.dp, textSize = 26.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveBadge()
                Text(text = channelName, color = TellyoColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (!cleanFullscreen) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 28.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = status.label, color = status.color, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                when (playbackState) {
                    is PlaybackState.External -> TellyoButton(
                        text = "Otvoriť oficiálne vysielanie",
                        icon = TellyoIcon.PLAY,
                        focusRequester = actionFocus,
                        modifier = Modifier.width(260.dp),
                        onClick = onOpenExternal,
                    )

                    is PlaybackState.Error, is PlaybackState.Unavailable -> TellyoButton(
                        text = "Skúsiť znova",
                        icon = TellyoIcon.PLAY,
                        focusRequester = actionFocus,
                        modifier = Modifier.width(142.dp),
                        onClick = onRetry,
                    )

                    else -> Unit
                }
            }
        }
    }
}

private data class PlaybackStatus(val label: String, val color: Color)

private fun playbackStatus(state: PlaybackState, player: Player?): PlaybackStatus = when (state) {
    PlaybackState.Idle -> if (player == null) {
        PlaybackStatus("Pripájam prehrávač…", TellyoColors.TextSecondary)
    } else {
        PlaybackStatus("Prehrávanie zastavené", TellyoColors.TextSecondary)
    }
    PlaybackState.Resolving -> PlaybackStatus("Vyhľadávam stream…", TellyoColors.PrimaryBlueBright)
    PlaybackState.Buffering -> PlaybackStatus("Načítavam stream…", TellyoColors.Warning)
    is PlaybackState.Playing -> PlaybackStatus("", TellyoColors.Success)
    is PlaybackState.Paused -> PlaybackStatus("Pozastavené", TellyoColors.TextSecondary)
    PlaybackState.Retrying -> PlaybackStatus("Opakujem pripojenie…", TellyoColors.Warning)
    is PlaybackState.External -> PlaybackStatus("Vyžaduje externú aplikáciu", TellyoColors.Warning)
    is PlaybackState.Unavailable -> PlaybackStatus(state.reason, TellyoColors.Live)
    is PlaybackState.Error -> PlaybackStatus(state.message, TellyoColors.Live)
}
