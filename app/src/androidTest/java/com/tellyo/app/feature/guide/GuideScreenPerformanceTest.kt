package com.tellyo.app.feature.guide

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.ui.theme.TellyoTheme
import com.tellyo.app.feature.home.HomeScreen
import com.tellyo.app.feature.home.HomeUiState
import com.tellyo.app.feature.home.NavigationDestination
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class GuideScreenPerformanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun offscreenRowsAreDeferredAndRemoteCanSelectLastProgramme() {
        val start = Instant.now().minus(Duration.ofHours(1))
        val rows = List(60) { index ->
            val channel = Channel(
                id = "channel-$index",
                number = index + 1,
                name = "Channel $index",
                shortName = "Channel $index",
                logoUrl = null,
                providerId = "preview",
                category = ChannelCategory.GENERAL,
                enabled = true,
            )
            GuideRowState(
                channel,
                listOf(
                    Program(
                        id = "programme-$index",
                        channelId = channel.id,
                        title = "Show $index",
                        description = null,
                        start = start,
                        end = start.plus(Duration.ofHours(3)),
                        category = null,
                    ),
                ),
            )
        }
        var selectedId: String? = null
        composeRule.setContent {
            TellyoTheme {
                HomeScreen(
                    uiState = HomeUiState(selectedDestination = NavigationDestination.GUIDE),
                    onAction = {},
                    guideUiState = GuideUiState(windowStart = start, rows = rows, isRefreshing = false),
                    onGuideAction = { if (it is GuideAction.SelectProgram) selectedId = it.programId },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Channel 0: Show 0").assertIsFocused()
        composeRule.onNodeWithContentDescription("Channel 59: Show 59").assertDoesNotExist()
        repeat(59) {
            composeRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithContentDescription("Channel 59: Show 59")
            .assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertEquals("programme-59", selectedId) }
    }
}
