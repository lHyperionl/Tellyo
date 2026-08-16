package com.tellyo.app.feature.settings

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.runtime.remember
import com.tellyo.app.core.ui.theme.TellyoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsScreenFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsToggleReceivesFocusAndHandlesOk() {
        var toggled = false

        composeRule.setContent {
            TellyoTheme {
                SettingsScreen(
                    state = SettingsUiState(reduceMotion = false, isLoading = false),
                    navigationFocus = remember { FocusRequester() },
                    onAction = { action ->
                        if (action == SettingsAction.ToggleReduceMotion) toggled = true
                    },
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Obmedziť animácie: vypnuté")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        composeRule.runOnIdle { assertTrue(toggled) }
    }

    @Test
    fun qualityChoiceIsReachableFromMotionToggle() {
        var cycled = false

        composeRule.setContent {
            TellyoTheme {
                SettingsScreen(
                    state = SettingsUiState(isLoading = false),
                    navigationFocus = remember { FocusRequester() },
                    onAction = { action ->
                        if (action == SettingsAction.CycleVideoQuality) cycled = true
                    },
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Obmedziť animácie: vypnuté")
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("Kvalita videa: AUTO")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        composeRule.runOnIdle { assertTrue(cycled) }
    }
}
