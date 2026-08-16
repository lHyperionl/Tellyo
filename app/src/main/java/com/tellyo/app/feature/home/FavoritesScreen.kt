package com.tellyo.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.settings.FavoriteSettings
import com.tellyo.app.core.ui.components.ChannelLogo
import com.tellyo.app.core.ui.components.SectionHeading
import com.tellyo.app.core.ui.components.TellyoFocusableSurface
import com.tellyo.app.core.ui.components.TellyoFocusStyles
import com.tellyo.app.core.ui.theme.TellyoColors

@Composable
fun FavoritesScreen(
    channels: List<Channel>,
    favoriteSettings: FavoriteSettings,
    navigationFocus: FocusRequester,
    entryFocus: FocusRequester,
    autoFocusContent: Boolean,
    onToggle: (String) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    val orderedChannels = remember(channels, favoriteSettings) {
        channels.orderedForManagement(favoriteSettings)
    }
    val rowFocusRequesters = remember(orderedChannels.map(Channel::id)) {
        List(orderedChannels.size) { FocusRequester() }
    }

    LaunchedEffect(orderedChannels, autoFocusContent) {
        if (autoFocusContent) entryFocus.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 18.dp, start = 8.dp, end = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Obľúbené",
            color = TellyoColors.TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Enter pridá alebo odstráni kanál · šípky doľava/doprava menia poradie",
            color = TellyoColors.TextSecondary,
            fontSize = 16.sp,
        )
        SectionHeading(text = "Kanály")

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            itemsIndexed(orderedChannels, key = { _, channel -> channel.id }) { index, channel ->
                val isFavorite = favoriteSettings.channelIds.contains(channel.id)
                TellyoFocusableSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(65.dp)
                        .focusProperties {
                            left = navigationFocus
                            if (index > 0) up = rowFocusRequesters[index - 1]
                            if (index < orderedChannels.lastIndex) down = rowFocusRequesters[index + 1]
                        },
                    shape = RoundedCornerShape(10.dp),
                    // Membership is communicated by the right-hand status;
                    // reserve the blue border for the one remotely focused row.
                    selected = false,
                    focusRequester = if (index == 0) entryFocus else rowFocusRequesters[index],
                    contentDescription = "${channel.name}: ${if (isFavorite) "v obľúbených" else "nie je v obľúbených"}",
                    focusStyle = TellyoFocusStyles.Standard,
                    onKeyInput = { key, type ->
                        if (type != KeyEventType.KeyUp || !isFavorite) {
                            false
                        } else {
                            when (key) {
                                Key.DirectionLeft -> {
                                    onMove(channel.id, -1)
                                    true
                                }
                                Key.DirectionRight -> {
                                    onMove(channel.id, 1)
                                    true
                                }
                                else -> false
                            }
                        }
                    },
                    onClick = { onToggle(channel.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelLogo(logoKey = channelLogoKey(channel), textSize = 32.sp)
                        Spacer(modifier = Modifier.width(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${channel.number}.  ${channel.name}",
                                color = TellyoColors.TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = channel.providerId.uppercase(),
                                color = TellyoColors.TextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                        Text(
                            text = if (isFavorite) "PRIDANÝ" else "PRIDAŤ",
                            color = if (isFavorite) TellyoColors.PrimaryBlueBright else TellyoColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

internal fun HomeUiState.homeChannels(): List<Channel> = channels.orderedForHome(favoriteSettings)

internal fun List<Channel>.orderedForHome(settings: FavoriteSettings): List<Channel> =
    orderedForManagement(settings)

private fun List<Channel>.orderedForManagement(settings: FavoriteSettings): List<Channel> {
    if (isEmpty()) return emptyList()
    val favorites = if (settings.configured) settings.channelIds else map(Channel::id).toSet()
    val byId = associateBy(Channel::id)
    val orderedFavoriteIds = settings.orderedChannelIds + map(Channel::id)
    val favoriteChannels = orderedFavoriteIds
        .distinct()
        .mapNotNull { id -> byId[id]?.takeIf { id in favorites } }
    return favoriteChannels + filter { it.id !in favorites }
}
