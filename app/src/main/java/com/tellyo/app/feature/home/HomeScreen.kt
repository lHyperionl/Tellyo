package com.tellyo.app.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.ui.components.ChannelLogo
import com.tellyo.app.core.ui.components.LiveBadge
import com.tellyo.app.core.ui.components.SectionHeading
import com.tellyo.app.core.ui.components.TellyoButton
import com.tellyo.app.core.ui.components.TellyoFocusableSurface
import com.tellyo.app.core.ui.components.TellyoFocusStyles
import com.tellyo.app.core.ui.components.TellyoIcon
import com.tellyo.app.core.ui.components.TellyoIcon as TellyoIconType
import com.tellyo.app.core.ui.components.TellyoLogo
import com.tellyo.app.core.ui.components.TellyoProgressBar
import com.tellyo.app.core.ui.theme.TellyoColors
import com.tellyo.app.feature.guide.GuideAction
import com.tellyo.app.feature.guide.GuideScreen
import com.tellyo.app.feature.guide.GuideUiState
import com.tellyo.app.feature.diagnostics.DiagnosticsAction
import com.tellyo.app.feature.diagnostics.DiagnosticsScreen
import com.tellyo.app.feature.diagnostics.DiagnosticsUiState
import com.tellyo.app.feature.settings.SettingsAction
import com.tellyo.app.feature.settings.SettingsScreen
import com.tellyo.app.feature.settings.SettingsUiState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.min

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    guideUiState: GuideUiState = GuideUiState(),
    onGuideAction: (GuideAction) -> Unit = {},
    diagnosticsUiState: DiagnosticsUiState = DiagnosticsUiState(),
    onDiagnosticsAction: (DiagnosticsAction) -> Unit = {},
    settingsUiState: SettingsUiState = SettingsUiState(),
    onSettingsAction: (SettingsAction) -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val systemDensity = LocalDensity.current
        val widthPx = with(systemDensity) { maxWidth.toPx() }
        val heightPx = with(systemDensity) { maxHeight.toPx() }
        val dashboardDensity = min(
            widthPx / DASHBOARD_REFERENCE_WIDTH,
            heightPx / DASHBOARD_REFERENCE_HEIGHT,
        ).coerceAtLeast(0.1f)

        CompositionLocalProvider(
            LocalDensity provides Density(
                density = dashboardDensity,
                fontScale = systemDensity.fontScale,
            ),
        ) {
            HomeDashboardFrame(
                uiState = uiState,
                onAction = onAction,
                guideUiState = guideUiState,
                onGuideAction = onGuideAction,
                diagnosticsUiState = diagnosticsUiState,
                onDiagnosticsAction = onDiagnosticsAction,
                settingsUiState = settingsUiState,
                onSettingsAction = onSettingsAction,
            )
        }
    }
}

@Composable
private fun HomeDashboardFrame(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    guideUiState: GuideUiState,
    onGuideAction: (GuideAction) -> Unit,
    diagnosticsUiState: DiagnosticsUiState,
    onDiagnosticsAction: (DiagnosticsAction) -> Unit,
    settingsUiState: SettingsUiState,
    onSettingsAction: (SettingsAction) -> Unit,
) {
    val firstNavigationFocus = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }
    val destinationFocus = remember { FocusRequester() }

    BackHandler(
        enabled = uiState.selectedDestination != NavigationDestination.HOME,
    ) {
        onAction(HomeAction.SelectDestination(NavigationDestination.HOME))
    }

    LaunchedEffect(uiState.selectedDestination, uiState.channels.size) {
        if (uiState.selectedDestination == NavigationDestination.HOME) {
            firstNavigationFocus.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF020714),
                        TellyoColors.BackgroundPrimary,
                        Color(0xFF04152C),
                    ),
                ),
            )
            .padding(start = 36.dp, top = 32.dp, end = 76.dp, bottom = 26.dp),
    ) {
        NavigationSidebar(
            selectedDestination = uiState.selectedDestination,
            firstNavigationFocus = firstNavigationFocus,
            contentFocus = if (uiState.selectedDestination == NavigationDestination.HOME) {
                heroFocus
            } else {
                destinationFocus
            },
            onDestinationSelected = {
                onAction(HomeAction.SelectDestination(it, requestContentFocus = false))
            },
        )

        Spacer(modifier = Modifier.width(28.dp))

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (uiState.selectedDestination == NavigationDestination.HOME) {
                DashboardContent(
                    uiState = uiState,
                    heroFocus = heroFocus,
                    navigationFocus = firstNavigationFocus,
                    onAction = onAction,
                )
            } else if (uiState.selectedDestination == NavigationDestination.GUIDE) {
                GuideScreen(
                    state = guideUiState,
                    entryFocus = destinationFocus,
                    autoFocusContent = uiState.focusContentOnDestination,
                    onAction = onGuideAction,
                )
            } else if (uiState.selectedDestination == NavigationDestination.DIAGNOSTICS) {
                DiagnosticsScreen(
                    state = diagnosticsUiState,
                    entryFocus = destinationFocus,
                    autoFocusContent = uiState.focusContentOnDestination,
                    onAction = onDiagnosticsAction,
                )
            } else if (uiState.selectedDestination == NavigationDestination.SETTINGS) {
                SettingsScreen(
                    state = settingsUiState,
                    navigationFocus = firstNavigationFocus,
                    entryFocus = destinationFocus,
                    autoFocusContent = uiState.focusContentOnDestination,
                    onAction = onSettingsAction,
                )
            } else if (uiState.selectedDestination == NavigationDestination.FAVORITES) {
                FavoritesScreen(
                    channels = uiState.channels,
                    favoriteSettings = uiState.favoriteSettings,
                    navigationFocus = firstNavigationFocus,
                    entryFocus = destinationFocus,
                    autoFocusContent = uiState.focusContentOnDestination,
                    onToggle = { channelId -> onAction(HomeAction.ToggleFavorite(channelId)) },
                    onMove = { channelId, direction -> onAction(HomeAction.MoveFavorite(channelId, direction)) },
                )
            } else {
                DestinationPlaceholder(
                    destination = uiState.selectedDestination,
                    entryFocus = destinationFocus,
                    autoFocusContent = uiState.focusContentOnDestination,
                    onReturnHome = { onAction(HomeAction.SelectDestination(NavigationDestination.HOME)) },
                )
            }
        }
    }
}

private const val DASHBOARD_REFERENCE_WIDTH = 1672f
private const val DASHBOARD_REFERENCE_HEIGHT = 941f

private data class ChannelPresentation(
    val channel: Channel,
    val programs: List<Program>,
    val currentProgram: Program?,
    val displayProgram: Program?,
)

private fun HomeUiState.presentation(channel: Channel, now: Instant = Instant.now()): ChannelPresentation {
    val programs = programsByChannel[channel.id].orEmpty().sortedBy { it.start }
    val current = programs.firstOrNull { it.start <= now && it.end.isAfter(now) }
    // Do not present an expired cached record as if it were the current show.
    // The official STVR endpoint can briefly omit metadata during a handover;
    // in that case showing no programme is more accurate than stale data.
    val display = current ?: programs.firstOrNull { it.start.isAfter(now) }
    return ChannelPresentation(
        channel = channel,
        programs = programs,
        currentProgram = current,
        displayProgram = display,
    )
}

internal fun channelLogoKey(channel: Channel): String = when (channel.id) {
    "stvr-jednotka" -> "stvr"
    "stvr-dvojka" -> "dvojka"
    "stvr-24" -> "24"
    "joj" -> "joj"
    "joj-plus" -> "joj-plus"
    "joj-krimi" -> "joj-krimi"
    "joj-24" -> "joj-24"
    "joj-sport", "joj-sport-2" -> "joj-sport"
    "jojko" -> "jojko"
    "joj-svet" -> "joj-svet"
    "markiza" -> "markiza"
    "markiza-doma" -> "markiza-doma"
    "markiza-dajto" -> "markiza-dajto"
    "markiza-krimi" -> "markiza-krimi"
    "markiza-klasik" -> "markiza-klasik"
    else -> "stvr"
}

private fun formatTime(instant: Instant): String =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(instant)

private fun formatDateTime(instant: Instant): String =
    DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale("sk", "SK"))
        .withZone(ZoneId.systemDefault())
        .format(instant)

private fun formatProgramRange(program: Program?): String =
    program?.let { "${formatTime(it.start)} – ${formatTime(it.end)}" } ?: "Čas nie je dostupný"

private fun programProgress(program: Program?, now: Instant = Instant.now()): Float {
    if (program == null || !program.end.isAfter(program.start)) return 0f
    return Duration.between(program.start, now).toMillis()
        .toFloat()
        .div(Duration.between(program.start, program.end).toMillis().toFloat())
        .coerceIn(0f, 1f)
}

@Composable
private fun NavigationSidebar(
    selectedDestination: NavigationDestination,
    firstNavigationFocus: FocusRequester,
    contentFocus: FocusRequester,
    onDestinationSelected: (NavigationDestination) -> Unit,
) {
    val navigationItems = listOf(
        NavigationDestination.HOME to TellyoIconType.HOME,
        NavigationDestination.GUIDE to TellyoIconType.GUIDE,
        NavigationDestination.FAVORITES to TellyoIconType.FAVORITES,
        NavigationDestination.SETTINGS to TellyoIconType.SETTINGS,
        NavigationDestination.DIAGNOSTICS to TellyoIconType.INFO,
    )
    val itemFocusRequesters = remember {
        List(navigationItems.size) { FocusRequester() }
    }

    Column(
        modifier = Modifier
            .width(250.dp)
            .fillMaxHeight(),
    ) {
        TellyoLogo(
            modifier = Modifier.padding(start = 14.dp),
            iconSize = 37.dp,
            textSize = 42.sp,
        )
        Spacer(modifier = Modifier.height(53.dp))

        navigationItems.forEachIndexed { index, (destination, icon) ->
            TellyoFocusableSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .focusProperties {
                        right = contentFocus
                        if (index > 0) {
                            up = if (index == 1) {
                                firstNavigationFocus
                            } else {
                                itemFocusRequesters[index - 1]
                            }
                        }
                        if (index < navigationItems.lastIndex) down = itemFocusRequesters[index + 1]
                    },
                shape = RoundedCornerShape(11.dp),
                selected = selectedDestination == destination,
                focusRequester = if (index == 0) firstNavigationFocus else itemFocusRequesters[index],
                contentDescription = destination.label,
                focusStyle = TellyoFocusStyles.Navigation,
                onClick = { onDestinationSelected(destination) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    TellyoIcon(
                        icon = icon,
                        modifier = Modifier.size(30.dp),
                        tint = if (selectedDestination == destination) {
                            TellyoColors.TextPrimary
                        } else {
                            TellyoColors.TextSecondary
                        },
                    )
                    Text(
                        text = destination.label,
                        color = if (selectedDestination == destination) {
                            TellyoColors.TextPrimary
                        } else {
                            TellyoColors.TextSecondary
                        },
                        fontSize = 20.sp,
                        fontWeight = if (selectedDestination == destination) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
            Spacer(modifier = Modifier.height(15.dp))
        }

        Spacer(modifier = Modifier.weight(1f))
        val now = Instant.now()
        Column(modifier = Modifier.padding(start = 10.dp, bottom = 72.dp)) {
            Text(
                text = DateTimeFormatter.ofPattern("EEEE", Locale("sk", "SK"))
                    .withZone(ZoneId.systemDefault())
                    .format(now)
                    .replaceFirstChar { it.titlecase(Locale("sk", "SK")) },
                color = TellyoColors.TextSecondary,
                fontSize = 17.sp,
            )
            Text(text = formatDateTime(now), color = TellyoColors.TextSecondary, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = formatTime(now),
                color = TellyoColors.PrimaryBlue,
                fontSize = 43.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: HomeUiState,
    heroFocus: FocusRequester,
    navigationFocus: FocusRequester,
    onAction: (HomeAction) -> Unit,
) {
    val playerButtonFocus = remember { FocusRequester() }
    val favoriteFocus = remember { FocusRequester() }
    val epgFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 18.dp)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeroPanel(
            uiState = uiState,
            heroFocus = heroFocus,
            navigationFocus = navigationFocus,
            playerButtonFocus = playerButtonFocus,
            favoriteFocus = favoriteFocus,
            onAction = onAction,
        )

        FavoriteChannels(
            uiState = uiState,
            firstFocus = favoriteFocus,
            playerButtonFocus = playerButtonFocus,
            epgFocus = epgFocus,
            navigationFocus = navigationFocus,
            onAction = onAction,
        )

        SectionHeading(text = "TV program")
        EpgPreview(
            uiState = uiState,
            firstProgramFocus = epgFocus,
            favoriteFocus = favoriteFocus,
            navigationFocus = navigationFocus,
            onAction = onAction,
        )
    }
}

@Composable
private fun HeroPanel(
    uiState: HomeUiState,
    heroFocus: FocusRequester,
    navigationFocus: FocusRequester,
    playerButtonFocus: FocusRequester,
    favoriteFocus: FocusRequester,
    onAction: (HomeAction) -> Unit,
) {
    val selected = uiState.channels
        .firstOrNull { it.id == uiState.selectedChannelId }
        ?.let { uiState.presentation(it) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(335.dp),
    ) {
        TellyoFocusableSurface(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties {
                    left = navigationFocus
                    down = playerButtonFocus
                },
            shape = RoundedCornerShape(20.dp),
            selected = true,
            focusRequester = heroFocus,
            contentDescription = "Náhľad ${selected?.channel?.name ?: "Tellyo"}",
            onClick = { onAction(HomeAction.OpenPlayer) },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF020918).copy(alpha = 0.99f),
                                    TellyoColors.BackgroundSecondary.copy(alpha = 0.96f),
                                    TellyoColors.SurfacePrimary.copy(alpha = 0.90f),
                                ),
                                startX = 0f,
                                endX = 780f,
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(650.dp),
                ) {
                    ProgramPreviewArtwork(
                        program = selected?.currentProgram,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(560.dp),
                    )
                    // Extend the artwork transition into the dark information
                    // area so the image never ends at a hard vertical seam.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(250.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0f to TellyoColors.SurfacePrimary.copy(alpha = 0.98f),
                                        0.42f to TellyoColors.SurfacePrimary.copy(alpha = 0.86f),
                                        0.78f to TellyoColors.SurfacePrimary.copy(alpha = 0.34f),
                                        1f to Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(455.dp)
                        .padding(start = 36.dp, top = 24.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (selected == null) {
                        Text(
                            text = if (uiState.isLoading) "Načítavam kanály…" else {
                                uiState.dataError ?: "Kanály nie sú dostupné."
                            },
                            color = TellyoColors.TextSecondary,
                            fontSize = 20.sp,
                        )
                    } else {
                        if (selected.currentProgram != null) LiveBadge()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogo(channelLogoKey(selected.channel), large = true, textSize = 58.sp)
                            Spacer(modifier = Modifier.width(24.dp))
                            Column {
                                Text(
                                    text = selected.channel.name,
                                    color = TellyoColors.TextPrimary,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = selected.displayProgram?.title ?: "Programové údaje nie sú dostupné",
                                    color = TellyoColors.TextSecondary,
                                    fontSize = 20.sp,
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatProgramRange(selected.displayProgram),
                                color = TellyoColors.TextSecondary,
                                fontSize = 16.sp,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            TellyoProgressBar(
                                progress = programProgress(selected.currentProgram),
                                modifier = Modifier.width(195.dp),
                            )
                        }
                        Text(
                            text = selected.displayProgram?.description
                                ?: "Programový sprievod pre tento kanál nie je dostupný.",
                            color = TellyoColors.TextSecondary,
                            fontSize = 16.sp,
                            lineHeight = 23.sp,
                        )
                        TellyoButton(
                            text = "Viac informácií",
                            icon = TellyoIconType.INFO,
                            focusRequester = playerButtonFocus,
                            modifier = Modifier
                                .width(178.dp)
                                .focusProperties {
                                    up = heroFocus
                                    down = favoriteFocus
                                    left = navigationFocus
                                },
                            onClick = { onAction(HomeAction.OpenPlayer) },
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun ProgramPreviewArtwork(
    program: Program?,
    modifier: Modifier = Modifier,
) {
    val categoryColor = when (program?.category?.lowercase()) {
        "news", "správy" -> Color(0xFF168BFF)
        "sport", "šport" -> Color(0xFFFF8A24)
        "culture", "kultúra" -> Color(0xFFE02B7B)
        else -> TellyoColors.PrimaryBlueBright
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        categoryColor.copy(alpha = 0.18f),
                        TellyoColors.BackgroundSecondary,
                        Color(0xFF020714),
                    ),
                ),
            ),
    ) {
        if (program?.artworkUrl != null) {
            AsyncImage(
                model = program.artworkUrl,
                contentDescription = "Obrazový náhľad programu ${program.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp)),
                alpha = 0.88f,
            )
        } else if (program != null) {
            Text(
                text = program.title,
                color = categoryColor.copy(alpha = 0.25f),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(420.dp)
                    .padding(end = 28.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.58f to Color.Transparent,
                            1f to Color(0xFF020918).copy(alpha = 0.18f),
                        ),
                    ),
                ),
        )
        if (program != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 28.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "PROGRAM PRÁVE PREBIEHA",
                    color = categoryColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = program.title,
                    color = TellyoColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun FavoriteChannels(
    uiState: HomeUiState,
    firstFocus: FocusRequester,
    playerButtonFocus: FocusRequester,
    epgFocus: FocusRequester,
    navigationFocus: FocusRequester,
    onAction: (HomeAction) -> Unit,
) {
    // Keep every registered channel reachable through the horizontally
    // navigable row; LazyRow provides the off-screen scrolling on TV.
    val channels = uiState.homeChannels()
    val cardFocusRequesters = remember(channels.map { it.id }) {
        List(channels.size) { FocusRequester() }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(145.dp)
        .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(channels, key = { _, item -> item.id }) { index, item ->
            val presentation = uiState.presentation(item)
            TellyoFocusableSurface(
                modifier = Modifier
                    .width(205.dp)
                    .fillMaxHeight()
                    .focusProperties {
                        up = playerButtonFocus
                        down = epgFocus
                        left = when (index) {
                            0 -> navigationFocus
                            1 -> firstFocus
                            else -> cardFocusRequesters[index - 1]
                        }
                        if (index < channels.lastIndex) {
                            right = cardFocusRequesters[index + 1]
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                selected = item.id == uiState.focusedFavoriteId,
                focusRequester = if (index == 0) firstFocus else cardFocusRequesters[index],
                contentDescription = "${item.name}, ${presentation.displayProgram?.title ?: "Programové údaje nie sú dostupné"}",
                onClick = { onAction(HomeAction.PlayChannel(item.id)) },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 15.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ChannelLogo(channelLogoKey(item), textSize = 36.sp)
                        if (presentation.currentProgram != null) {
                            Box(
                                modifier = Modifier
                                    .background(TellyoColors.PrimaryBlueDark, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = TellyoColors.TextPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Text(
                        text = item.name,
                        color = TellyoColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = presentation.displayProgram?.title ?: "Programové údaje nie sú dostupné",
                        color = TellyoColors.TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = formatProgramRange(presentation.displayProgram),
                        color = TellyoColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TellyoProgressBar(
                        progress = programProgress(presentation.currentProgram),
                        modifier = Modifier.fillMaxWidth(),
                        height = 3.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgPreview(
    uiState: HomeUiState,
    firstProgramFocus: FocusRequester,
    favoriteFocus: FocusRequester,
    navigationFocus: FocusRequester,
    onAction: (HomeAction) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(296.dp),
    ) {
        val channelColumn = 171.dp
        val chevronColumn = 34.dp
        val timelineWidth = maxWidth - channelColumn - chevronColumn
        val now = Instant.now()
        val windowStart = now.truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(1))
        val windowHours = 6f
        val markerFraction = (Duration.between(windowStart, now).toMinutes() / 60f / windowHours)
            .coerceIn(0f, 1f)
        val markerX = channelColumn + timelineWidth * markerFraction

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(31.dp),
            ) {
                Text(
                    text = formatDateTime(windowStart),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
                    color = TellyoColors.TextSecondary,
                    fontSize = 14.sp,
                )
                Row(
                    modifier = Modifier
                        .offset(x = channelColumn)
                        .width(timelineWidth)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    (0 until windowHours.toInt()).forEach { hourOffset ->
                        Text(
                            text = formatTime(windowStart.plus(Duration.ofHours(hourOffset.toLong()))),
                            modifier = Modifier.weight(1f),
                            color = TellyoColors.TextSecondary,
                            fontSize = 14.sp,
                        )
                    }
                }
                Text(
                    text = formatTime(now),
                    modifier = Modifier.offset(x = markerX - 22.dp),
                    color = TellyoColors.PrimaryBlueBright,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    uiState.channels.take(5).forEachIndexed { rowIndex, channel ->
                        EpgRow(
                            channel = channel,
                            programs = uiState.presentation(channel, now).programs,
                            rowIndex = rowIndex,
                            channelColumn = channelColumn,
                            chevronColumn = chevronColumn,
                            timelineWidth = timelineWidth,
                            windowStart = windowStart,
                            windowMinutes = windowHours * 60f,
                            now = now,
                            firstProgramFocus = firstProgramFocus,
                            favoriteFocus = favoriteFocus,
                            navigationFocus = navigationFocus,
                            onAction = onAction,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .offset(x = markerX)
                        .background(TellyoColors.PrimaryBlueBright)
                        .zIndex(2f),
                )
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .offset(x = markerX - 3.5.dp, y = (-4).dp)
                        .background(TellyoColors.PrimaryBlueBright, RoundedCornerShape(50))
                        .zIndex(3f),
                )
            }
        }
    }
}

@Composable
private fun EpgRow(
    channel: Channel,
    programs: List<Program>,
    rowIndex: Int,
    channelColumn: Dp,
    chevronColumn: Dp,
    timelineWidth: Dp,
    windowStart: Instant,
    windowMinutes: Float,
    now: Instant,
    firstProgramFocus: FocusRequester,
    favoriteFocus: FocusRequester,
    navigationFocus: FocusRequester,
    onAction: (HomeAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .width(channelColumn)
                .fillMaxHeight()
                .background(TellyoColors.SurfacePrimary, RoundedCornerShape(8.dp))
                .border(1.dp, TellyoColors.Divider, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            ChannelLogo(
                logoKey = channelLogoKey(channel),
                modifier = Modifier.width(55.dp),
                textSize = 31.sp,
            )
            Text(
                text = channel.shortName,
                color = TellyoColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().focusGroup()) {
            programs.forEachIndexed { programIndex, program ->
                val startMinutes = Duration.between(windowStart, program.start).toMinutes().toFloat()
                val endMinutes = Duration.between(windowStart, program.end).toMinutes().toFloat()
                val visibleStart = startMinutes.coerceAtLeast(0f)
                val visibleEnd = endMinutes.coerceAtMost(windowMinutes)
                if (visibleEnd > visibleStart) {
                    val x = (visibleStart / windowMinutes).coerceIn(0f, 1f)
                    val width = ((visibleEnd - visibleStart) / windowMinutes).coerceIn(0f, 1f)
                    val isCurrent = program.start <= now && program.end.isAfter(now)
                    TellyoFocusableSurface(
                        modifier = Modifier
                            .offset(x = timelineWidth * x)
                            .width(timelineWidth * width)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                            .focusProperties {
                                if (rowIndex == 0 && programIndex == 0) {
                                    up = favoriteFocus
                                    left = navigationFocus
                                }
                            },
                        shape = RoundedCornerShape(6.dp),
                        selected = isCurrent,
                        focusRequester = if (rowIndex == 0 && programIndex == 0) firstProgramFocus else null,
                        contentDescription = "${channel.name}: ${program.title}",
                        focusStyle = if (isCurrent) {
                            TellyoFocusStyles.FilledSelection
                        } else {
                            TellyoFocusStyles.Standard
                        },
                        onClick = { onAction(HomeAction.SelectDestination(NavigationDestination.GUIDE)) },
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)) {
                            Text(
                                text = formatProgramRange(program),
                                color = TellyoColors.TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 12.sp,
                                maxLines = 1,
                            )
                            Text(
                                text = program.title,
                                color = TellyoColors.TextPrimary,
                                fontSize = 15.sp,
                                lineHeight = 17.sp,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .width(chevronColumn)
                .fillMaxHeight()
                .background(TellyoColors.SurfacePrimary, RoundedCornerShape(8.dp))
                .border(1.dp, TellyoColors.Divider, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            TellyoIcon(
                icon = TellyoIconType.CHEVRON_RIGHT,
                modifier = Modifier.size(22.dp),
                tint = TellyoColors.TextMuted,
            )
        }
    }
}

@Composable
private fun DestinationPlaceholder(
    destination: NavigationDestination,
    entryFocus: FocusRequester,
    autoFocusContent: Boolean,
    onReturnHome: () -> Unit,
) {
    LaunchedEffect(autoFocusContent) {
        if (autoFocusContent) entryFocus.requestFocus()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = destination.label, color = TellyoColors.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Táto obrazovka je pripravená pre ďalšiu fázu Tellyo.",
                color = TellyoColors.TextSecondary,
                fontSize = 17.sp,
            )
            TellyoButton(
                text = "Späť na Domov",
                icon = TellyoIconType.HOME,
                focusRequester = entryFocus,
                onClick = onReturnHome,
            )
        }
    }
}
