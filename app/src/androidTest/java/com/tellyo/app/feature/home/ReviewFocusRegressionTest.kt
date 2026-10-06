package com.tellyo.app.feature.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.settings.FavoriteSettings
import com.tellyo.app.core.ui.theme.TellyoTheme
import com.tellyo.app.feature.guide.*
import com.tellyo.app.feature.diagnostics.*
import java.time.Instant
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ReviewFocusRegressionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun homeChannelRowScrollsPastInitiallyComposedCardsAndReturnsFromHero() {
        val channels = (0 until 30).map(::channel)
        rule.setContent { TellyoTheme {
            HomeScreen(HomeUiState(channels = channels, selectedChannelId = channels.first().id, isLoading = false), {})
        } }
        rule.waitForIdle()
        repeat(29) { rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }; rule.waitForIdle() }
        rule.onNodeWithContentDescription("Channel 29, Programové údaje nie sú dostupné").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionDown) }
        rule.onNode(isFocused()).assert(hasContentDescription("Channel 29, Programové údaje nie sú dostupné"))
    }

    @Test fun favoritesMovesUpFromSecondRowAndScrollsToLastChannel() {
        val channels = (0 until 60).map(::channel)
        rule.setContent {
            TellyoTheme {
                HomeScreen(HomeUiState(channels = channels, isLoading = false,
                    selectedDestination = NavigationDestination.FAVORITES,
                    favoriteSettings = FavoriteSettings(true, channels.map { it.id }.toSet())), {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Channel 0: v obľúbených").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Channel 1: v obľúbených").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Channel 0: v obľúbených").assertIsFocused()
        repeat(59) { rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; rule.waitForIdle() }
        rule.onNodeWithContentDescription("Channel 59: v obľúbených").assertIsFocused().assertIsDisplayed()
    }

    @Test fun guideSkipsBoundaryProgrammeAndEmptyFirstChannel() {
        val start = Instant.parse("2026-10-03T12:00:00Z")
        val old = program(channel(1), "Expired", start.minusSeconds(3600), start)
        val visible = program(channel(1), "Visible", start, start.plusSeconds(3600))
        rule.setContent {
            TellyoTheme {
                GuideScreen(GuideUiState(windowStart = start, now = start, rows = listOf(
                    GuideRowState(channel(0)), GuideRowState(channel(1), listOf(old, visible)),
                ), isRefreshing = false), onAction = {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Channel 1: Visible").assertIsFocused()
        rule.onNodeWithContentDescription("Channel 1: Expired").assertDoesNotExist()
    }

    @Test fun homeDownEntersFirstVisibleProgrammeEvenWithExpiredStoredRecord() {
        val now = Instant.parse("2026-10-03T12:30:00Z")
        val channel = channel(0)
        val old = program(channel, "Expired", now.minusSeconds(7200), now.minusSeconds(5400))
        val visible = program(channel, "Visible", now.minusSeconds(1800), now.plusSeconds(1800))
        rule.setContent { TellyoTheme {
            HomeScreen(HomeUiState(now = now, channels = listOf(channel), selectedChannelId = channel.id,
                programsByChannel = mapOf(channel.id to listOf(old, visible)), isLoading = false), {})
        } }
        rule.waitForIdle()
        rule.onNode(isFocused()).performKeyInput {
            pressKey(Key.DirectionDown)
        }
        rule.onNodeWithContentDescription("Channel 0: Visible").assertIsFocused()
    }

    @Test fun homeWithNoProgrammesStillHasAnEpgEntryTarget() {
        val channel = channel(0)
        rule.setContent { TellyoTheme {
            HomeScreen(HomeUiState(channels = listOf(channel), selectedChannelId = channel.id, isLoading = false), {})
        } }
        rule.waitForIdle()
        rule.onNode(isFocused()).performKeyInput {
            pressKey(Key.DirectionDown)
        }
        rule.onNode(isFocused()).assert(hasContentDescription("Celý program"))
    }

    @Test fun diagnosticsVirtualizesAndScrollsToFinalResult() {
        val health = (0 until 64).map { ChannelHealth(channel(it)) }
        rule.setContent { TellyoTheme {
            HomeScreen(HomeUiState(selectedDestination = NavigationDestination.DIAGNOSTICS), {},
                diagnosticsUiState = DiagnosticsUiState(channelHealth = health))
        } }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Channel 63: Idle").assertDoesNotExist()
        repeat(65) { rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; rule.waitForIdle() }
        rule.onNodeWithContentDescription("Channel 63: Idle").assertIsDisplayed().assertIsFocused()
    }

    private fun channel(index: Int) = Channel("review-$index", index + 1, "Channel $index", "C$index", null,
        "review", ChannelCategory.GENERAL, true)
    private fun program(channel: Channel, title: String, start: Instant, end: Instant) =
        Program("${channel.id}-$title", channel.id, title, null, start, end, null)
}
