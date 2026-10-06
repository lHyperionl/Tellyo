package com.tellyo.app.feature.guide

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.ui.theme.TellyoTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class GuideScreenFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun hourLabelIsAlignedWithProgrammeStartingAtThatHour() {
        val channel = Channel("timeline", 1, "Timeline", "T", null, "test", ChannelCategory.GENERAL, true)
        val start = Instant.parse("2026-10-06T12:00:00Z")
        val programStart = start.plusSeconds(7200)
        val program = Program("timeline-show", channel.id, "Show", null, programStart, programStart.plusSeconds(3600), null)
        composeRule.setContent { TellyoTheme {
            GuideScreen(GuideUiState(windowStart = start, now = start,
                rows = listOf(GuideRowState(channel, listOf(program))), isRefreshing = false), onAction = {})
        } }
        composeRule.waitForIdle()
        val label = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(programStart)
        // The timeline header precedes programme cells; a narrow cell may also show only its start time.
        val labelLeft = composeRule.onAllNodesWithText(label)[0].getUnclippedBoundsInRoot().left.value
        val programmeLeft = composeRule.onNodeWithContentDescription("Timeline: Show").getUnclippedBoundsInRoot().left.value
        assertTrue("Hour label $labelLeft must match programme $programmeLeft", kotlin.math.abs(labelLeft - programmeLeft) < 2f)
    }

    @Test
    fun detailReceivesFocusAndCanOpenChannelWithRemote() {
        val channel = Channel("detail", 1, "Detail", "D", null, "test", ChannelCategory.GENERAL, true)
        val start = Instant.parse("2026-10-06T12:00:00Z")
        val program = Program("detail-show", channel.id, "Show", "Description", start, start.plusSeconds(3600), null)
        var opened = false
        composeRule.setContent {
            val state = remember { mutableStateOf(GuideUiState(windowStart = start, now = start,
                rows = listOf(GuideRowState(channel, listOf(program))), isRefreshing = false)) }
            TellyoTheme {
                GuideScreen(state.value, onAction = { action ->
                    when (action) {
                        is GuideAction.SelectProgram -> state.value = state.value.copy(selectedProgram = program)
                        is GuideAction.OpenChannel -> opened = true
                        else -> Unit
                    }
                })
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Detail: Show").performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Otvoriť kanál").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertTrue(opened) }
    }

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
