package com.tellyo.app.feature.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.tellyo.app.core.player.PlaybackState
import com.tellyo.app.core.ui.theme.TellyoTheme
import org.junit.Rule
import org.junit.Test

class PlayerControlsFocusTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pauseShowsControlsAndFocusesResumeActionAfterTheyAttach() {
        val state = mutableStateOf<PlaybackState>(PlaybackState.Playing("fixture"))
        rule.setContent { TellyoTheme {
            PlayerScreen(channelName = "Fixture", player = null, playbackState = state.value,
                onBack = {}, onRetry = {})
        } }
        rule.waitForIdle()
        rule.runOnIdle { state.value = PlaybackState.Paused("fixture") }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Prehrať").assertIsFocused()
    }
}
