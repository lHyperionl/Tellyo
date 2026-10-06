package com.tellyo.app.feature.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.ui.components.TellyoButton
import com.tellyo.app.core.ui.components.TellyoFocusableSurface
import com.tellyo.app.core.ui.components.TellyoFocusStyles
import com.tellyo.app.core.ui.components.TellyoIcon as TellyoIconType
import com.tellyo.app.core.ui.components.ChannelLogo
import com.tellyo.app.core.ui.theme.TellyoColors
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

@Composable
fun GuideScreen(
    state: GuideUiState,
    entryFocus: FocusRequester? = null,
    autoFocusContent: Boolean = true,
    onAction: (GuideAction) -> Unit,
) {
    BackHandler(enabled = state.selectedProgram != null) {
        onAction(GuideAction.DismissProgram)
    }

    val firstProgramFocus = remember { FocusRequester() }
    val toolbarFocus = entryFocus ?: remember { FocusRequester() }
    val listState = rememberLazyListState()
    val windowEnd = state.windowStart.plus(Duration.ofHours(state.windowHours.toLong()))
    val firstProgramRow = state.rows.indexOfFirst { row ->
        row.programs.any { it.end > state.windowStart && it.start < windowEnd }
    }
    val firstProgramId = state.rows.getOrNull(firstProgramRow)?.programs?.firstOrNull {
        it.end > state.windowStart && it.start < windowEnd
    }?.id
    LaunchedEffect(firstProgramId, autoFocusContent) {
        if (autoFocusContent) {
            if (firstProgramId != null) listState.scrollToItem(firstProgramRow)
            withFrameNanos { }
            if (firstProgramId != null) firstProgramFocus.requestFocus() else toolbarFocus.requestFocus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "TV program",
                    modifier = Modifier.weight(1f),
                    color = TellyoColors.TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TellyoButton(
                        text = "←",
                        modifier = Modifier.width(52.dp),
                        onClick = { onAction(GuideAction.ShiftWindow(-2)) },
                    )
                    TellyoButton(
                        text = "→",
                        modifier = Modifier.width(52.dp),
                        onClick = { onAction(GuideAction.ShiftWindow(2)) },
                    )
                    TellyoButton(
                        text = "Obnoviť",
                        focusRequester = toolbarFocus,
                        icon = TellyoIconType.INFO,
                        modifier = Modifier.width(106.dp),
                        onClick = { onAction(GuideAction.Refresh) },
                    )
                }
            }

            Text(
                text = "Všetky kanály · ${formatDate(state.windowStart)}",
                color = TellyoColors.TextSecondary,
                fontSize = 14.sp,
            )

            if (state.error != null) {
                Text(text = state.error, color = TellyoColors.Warning, fontSize = 15.sp)
            }

            if (state.rows.isEmpty()) {
                Text(
                    text = if (state.isRefreshing) "Načítavam TV program…" else "TV program nie je dostupný.",
                    color = TellyoColors.TextSecondary,
                    fontSize = 18.sp,
                )
            } else {
                GuideGrid(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    state = state,
                    firstProgramFocus = firstProgramFocus,
                    firstProgramId = firstProgramId,
                    listState = listState,
                    onAction = onAction,
                )
            }
        }

        state.selectedProgram?.let { program ->
            ProgramDetail(
                program = program,
                onDismiss = { onAction(GuideAction.DismissProgram) },
                onOpenChannel = { onAction(GuideAction.OpenChannel(program.channelId)) },
            )
        }
    }
}

@Composable
private fun GuideGrid(
    modifier: Modifier,
    state: GuideUiState,
    firstProgramFocus: FocusRequester,
    firstProgramId: String?,
    listState: LazyListState,
    onAction: (GuideAction) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier,
    ) {
        val channelColumn = 170.dp
        val timelineWidth = (maxWidth - channelColumn).coerceAtLeast(1.dp)
        val windowMinutes = state.windowHours * 60f
        val markerMinutes = Duration.between(state.windowStart, state.now).toMinutes().toFloat()
        val markerFraction = markerMinutes / windowMinutes

        Column(modifier = Modifier.fillMaxSize()) {
            TimelineHeader(
                channelColumn = channelColumn,
                timelineWidth = timelineWidth,
                state = state,
            )
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(state.rows, key = { _, row -> row.channel.id }) { rowIndex, row ->
                        GuideRow(
                            row = row,
                            channelColumn = channelColumn,
                            timelineWidth = timelineWidth,
                            windowStart = state.windowStart,
                            windowMinutes = windowMinutes,
                            focusedProgramId = state.focusedProgramId,
                            firstProgramFocus = firstProgramFocus,
                            firstProgramId = firstProgramId,
                            now = state.now,
                            onAction = onAction,
                        )
                    }
                }
                if (markerFraction >= 0f && markerFraction < 1f) Box(
                    modifier = Modifier
                        .offset(x = channelColumn + timelineWidth * markerFraction)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(TellyoColors.PrimaryBlueBright)
                        .zIndex(4f),
                )
            }
        }
    }
}

@Composable
private fun TimelineHeader(
    channelColumn: Dp,
    timelineWidth: Dp,
    state: GuideUiState,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(29.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(channelColumn))
        Box(modifier = Modifier.width(timelineWidth).fillMaxHeight()) {
            (0..state.windowHours).forEach { hourOffset ->
                Text(
                    text = formatTime(state.windowStart.plus(Duration.ofHours(hourOffset.toLong()))),
                    modifier = if (hourOffset == state.windowHours) Modifier.align(Alignment.CenterEnd) else Modifier
                        .offset(x = timelineWidth * (hourOffset.toFloat() / state.windowHours))
                        .align(Alignment.CenterStart),
                    color = if (hourOffset == 0) TellyoColors.PrimaryBlueBright else TellyoColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (hourOffset == 0) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    row: GuideRowState,
    channelColumn: Dp,
    timelineWidth: Dp,
    windowStart: Instant,
    windowMinutes: Float,
    focusedProgramId: String?,
    firstProgramFocus: FocusRequester,
    firstProgramId: String?,
    now: Instant,
    onAction: (GuideAction) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
    ) {
        Row(
            modifier = Modifier
                .width(channelColumn)
                .fillMaxHeight()
                .background(TellyoColors.SurfacePrimary, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            ChannelLogo(
                logoKey = when (row.channel.id) {
                    "stvr-jednotka" -> "stvr"
                    "stvr-dvojka" -> "dvojka"
                    "stvr-24" -> "24"
                    "stvr-sport" -> "sport"
                    "joj" -> "joj"
                    "joj-plus" -> "joj-plus"
                    "joj-krimi" -> "joj-krimi"
                    "joj-24" -> "joj-24"
                    "markiza" -> "markiza"
                    "markiza-doma" -> "markiza-doma"
                    "markiza-dajto" -> "markiza-dajto"
                    "markiza-krimi" -> "markiza-krimi"
                    "markiza-klasik" -> "markiza-klasik"
                    "ta3" -> "ta3"
                    else -> row.channel.shortName
                },
                logoUrl = row.channel.logoUrl,
                textSize = 28.sp,
            )
            Text(
                text = row.channel.name,
                color = TellyoColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Box(
            modifier = Modifier
                .offset(x = channelColumn)
                .width(timelineWidth)
                .fillMaxHeight(),
        ) {
            row.programs.forEachIndexed { index, program ->
                val startMinutes = Duration.between(windowStart, program.start).toMillis() / 60_000f
                val endMinutes = Duration.between(windowStart, program.end).toMillis() / 60_000f
                val visibleStart = max(0f, startMinutes)
                val visibleEnd = min(windowMinutes, endMinutes)
                if (visibleEnd > visibleStart) {
                    val x = timelineWidth * (visibleStart / windowMinutes)
                    val width = timelineWidth * ((visibleEnd - visibleStart) / windowMinutes)
                    val isCurrent = program.start <= now && program.end > now
                    TellyoFocusableSurface(
                        modifier = Modifier
                            .offset(x = x)
                            .width(width)
                            .height(56.dp)
                            .padding(horizontal = 1.dp),
                        shape = RoundedCornerShape(7.dp),
                        selected = isCurrent || focusedProgramId == program.id,
                        focusRequester = if (program.id == firstProgramId) firstProgramFocus else null,
                        contentDescription = "${row.channel.name}: ${program.title}",
                        focusStyle = TellyoFocusStyles.FilledSelection,
                        onClick = { onAction(GuideAction.SelectProgram(program.id)) },
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (width >= 58.dp) Text(
                                text = if (width >= 110.dp) {
                                    "${formatTime(program.start)} - ${formatTime(program.end)}"
                                } else formatTime(program.start),
                                color = TellyoColors.TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = program.title,
                                color = TellyoColors.TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramDetail(
    program: Program,
    onDismiss: () -> Unit,
    onOpenChannel: () -> Unit,
) {
    val openChannelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(program.id) {
            withFrameNanos { }
            openChannelFocus.requestFocus()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            TellyoFocusableSurface(
                modifier = Modifier
                    .padding(bottom = 18.dp)
                    .width(500.dp),
                shape = RoundedCornerShape(14.dp),
                selected = true,
                contentDescription = "Detail programu ${program.title}",
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(text = program.title, color = TellyoColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${formatTime(program.start)} - ${formatTime(program.end)}",
                        color = TellyoColors.PrimaryBlueBright,
                        fontSize = 14.sp,
                    )
                    program.description?.let { description ->
                        Text(text = description, color = TellyoColors.TextSecondary, fontSize = 15.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TellyoButton(
                            text = "Otvoriť kanál",
                            icon = TellyoIconType.PLAY,
                            modifier = Modifier.width(138.dp),
                            focusRequester = openChannelFocus,
                            onClick = onOpenChannel,
                        )
                        TellyoButton(text = "Zavrieť", modifier = Modifier.width(110.dp), onClick = onDismiss)
                    }
                }
            }
        }
    }
}

private fun formatTime(instant: Instant): String =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(instant)

private fun formatDate(instant: Instant): String =
    DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault()).format(instant)
