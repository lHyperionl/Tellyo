package com.tellyo.app.feature.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tellyo.app.core.network.NetworkStatus
import com.tellyo.app.core.player.PlaybackState
import com.tellyo.app.core.ui.components.SectionHeading
import com.tellyo.app.core.ui.components.TellyoButton
import com.tellyo.app.core.ui.components.TellyoFocusableSurface
import com.tellyo.app.core.ui.components.TellyoIcon as TellyoIconType
import com.tellyo.app.core.ui.theme.TellyoColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    entryFocus: FocusRequester? = null,
    autoFocusContent: Boolean = true,
    onAction: (DiagnosticsAction) -> Unit,
) {
    val checkFocus = entryFocus ?: remember { FocusRequester() }
    val epgFocus = remember { FocusRequester() }

    LaunchedEffect(autoFocusContent) {
        if (autoFocusContent) checkFocus.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Diagnostika",
            color = TellyoColors.TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Stav siete, EPG, prehrávača a oficiálnych zdrojov kanálov",
            color = TellyoColors.TextSecondary,
            fontSize = 16.sp,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusCard(
                title = "Sieť",
                value = if (state.networkStatus == NetworkStatus.Available) "OK" else "Nedostupná",
                valueColor = if (state.networkStatus == NetworkStatus.Available) {
                    TellyoColors.Success
                } else {
                    TellyoColors.Live
                },
                modifier = Modifier.weight(1f),
            )
            StatusCard(
                title = "Prehrávač",
                value = playbackLabel(state.playbackState),
                valueColor = TellyoColors.PrimaryBlueBright,
                modifier = Modifier.weight(1f),
            )
            StatusCard(
                title = "EPG",
                value = epgLabel(state),
                valueColor = when (state.epgStatus) {
                    EpgRefreshStatus.Failed -> TellyoColors.Live
                    EpgRefreshStatus.Updated -> TellyoColors.Success
                    else -> TellyoColors.PrimaryBlueBright
                },
                modifier = Modifier.weight(1f),
            )
        }

        SectionHeading(text = "Aktuálny stream")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TelemetryCard("Protokol", state.playbackTelemetry.protocol ?: "-", Modifier.weight(1f))
            TelemetryCard(
                "Rozlíšenie",
                state.playbackTelemetry.resolutionLabel(),
                Modifier.weight(1f),
            )
            TelemetryCard(
                "Bitrate",
                state.playbackTelemetry.effectiveBitrate.bitrateLabel(),
                Modifier.weight(1f),
            )
            TelemetryCard(
                "Vynechané snímky",
                state.playbackTelemetry.droppedFrames.toString(),
                Modifier.weight(1f),
            )
            TelemetryCard(
                "Buffer",
                state.playbackTelemetry.bufferDurationMillis.secondsLabel(),
                Modifier.weight(1f),
            )
            TelemetryCard(
                "Live offset",
                state.playbackTelemetry.liveOffsetMillis.secondsLabel(),
                Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TellyoButton(
                text = if (state.isCheckingChannels) "Testujem kanály…" else "Otestovať kanály",
                enabled = !state.isCheckingChannels,
                icon = TellyoIconType.SIGNAL,
                focusRequester = checkFocus,
                modifier = Modifier
                    .width(190.dp)
                    .focusProperties { right = epgFocus },
                onClick = { onAction(DiagnosticsAction.CheckChannels) },
            )
            TellyoButton(
                text = "Obnoviť EPG",
                enabled = state.epgStatus != EpgRefreshStatus.Refreshing,
                icon = TellyoIconType.GUIDE,
                focusRequester = epgFocus,
                modifier = Modifier
                    .width(150.dp)
                    .focusProperties { left = checkFocus },
                onClick = { onAction(DiagnosticsAction.RefreshEpg) },
            )
        }

        SectionHeading(text = "Stav kanálov")
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.channelHealth, key = { it.channel.id }) { health ->
                TellyoFocusableSurface(
                    modifier = Modifier.fillMaxWidth(),
                    contentDescription = "${health.channel.name}: ${health.status}",
                ) { ChannelHealthRow(health) }
            }
            if (state.channelHealth.isEmpty()) {
                item {
                    Text(
                        text = "Kanály sa ešte načítavajú…",
                        color = TellyoColors.TextSecondary,
                        fontSize = 16.sp,
                    )
                }
            }
        }

        state.epgDetail?.let { detail ->
            Text(text = detail, color = TellyoColors.Live, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TelemetryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(TellyoColors.SurfacePrimary, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = title, color = TellyoColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
        Text(
            text = value,
            color = TellyoColors.PrimaryBlueBright,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(TellyoColors.SurfacePrimary, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(text = title, color = TellyoColors.TextSecondary, fontSize = 14.sp)
        Text(text = value, color = valueColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChannelHealthRow(health: ChannelHealth) {
    val (statusText, statusColor) = when (health.status) {
        ChannelCheckStatus.Idle -> "Neoverené" to TellyoColors.TextMuted
        ChannelCheckStatus.Checking -> "Testujem…" to TellyoColors.PrimaryBlueBright
        ChannelCheckStatus.Ok -> "OK" to TellyoColors.Success
        ChannelCheckStatus.External -> "Oficiálny web" to TellyoColors.Warning
        ChannelCheckStatus.Failed -> "Chyba" to TellyoColors.Live
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(TellyoColors.SurfacePrimary, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${health.channel.number}  ${health.channel.name}",
            color = TellyoColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        health.durationMillis?.let {
            Text(text = "${it} ms", color = TellyoColors.TextSecondary, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(14.dp))
        }
        Text(text = statusText, color = statusColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

private fun playbackLabel(state: PlaybackState): String = when (state) {
    PlaybackState.Idle -> "Idle"
    PlaybackState.Resolving -> "Resolving"
    PlaybackState.Buffering -> "Buffering"
    is PlaybackState.Playing -> "Prehráva sa"
    is PlaybackState.Paused -> "Pozastavené"
    PlaybackState.Retrying -> "Opakujem"
    is PlaybackState.OfficialEmbed -> "Oficiálny prehrávač"
    is PlaybackState.External -> "Externý zdroj"
    is PlaybackState.Unavailable -> "Nedostupné"
    is PlaybackState.Error -> "Chyba"
}

private fun epgLabel(state: DiagnosticsUiState): String = when (state.epgStatus) {
    EpgRefreshStatus.Idle -> "Neoverené"
    EpgRefreshStatus.Refreshing -> "Obnovujem"
    EpgRefreshStatus.Updated -> state.lastEpgRefresh?.let {
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(it)
    } ?: "Aktuálne"
    EpgRefreshStatus.Failed -> "Chyba"
}

private fun com.tellyo.app.core.player.PlaybackTelemetry.resolutionLabel(): String =
    if (videoWidth != null && videoHeight != null) "${videoWidth}×${videoHeight}" else "-"

private fun Long?.bitrateLabel(): String = this?.let { bitrate ->
    String.format(java.util.Locale.ROOT, "%.1f Mbps", bitrate / 1_000_000.0)
} ?: "-"

private fun Long?.secondsLabel(): String = this?.let { millis ->
    String.format(java.util.Locale.ROOT, "%.1f s", millis / 1_000.0)
} ?: "-"
