package com.tellyo.app.feature.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.ui.components.ChannelLogo
import com.tellyo.app.feature.home.channelLogoKey
import com.tellyo.app.core.ui.theme.TellyoColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private const val CHANNEL_BROWSER_IDLE_TIMEOUT_MILLIS = 10_000L

/** Browsing is local UI state: only OK commits a channel to the playback service. */
@Composable
fun QuickChannelSwitcher(
    channels: List<Channel>,
    playingChannelId: String,
    programsByChannel: Map<String, List<Program>>,
    now: Instant,
    onSwitchChannel: (String) -> Unit,
    onBack: () -> Unit,
    registerKeyHandler: (((KeyEvent) -> Boolean)?) -> Unit = {},
    allowDirectionalSwitching: Boolean = true,
    content: @Composable (onPlayerBack: () -> Unit) -> Unit,
) {
    val available = remember(channels) { channels.filter { it.enabled } }
    var browsedId by remember { mutableStateOf<String?>(null) }
    var inputGeneration by remember { mutableLongStateOf(0L) }
    val visible = browsedId != null && available.isNotEmpty()
    val index = available.indexOfFirst { it.id == browsedId }.coerceAtLeast(0)
    val back = { if (visible) browsedId = null else onBack() }
    val handleKey: (KeyEvent) -> Boolean = { event ->
        val vertical = (visible || allowDirectionalSwitching) &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
        val channelKey = event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP ||
            event.keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN
        val browse = vertical || channelKey
        val confirm = event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER)
        val dismiss = event.keyCode in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_DEL)
        if (available.isNotEmpty() && (visible || browse) &&
            (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
            inputGeneration++
        }
        when {
            available.isEmpty() -> false
            browse -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val current = if (visible) index else
                        available.indexOfFirst { it.id == playingChannelId }.coerceAtLeast(0)
                    val direction = if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                        event.keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) -1 else 1
                    browsedId = available[(current + direction + available.size) % available.size].id
                }
                true
            }
            visible && confirm -> {
                // Commit on release so this OK press cannot also trigger player controls.
                if (event.action == KeyEvent.ACTION_UP) {
                    val id = available[index].id
                    browsedId = null
                    if (id != playingChannelId) onSwitchChannel(id)
                }
                true
            }
            visible && dismiss -> {
                if (event.action == KeyEvent.ACTION_UP) browsedId = null
                true
            }
            visible && event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT) -> true
            else -> false
        }
    }
    val latestHandler by rememberUpdatedState(handleKey)
    DisposableEffect(Unit) {
        // Activity interception also covers PlayerView and broadcaster WebView focus.
        registerKeyHandler { latestHandler(it) }
        onDispose { registerKeyHandler(null) }
    }
    LaunchedEffect(available, playingChannelId) {
        if (browsedId != null && available.none { it.id == browsedId }) browsedId = null
    }
    LaunchedEffect(visible, inputGeneration) {
        if (visible) {
            delay(CHANNEL_BROWSER_IDLE_TIMEOUT_MILLIS)
            browsedId = null
        }
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { handleKey(it.nativeKeyEvent) }) {
        content(back)
        BackHandler(enabled = visible) { browsedId = null }
        if (visible) {
            val listState = rememberLazyListState()
            LaunchedEffect(index) {
                // Instant scrolling keeps held-arrow browsing responsive and respects reduced motion.
                listState.scrollToItem((index - 1).coerceAtLeast(0))
            }
            Column(
                Modifier.align(Alignment.CenterEnd)
                    .testTag("quick-channel-switcher")
                    .width(486.dp).fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.20f to TellyoColors.BackgroundPrimary.copy(alpha = 0.97f),
                            0.38f to TellyoColors.BackgroundPrimary,
                            1f to TellyoColors.BackgroundPrimary,
                        ),
                    )
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                inputGeneration++
                            }
                        }
                    }
                    .padding(start = 116.dp, end = 20.dp, top = 28.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("${index + 1} / ${available.size}", color = TellyoColors.TextMuted,
                    fontSize = 13.sp, modifier = Modifier.align(Alignment.End))
                LazyColumn(state = listState, modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(available, key = { _, channel -> channel.id }) { rowIndex, channel ->
                        val highlighted = rowIndex == index
                        val program = programsByChannel[channel.id].orEmpty()
                            .firstOrNull { it.start <= now && it.end > now }
                        Row(
                            Modifier.fillMaxWidth()
                                .semantics { selected = highlighted }
                                .background(if (highlighted) TellyoColors.SurfaceElevated else Color.Transparent,
                                    RoundedCornerShape(12.dp))
                                .border(if (highlighted) 3.dp else 0.dp,
                                    if (highlighted) TellyoColors.FocusRing else Color.Transparent,
                                    RoundedCornerShape(12.dp))
                                .focusProperties { canFocus = false }
                                .clickable {
                                    browsedId = null
                                    if (channel.id != playingChannelId) onSwitchChannel(channel.id)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ChannelLogo(channelLogoKey(channel), logoUrl = channel.logoUrl,
                                    modifier = Modifier.width(64.dp).height(32.dp))
                                Text(channel.number.toString(), color = TellyoColors.TextMuted, fontSize = 11.sp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(channel.name, color = TellyoColors.TextPrimary, fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (channel.id == playingChannelId) {
                                    Text("Práve sledujete", color = TellyoColors.PrimaryBlueBright, fontSize = 12.sp)
                                }
                                Text(program?.title ?: "Program nie je k dispozícii",
                                    color = TellyoColors.TextSecondary, fontSize = 14.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (program != null) Text(
                                    "${program.start.channelTime()} – ${program.end.channelTime()}",
                                    color = TellyoColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowUp, null, tint = TellyoColors.TextSecondary)
                    Icon(Icons.Outlined.KeyboardArrowDown, null, tint = TellyoColors.TextSecondary)
                    Text("Výber   ·   OK Prepnúť   ·   Späť Zavrieť", color = TellyoColors.TextSecondary,
                        fontSize = 12.sp)
                }
            }
        }
    }
}

private val channelTimeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private fun Instant.channelTime(): String = channelTimeFormat.format(this)
