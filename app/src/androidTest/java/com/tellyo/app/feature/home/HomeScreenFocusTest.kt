package com.tellyo.app.feature.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.tellyo.app.core.provider.StvrTvProvider
import com.tellyo.app.core.ui.theme.TellyoTheme
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeScreenFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

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
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithContentDescription("Dvojka, Programové údaje nie sú dostupné").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeRule.onNodeWithContentDescription("Jednotka, Programové údaje nie sú dostupné").assertIsFocused()
    }

    @Test
    fun downFromHeroMovesToPlayerEntryPoint() {
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
        composeRule.onNodeWithContentDescription("Viac informácií").assertIsFocused().performKeyInput {
            pressKey(Key.Enter)
        }

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
        channels = StvrTvProvider.channels,
        selectedChannelId = StvrTvProvider.channels.first().id,
        focusedFavoriteId = StvrTvProvider.channels.first().id,
    )
}
