package com.tellyo.app.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
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
import java.time.format.DateTimeFormatter
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
    var initialChannelFocusPending by remember {
        mutableStateOf(uiState.selectedDestination == NavigationDestination.HOME && !uiState.restoreChannelFocus)
    }

    BackHandler(
        enabled = uiState.selectedDestination != NavigationDestination.HOME,
    ) {
        onAction(HomeAction.SelectDestination(NavigationDestination.HOME))
    }

    LaunchedEffect(uiState.selectedDestination) {
        if (uiState.selectedDestination != NavigationDestination.HOME) {
            initialChannelFocusPending = false
        } else if (!uiState.restoreChannelFocus && (!initialChannelFocusPending || uiState.channels.isEmpty())) {
            firstNavigationFocus.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TellyoColors.BackgroundPrimary)
            .padding(start = 48.dp, top = 42.dp, end = 48.dp, bottom = 38.dp),
    ) {
        NavigationSidebar(
            now = uiState.now,
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

        Spacer(modifier = Modifier.width(40.dp))

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (uiState.selectedDestination == NavigationDestination.HOME) {
                DashboardContent(
                    uiState = uiState,
                    focusFirstChannel = initialChannelFocusPending,
                    onInitialChannelFocusHandled = { initialChannelFocusPending = false },
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

private fun HomeUiState.presentation(channel: Channel, now: Instant = this.now): ChannelPresentation {
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
    else -> channel.shortName
}

private fun formatTime(instant: Instant): String =
    timeFormatter.withZone(ZoneId.systemDefault()).format(instant)

private fun formatDateTime(instant: Instant): String =
    dateFormatter
        .withZone(ZoneId.systemDefault())
        .format(instant)

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale("sk", "SK"))

private fun formatProgramRange(program: Program?): String =
    program?.let { "${formatTime(it.start)} - ${formatTime(it.end)}" } ?: ""

private fun programProgress(program: Program?, now: Instant = Instant.now()): Float {
    if (program == null || !program.end.isAfter(program.start)) return 0f
    return Duration.between(program.start, now).toMillis()
        .toFloat()
        .div(Duration.between(program.start, program.end).toMillis().toFloat())
        .coerceIn(0f, 1f)
}

@Composable
private fun NavigationSidebar(
    now: Instant,
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
            .width(224.dp)
            .fillMaxHeight(),
    ) {
        TellyoLogo(
            modifier = Modifier.padding(start = 14.dp),
            iconSize = 37.dp,
            textSize = 42.sp,
        )
        Spacer(modifier = Modifier.height(48.dp))

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
                shape = RoundedCornerShape(10.dp),
                selected = selectedDestination == destination,
                focusRequester = if (index == 0) firstNavigationFocus else itemFocusRequesters[index],
                contentDescription = destination.label,
                focusStyle = TellyoFocusStyles.Navigation,
                onClick = { onDestinationSelected(destination) },
            ) {
                Row(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TellyoIcon(
                        icon = icon,
                        modifier = Modifier.size(25.dp),
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
                        fontSize = 24.sp,
                        fontWeight = if (selectedDestination == destination) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.weight(1f))
        Column(modifier = Modifier.padding(start = 18.dp, bottom = 12.dp)) {
            Text(
                text = DateTimeFormatter.ofPattern("EEEE", Locale("sk", "SK"))
                    .withZone(ZoneId.systemDefault())
                    .format(now)
                    .replaceFirstChar { it.titlecase(Locale("sk", "SK")) },
                color = TellyoColors.TextSecondary,
                fontSize = 22.sp,
            )
            Text(text = formatDateTime(now), color = TellyoColors.TextSecondary, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = formatTime(now),
                color = TellyoColors.TextPrimary,
                fontSize = 46.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: HomeUiState,
    focusFirstChannel: Boolean,
    onInitialChannelFocusHandled: () -> Unit,
    heroFocus: FocusRequester,
    navigationFocus: FocusRequester,
    onAction: (HomeAction) -> Unit,
) {
    val favoriteFocus = remember { FocusRequester() }
    val epgFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HeroPanel(
            uiState = uiState,
            heroFocus = heroFocus,
            navigationFocus = navigationFocus,
            favoriteFocus = favoriteFocus,
            onAction = onAction,
        )

        SectionHeading(text = "Kanály")
        FavoriteChannels(
            uiState = uiState,
            focusFirstChannel = focusFirstChannel,
            onInitialChannelFocusHandled = onInitialChannelFocusHandled,
            firstFocus = favoriteFocus,
            heroFocus = heroFocus,
            epgFocus = epgFocus,
            navigationFocus = navigationFocus,
            onAction = onAction,
        )

        SectionHeading(text = "TV program")
        EpgPreview(
            uiState = uiState,
            modifier = Modifier.weight(1f),
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
    favoriteFocus: FocusRequester,
    onAction: (HomeAction) -> Unit,
) {
    val selected = uiState.channels
        .firstOrNull { it.id == uiState.selectedChannelId }
        ?.let { uiState.presentation(it) }
    val largeText = LocalDensity.current.fontScale > 1.15f
    TellyoFocusableSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp)
            .focusProperties {
                left = navigationFocus
                down = favoriteFocus
            },
        shape = RoundedCornerShape(16.dp),
        focusStyle = TellyoFocusStyles.Hero,
        focusRequester = heroFocus,
        contentDescription = "Náhľad ${selected?.channel?.name ?: "Tellyo"}",
        onClick = { onAction(HomeAction.OpenPlayer) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (selected != null) {
                ProgramPreviewArtwork(
                    presentation = selected,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(620.dp),
                )
            }
            // Shade the complete hero, so the artwork cannot introduce a seam.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to TellyoColors.BackgroundPrimary.copy(alpha = 0.55f),
                        ),
                    ),
                ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(620.dp)
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (selected == null) {
                    Text(
                        text = if (uiState.isLoading) "Načítavam kanály…" else {
                            uiState.dataError ?: "Kanály nie sú dostupné."
                        },
                        color = TellyoColors.TextSecondary,
                        fontSize = 24.sp,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ChannelLogo(
                            channelLogoKey(selected.channel),
                            modifier = Modifier.width(120.dp),
                            textSize = 36.sp,
                            logoUrl = selected.channel.logoUrl,
                        )
                        Text(
                            text = selected.channel.name,
                            modifier = Modifier.weight(1f, fill = false),
                            color = TellyoColors.TextSecondary,
                            fontSize = 26.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected.currentProgram != null) LiveBadge()
                    }
                    Text(
                        text = selected.displayProgram?.title ?: selected.channel.name,
                        color = TellyoColors.TextPrimary,
                        fontSize = 44.sp,
                        lineHeight = 50.sp,
                        letterSpacing = (-0.7).sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (largeText) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    selected.displayProgram?.let { program ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Text(
                                text = formatProgramRange(program),
                                color = TellyoColors.TextSecondary,
                                fontSize = 22.sp,
                                lineHeight = 28.sp,
                            )
                            if (selected.currentProgram != null) TellyoProgressBar(
                                progress = programProgress(selected.currentProgram, uiState.now),
                                modifier = Modifier.width(150.dp),
                                height = 3.dp,
                            )
                        }
                    }
                    val description = selected.displayProgram?.description?.takeIf { it.isNotBlank() }
                        ?: if (selected.displayProgram == null) {
                            "Programový sprievod pre tento kanál nie je dostupný."
                        } else null
                    if (description != null) Text(
                        text = description,
                        modifier = Modifier.weight(1f, fill = false),
                        color = TellyoColors.TextSecondary,
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        maxLines = if (largeText) 2 else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramPreviewArtwork(
    presentation: ChannelPresentation,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        val program = presentation.currentProgram
        if (program?.artworkUrl != null) {
            SubcomposeAsyncImage(
                model = program.artworkUrl,
                contentDescription = "Obrazový náhľad programu ${program.title}",
                // Preserve the broadcaster's complete thumbnail, including heads
                // and captions. Wide hero geometry must not enlarge a 16:9 image.
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize(),
                loading = { ChannelArtworkFallback(presentation) },
                error = { ChannelArtworkFallback(presentation) },
                success = { SubcomposeAsyncImageContent() },
            )
        } else {
            // Use channel identity when programme artwork is unavailable.
            // A generic photograph would imply content the provider did not supply.
            ChannelArtworkFallback(presentation)
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to TellyoColors.SurfacePrimary,
                        0.1f to TellyoColors.SurfacePrimary.copy(alpha = 0.75f),
                        0.25f to TellyoColors.SurfacePrimary.copy(alpha = 0.1f),
                        0.35f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                ),
            ),
        )
    }
}

@Composable
private fun ChannelArtworkFallback(presentation: ChannelPresentation) {
    Box(
        modifier = Modifier.fillMaxSize().background(TellyoColors.BackgroundSecondary),
        contentAlignment = Alignment.Center,
    ) {
        ChannelLogo(
            logoKey = channelLogoKey(presentation.channel),
            large = true,
            textSize = 90.sp,
            logoUrl = presentation.channel.logoUrl,
            modifier = Modifier.width(240.dp).height(130.dp),
        )
    }
}

@Composable
private fun FavoriteChannels(
    uiState: HomeUiState,
    focusFirstChannel: Boolean,
    onInitialChannelFocusHandled: () -> Unit,
    firstFocus: FocusRequester,
    heroFocus: FocusRequester,
    epgFocus: FocusRequester,
    navigationFocus: FocusRequester,
    onAction: (HomeAction) -> Unit,
) {
    // Keep every registered channel reachable through the horizontally
    // navigable row; LazyRow provides the off-screen scrolling on TV.
    val channels = remember(uiState.channels, uiState.favoriteSettings) { uiState.homeChannels() }
    val largeText = LocalDensity.current.fontScale > 1.15f
    val channelIds = remember(channels) { channels.map { it.id } }
    val cardFocusRequesters = remember(channelIds) {
        List(channelIds.size) { FocusRequester() }
    }
    var focusedCardId by remember(channelIds) {
        mutableStateOf(uiState.selectedChannelId.takeIf { it in channelIds } ?: channelIds.firstOrNull())
    }
    val listState = rememberLazyListState()
    LaunchedEffect(focusFirstChannel, uiState.restoreChannelFocus, channelIds) {
        if ((focusFirstChannel || uiState.restoreChannelFocus) && channels.isNotEmpty()) {
            val index = if (uiState.restoreChannelFocus) {
                channelIds.indexOf(uiState.selectedChannelId).coerceAtLeast(0)
            } else {
                0
            }
            focusedCardId = channelIds[index]
            listState.scrollToItem(index)
            // Off-screen cards must be composed and attached before requesting focus.
            withFrameNanos { }
            cardFocusRequesters[index].requestFocus()
            onInitialChannelFocusHandled()
            if (uiState.restoreChannelFocus) onAction(HomeAction.ChannelFocusRestored)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .focusGroup(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(channels, key = { _, item -> item.id }) { index, item ->
            val presentation = uiState.presentation(item)
            TellyoFocusableSurface(
                modifier = Modifier
                    .width(208.dp)
                    .fillMaxHeight()
                    .then(if (item.id == focusedCardId) Modifier.focusRequester(firstFocus) else Modifier)
                    .semantics { selected = item.id == uiState.focusedFavoriteId }
                    .focusProperties {
                        up = heroFocus
                        down = epgFocus
                        // LazyRow's focus search composes and scrolls off-screen
                        // cards. Explicit neighbour requesters may be unattached.
                        if (index == 0) left = navigationFocus
                    },
                shape = RoundedCornerShape(12.dp),
                selected = item.id == uiState.focusedFavoriteId,
                focusStyle = TellyoFocusStyles.Channel,
                focusRequester = cardFocusRequesters[index],
                contentDescription = "${item.name}, ${presentation.displayProgram?.title ?: "Programové údaje nie sú dostupné"}",
                onFocusChanged = { focused ->
                    if (focused) {
                        focusedCardId = item.id
                        onAction(HomeAction.FocusFavorite(item.id))
                    }
                },
                onClick = { onAction(HomeAction.PlayChannel(item.id)) },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        ChannelLogo(
                            channelLogoKey(item),
                            textSize = 28.sp,
                            logoUrl = item.logoUrl,
                        )
                    }
                    Text(
                        text = item.name,
                        color = TellyoColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = presentation.displayProgram?.title ?: "Program nie je dostupný",
                        color = TellyoColors.TextSecondary,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!largeText) Text(
                        text = formatProgramRange(presentation.displayProgram),
                        color = TellyoColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (presentation.currentProgram != null) TellyoProgressBar(
                        progress = programProgress(presentation.currentProgram, uiState.now),
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
    modifier: Modifier = Modifier,
    firstProgramFocus: FocusRequester,
    favoriteFocus: FocusRequester,
    navigationFocus: FocusRequester,
    onAction: (HomeAction) -> Unit,
) {
    val channel = uiState.channels.firstOrNull { it.id == uiState.selectedChannelId }
        ?: uiState.channels.firstOrNull()
    val now = uiState.now
    val programs = channel?.let { uiState.presentation(it, now).programs }
        .orEmpty().filter { it.end > now }
    val listState = rememberLazyListState()
    LaunchedEffect(channel?.id) { listState.scrollToItem(0) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = formatDateTime(now), color = TellyoColors.TextSecondary, fontSize = 18.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "Teraz a nasleduje", color = TellyoColors.TextSecondary, fontSize = 18.sp)
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            channel?.let {
                Column(
                    modifier = Modifier.width(171.dp).fillMaxHeight()
                        .background(TellyoColors.SurfacePrimary, RoundedCornerShape(12.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ChannelLogo(channelLogoKey(it), logoUrl = it.logoUrl,
                        modifier = Modifier.width(100.dp), textSize = 54.sp)
                    Text(text = it.shortName, color = TellyoColors.TextPrimary,
                        fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (programs.isEmpty()) Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(TellyoColors.SurfacePrimary, RoundedCornerShape(12.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                Text(text = "Programové údaje nie sú dostupné", color = TellyoColors.TextSecondary, fontSize = 22.sp)
                TellyoButton(
                    text = "Celý program",
                    focusRequester = firstProgramFocus,
                    modifier = Modifier.focusProperties { up = favoriteFocus; left = navigationFocus },
                    onClick = { onAction(HomeAction.SelectDestination(NavigationDestination.GUIDE)) },
                )
            } else LazyRow(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxHeight().focusGroup(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(programs, key = { _, program -> program.id }) { index, program ->
                    val isCurrent = program.start <= now && program.end.isAfter(now)
                    TellyoFocusableSurface(
                        modifier = Modifier.width(320.dp).fillMaxHeight().focusProperties {
                            up = favoriteFocus
                            if (index == 0) left = navigationFocus
                        },
                        shape = RoundedCornerShape(12.dp),
                        selected = isCurrent,
                        focusRequester = if (index == 0) firstProgramFocus else null,
                        contentDescription = "${channel?.name}: ${program.title}",
                        focusStyle = if (isCurrent) TellyoFocusStyles.FilledSelection else TellyoFocusStyles.Standard,
                        onClick = { onAction(HomeAction.SelectDestination(NavigationDestination.GUIDE)) },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = formatProgramRange(program), color = TellyoColors.TextSecondary,
                                    fontSize = 16.sp, lineHeight = 20.sp)
                                if (isCurrent) LiveBadge()
                            }
                            Text(text = program.title, color = TellyoColors.TextPrimary, fontSize = 24.sp,
                                lineHeight = 30.sp, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (!program.description.isNullOrBlank()) Text(
                                text = program.description, modifier = Modifier.weight(1f, fill = false),
                                color = TellyoColors.TextSecondary,
                                fontSize = 18.sp, lineHeight = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
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
