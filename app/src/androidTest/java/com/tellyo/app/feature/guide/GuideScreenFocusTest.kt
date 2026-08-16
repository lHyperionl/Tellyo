package com.tellyo.app.feature.guide

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.ui.theme.TellyoTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class GuideScreenFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstProgramReceivesFocusAndEnterOpensDetails() {
        var selected = false
        val channel = Channel(
            id = "stvr-jednotka",
            number = 1,
            name = "Jednotka",
            shortName = "Jednotka",
            logoUrl = null,
            providerId = "stvr",
            category = ChannelCategory.GENERAL,
            enabled = true,
        )
        val program = Program(
            id = "programme-1",
            channelId = channel.id,
            title = "Večerné správy",
            description = "Správy dňa",
            start = Instant.parse("2026-08-14T18:00:00Z"),
            end = Instant.parse("2026-08-14T21:00:00Z"),
            category = "news",
        )

        composeRule.setContent {
            TellyoTheme {
                GuideScreen(
                    state = GuideUiState(
                        windowStart = Instant.parse("2026-08-14T17:00:00Z"),
                        now = Instant.parse("2026-08-14T19:00:00Z"),
                        rows = listOf(GuideRowState(channel, listOf(program))),
                        isRefreshing = false,
                    ),
                    onAction = { action ->
                        if (action is GuideAction.SelectProgram) selected = true
                    },
                )
            }
        }

        composeRule.waitForIdle()
        val node = composeRule.onNodeWithContentDescription("Jednotka: Večerné správy")
        node.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertTrue(selected) }
    }
}
