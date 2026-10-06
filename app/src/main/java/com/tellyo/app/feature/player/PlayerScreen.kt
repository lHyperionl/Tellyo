package com.tellyo.app.feature.player

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tellyo.app.R
import com.tellyo.app.core.player.PlaybackState
import com.tellyo.app.core.ui.components.LiveBadge
import com.tellyo.app.core.ui.components.TellyoButton
import com.tellyo.app.core.ui.components.TellyoLogo
import com.tellyo.app.core.ui.components.TellyoProgressBar
import com.tellyo.app.core.ui.theme.TellyoColors
import kotlinx.coroutines.delay

private const val CONTROL_HIDE_DELAY_MILLIS = 4_500L

@Composable
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
fun PlayerScreen(
    channelName: String,
    programTitle: String? = null,
    player: Player?,
    playbackState: PlaybackState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    isInternetAvailable: Boolean = true,
) {
    BackHandler(onBack = onBack)
    NativePlaybackKeepScreenOn(player)
    val context = LocalContext.current
    val status = playbackStatus(playbackState, player)
    var playbackStarted by remember(channelName, player) { mutableStateOf(false) }
    var showRebufferNotice by remember(channelName, player) { mutableStateOf(false) }
    val cleanFullscreen = playbackState is PlaybackState.Playing || playbackState is PlaybackState.Paused ||
        (playbackStarted && playbackState == PlaybackState.Buffering)
    val interactivePlayback = cleanFullscreen && isInternetAvailable
    val actionFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val actionNeedsFocus = isInternetAvailable && (playbackState is PlaybackState.External ||
        playbackState is PlaybackState.Error ||
        playbackState is PlaybackState.Unavailable)
    var controlsVisible by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(C.TIME_UNSET) }

    LaunchedEffect(playbackState, isInternetAvailable) {
        if (!isInternetAvailable) return@LaunchedEffect
        when (playbackState) {
            is PlaybackState.Playing -> {
                if (!playbackStarted) {
                    controlsVisible = false
                    rootFocus.requestFocus()
                }
                playbackStarted = true
            }

            is PlaybackState.Paused -> {
                playbackStarted = true
                controlsVisible = true
            }

            else -> Unit
        }
    }

    LaunchedEffect(playbackState, playbackStarted) {
        showRebufferNotice = false
        if (playbackStarted && playbackState == PlaybackState.Buffering) {
            delay(1_500L)
            showRebufferNotice = true
        }
    }

    LaunchedEffect(actionNeedsFocus, playbackState) {
        if (actionNeedsFocus) actionFocus.requestFocus()
    }

    LaunchedEffect(player, interactivePlayback) {
        while (interactivePlayback) {
            player?.let {
                position = it.currentPosition.coerceAtLeast(0L)
                duration = it.duration
            }
            delay(500L)
        }
    }

    LaunchedEffect(controlsVisible, interactivePlayback) {
        if (controlsVisible && interactivePlayback) {
            // AnimatedVisibility attaches its controls after the visibility
            // state changes, including when a playing stream is paused.
            withFrameNanos { }
            playFocus.requestFocus()
        }
    }

    LaunchedEffect(controlsVisible, interactivePlayback, player?.isPlaying) {
        if (controlsVisible && interactivePlayback && player?.isPlaying == true) {
            delay(CONTROL_HIDE_DELAY_MILLIS)
            controlsVisible = false
            rootFocus.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                if (!interactivePlayback || controlsVisible || event.type != KeyEventType.KeyUp) {
                    false
                } else if (event.key == Key.Enter ||
                    event.key == Key.DirectionCenter ||
                    event.key == Key.NumPadEnter ||
                    event.key == Key.DirectionUp ||
                    event.key == Key.DirectionDown ||
                    event.key == Key.DirectionLeft ||
                    event.key == Key.DirectionRight
                ) {
                    controlsVisible = true
                    true
                } else {
                    false
                }
            }
            .focusable(),
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
                        // The visible transport is Tellyo-owned. Media3 still
                        // owns the video surface and player/session lifecycle.
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setEnableComposeSurfaceSyncWorkaround(true)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setKeepContentOnPlayerReset(true)
                    }
                },
                update = { view ->
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    if (view.player !== player) {
                        view.player = player
                        view.requestLayout()
                    }
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

        if (cleanFullscreen) {
            if (showRebufferNotice) Text(
                text = status.label,
                color = status.color,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            TellyoPlayerControls(
                visible = controlsVisible && isInternetAvailable,
                channelName = channelName,
                programTitle = programTitle,
                player = player,
                position = position,
                duration = duration,
                playFocus = playFocus,
                onOpenGuide = onOpenGuide,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (isInternetAvailable) {
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
                        focusRequester = actionFocus,
                        modifier = Modifier.width(260.dp),
                        onClick = onOpenExternal,
                    )

                    is PlaybackState.Error, is PlaybackState.Unavailable -> TellyoButton(
                        text = "Skúsiť znova",
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

@Composable
private fun TellyoPlayerControls(
    visible: Boolean,
    channelName: String,
    programTitle: String?,
    player: Player?,
    position: Long,
    duration: Long,
    playFocus: FocusRequester,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var subtitlesEnabled by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(218.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, TellyoColors.BackgroundPrimary.copy(alpha = 0.98f)),
                    ),
                )
                .padding(horizontal = 34.dp, vertical = 20.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LiveBadge()
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channelName,
                            color = TellyoColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = programTitle ?: "Živé vysielanie",
                            color = TellyoColors.TextSecondary,
                            fontSize = 15.sp,
                        )
                    }
                    Text(
                        text = formatPlaybackTime(position),
                        color = TellyoColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                }
                TellyoProgressBar(
                    progress = if (duration > 0L && duration != C.TIME_UNSET) {
                        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 5.dp,
                )
                notice?.let {
                    Text(text = it, color = TellyoColors.TextSecondary, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TellyoButton(
                        text = "−15 s",
                        modifier = Modifier.width(90.dp),
                        onClick = {
                            player?.let { it.seekTo((it.currentPosition - 15_000L).coerceAtLeast(0L)) }
                        },
                    )
                    TellyoButton(
                        text = if (player?.isPlaying == true) "Pauza" else "Prehrať",
                        modifier = Modifier.width(108.dp),
                        focusRequester = playFocus,
                        onClick = {
                            if (player?.isPlaying == true) player.pause() else player?.play()
                        },
                    )
                    TellyoButton(
                        text = "+30 s",
                        modifier = Modifier.width(90.dp),
                        onClick = { player?.let { it.seekTo(it.currentPosition + 30_000L) } },
                    )
                    TellyoButton(
                        text = "Zvuk",
                        modifier = Modifier.width(82.dp),
                        onClick = {
                            player?.let {
                                it.volume = 1f
                                it.trackSelectionParameters = it.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                    .build()
                            }
                            notice = "Zvuková stopa: automatická"
                        },
                    )
                    TellyoButton(
                        text = if (subtitlesEnabled) "Titulky vypnúť" else "Titulky",
                        modifier = Modifier.width(126.dp),
                        onClick = {
                            subtitlesEnabled = !subtitlesEnabled
                            player?.let {
                                it.trackSelectionParameters = it.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                                    .build()
                            }
                            notice = if (subtitlesEnabled) "Titulky zapnuté" else "Titulky vypnuté"
                        },
                    )
                    TellyoButton(
                        text = "TV program",
                        modifier = Modifier.width(130.dp),
                        onClick = onOpenGuide,
                    )
                }
            }
        }
    }
}

private fun formatPlaybackTime(millis: Long): String {
    val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
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
    is PlaybackState.OfficialEmbed -> PlaybackStatus("Oficiálny prehrávač", TellyoColors.Success)
    is PlaybackState.External -> PlaybackStatus("Vyžaduje externú aplikáciu", TellyoColors.Warning)
    is PlaybackState.Unavailable -> PlaybackStatus(state.reason, TellyoColors.Live)
    is PlaybackState.Error -> PlaybackStatus(state.message, TellyoColors.Live)
}
