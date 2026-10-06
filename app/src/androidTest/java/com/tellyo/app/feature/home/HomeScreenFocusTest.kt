package com.tellyo.app.feature.home

import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.tellyo.app.core.provider.StvrTvProvider
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.ui.theme.LocalTellyoReduceMotion
import com.tellyo.app.core.ui.theme.TellyoTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeScreenFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun returningFromPlaybackFocusesWatchedChannelAndScrollsOffScreenCardIntoView() {
        val initial = loadedHomeState()
        val channels = initial.channels + List(50) { index ->
            initial.channels.first().copy(id = "return-$index", name = "Return $index", number = 100 + index)
        }
        var state by mutableStateOf(initial.copy(
            channels = channels,
            selectedChannelId = "return-49",
            focusedFavoriteId = "return-49",
            restoreChannelFocus = true,
        ))
        composeRule.setContent {
            TellyoTheme {
                HomeScreen(state, onAction = { action ->
                    if (action == HomeAction.ChannelFocusRestored) state = state.copy(restoreChannelFocus = false)
                })
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Return 49, Programové údaje nie sú dostupné")
            .assertIsDisplayed().assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithContentDescription("Return 48, Programové údaje nie sú dostupné").assertIsFocused()
        composeRule.runOnIdle { state = state.copy(now = state.now.plusSeconds(60)) }
        composeRule.onNodeWithContentDescription("Return 48, Programové údaje nie sú dostupné").assertIsFocused()
    }

    @Test
    fun browsedChannelHighlightFollowsFocusAndPersistsWithoutStartingPlayback() {
        var state by mutableStateOf(loadedHomeState())
        var playedChannelId: String? = null
        composeRule.setContent {
            TellyoTheme {
                HomeScreen(
                    uiState = state,
                    onAction = { action ->
                        when (action) {
                            is HomeAction.FocusFavorite -> state = state.copy(
                                focusedFavoriteId = action.channelId,
                                selectedChannelId = action.channelId,
                            )
                            is HomeAction.PlayChannel -> playedChannelId = action.channelId
                            else -> Unit
                        }
                    },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Domov").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionDown)
        }
        composeRule.onNodeWithContentDescription("Jednotka, Programové údaje nie sú dostupné")
            .assertIsFocused().assertIsSelected().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        val secondChannel = composeRule.onNodeWithContentDescription("Dvojka, Programové údaje nie sú dostupné")
        secondChannel.assertIsFocused().assertIsSelected()
        composeRule.onNodeWithContentDescription("Jednotka, Programové údaje nie sú dostupné")
            .assertIsNotSelected()
        composeRule.runOnIdle {
            check(state.selectedChannelId == StvrTvProvider.DVOJKA_ID)
            check(playedChannelId == null)
        }
        secondChannel.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        secondChannel.assertIsSelected()
        composeRule.onNodeWithContentDescription("Náhľad Dvojka").assertIsFocused()
    }

    @Test
    fun longProgrammeTitleKeepsSynopsisAndPlayablePreviewVisible() {
        assertHeroActionFits(fontScale = 1f)
    }

    @Test
    fun enlargedTextKeepsSynopsisVisibleWithReducedMotion() {
        assertHeroActionFits(fontScale = 1.4f)
    }

    private fun assertHeroActionFits(fontScale: Float) {
        val now = Instant.now()
        val initial = loadedHomeState()
        val programme = Program(
            id = "long-title",
            channelId = StvrTvProvider.JEDNOTKA_ID,
            title = "Majstrovstvá Európy: Slovensko proti Portugalsku, priamy prenos a rozhovory po zápase",
            description = "Priamy prenos zo záverečného zápasu s komentárom a rozhovormi s hráčmi po skončení vysielania.",
            start = now.minusSeconds(600),
            end = now.plusSeconds(3600),
            category = "sport",
        )
        var openedPlayer = false
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalTellyoReduceMotion provides true,
            ) {
                TellyoTheme {
                    HomeScreen(
                        uiState = initial.copy(
                            programsByChannel = mapOf(programme.channelId to listOf(programme)),
                        ),
                        onAction = { if (it == HomeAction.OpenPlayer) openedPlayer = true },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val heroBounds = composeRule.onNodeWithContentDescription("Náhľad Jednotka")
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithContentDescription("Viac informácií").assertDoesNotExist()
        val synopsis = composeRule.onNode(
            hasText(requireNotNull(programme.description)) and
                hasAnyAncestor(hasContentDescription("Náhľad Jednotka")),
            useUnmergedTree = true,
        )
        synopsis.assertIsDisplayed()
        assertTrue("Synopsis must fit inside the hero", synopsis.fetchSemanticsNode().boundsInRoot.bottom <= heroBounds.bottom)
        composeRule.onNodeWithContentDescription("Domov").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithContentDescription("Náhľad Jednotka")
            .assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertTrue(openedPlayer) }
    }

    @Test
    fun remoteCanReachAndPlayTheLastChannelInALargePlaylist() {
        val initial = loadedHomeState()
        val added = List(50) { index ->
            initial.channels.first().copy(
                id = "iptv-test-$index",
                number = 100 + index,
                name = "IPTV $index",
                shortName = "IPTV $index",
                providerId = "slovak-iptv",
            )
        }
        var playedChannelId: String? = null
        composeRule.setContent {
            TellyoTheme {
                HomeScreen(
                    uiState = initial.copy(channels = initial.channels + added),
                    onAction = { if (it is HomeAction.PlayChannel) playedChannelId = it.channelId },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Domov").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionDown)
        }
        repeat(initial.channels.size + added.size - 1) {
            composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithContentDescription("IPTV 49, Programové údaje nie sú dostupné")
            .assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { check(playedChannelId == "iptv-test-49") }
    }

    @Test
    fun initialFocusStartsOnHomeAndDownMovesToGuide() {
        composeRule.setContent {
            TellyoTheme {
                HomeScreen(
                    uiState = loadedHomeState(),
                    onAction = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Domov").assertIsFocused()
        composeRule.onNodeWithContentDescription("Domov").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.onNodeWithContentDescription("TV program").assertIsFocused()
    }

    @Test
    fun upFromFavoritesTraversesBackToHome() {
        composeRule.setContent {
            TellyoTheme {
                HomeScreen(
                    uiState = loadedHomeState(),
                    onAction = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Domov").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
        }
        composeRule.onNodeWithContentDescription("Obľúbené").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionUp)
        }
        composeRule.onNodeWithContentDescription("Domov").assertIsFocused()
    }

    @Test
    fun rightFromNavigationMovesFocusToHero() {
        composeRule.setContent {
            TellyoTheme {
                HomeScreen(
                    uiState = loadedHomeState(),
                    onAction = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Domov").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithContentDescription("Náhľad Jednotka").assertIsFocused()
    }

    @Test
    fun leftFromSecondFavoriteMovesToFirstFavorite() {
        composeRule.setContent {
            TellyoTheme {
                HomeScreen(
                    uiState = loadedHomeState(),
                    onAction = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Domov").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithContentDescription("Dvojka, Programové údaje nie sú dostupné").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeRule.onNodeWithContentDescription("Jednotka, Programové údaje nie sú dostupné").assertIsFocused()
    }

    @Test
    fun downFromHeroMovesDirectlyToChannelRow() {
        var openedPlayer = false

        composeRule.setContent {
            TellyoTheme {
                HomeScreen(
                    uiState = loadedHomeState(),
                    onAction = { action ->
                        if (action == HomeAction.OpenPlayer) openedPlayer = true
                    },
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Domov").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Náhľad Jednotka").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Jednotka, Programové údaje nie sú dostupné")
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithContentDescription("Náhľad Jednotka")
            .assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { check(openedPlayer) }
    }

    @Test
    fun pressingOkOnFavoriteStartsThatChannel() {
        var playedChannelId: String? = null

        composeRule.setContent {
            TellyoTheme {
                HomeScreen(
                    uiState = loadedHomeState(),
                    onAction = { action ->
                        if (action is HomeAction.PlayChannel) {
                            playedChannelId = action.channelId
                        }
                    },
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Domov").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionDown)
        }
        composeRule.onNodeWithContentDescription("Jednotka, Programové údaje nie sú dostupné")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        composeRule.runOnIdle {
            check(playedChannelId == StvrTvProvider.JEDNOTKA_ID)
        }
    }

    private fun loadedHomeState() = HomeUiState(
        channels = listOf(
            Channel(
                id = StvrTvProvider.JEDNOTKA_ID,
                number = 1,
                name = "Jednotka",
                shortName = "Jednotka",
                logoUrl = null,
                providerId = StvrTvProvider.PROVIDER_ID,
                category = ChannelCategory.GENERAL,
                enabled = true,
            ),
            Channel(
                id = StvrTvProvider.DVOJKA_ID,
                number = 2,
                name = "Dvojka",
                shortName = "Dvojka",
                logoUrl = null,
                providerId = StvrTvProvider.PROVIDER_ID,
                category = ChannelCategory.CULTURE,
                enabled = true,
            ),
            Channel(
                id = StvrTvProvider.NEWS_24_ID,
                number = 3,
                name = ":24",
                shortName = ":24",
                logoUrl = null,
                providerId = StvrTvProvider.PROVIDER_ID,
                category = ChannelCategory.NEWS,
                enabled = true,
            ),
            Channel(
                id = StvrTvProvider.SPORT_ID,
                number = 4,
                name = "Šport",
                shortName = "Šport",
                logoUrl = null,
                providerId = StvrTvProvider.PROVIDER_ID,
                category = ChannelCategory.SPORTS,
                enabled = true,
            ),
        ),
        selectedChannelId = StvrTvProvider.JEDNOTKA_ID,
        focusedFavoriteId = StvrTvProvider.JEDNOTKA_ID,
    )
}
