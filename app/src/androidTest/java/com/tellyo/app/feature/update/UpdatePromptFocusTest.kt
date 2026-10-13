package com.tellyo.app.feature.update

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.tellyo.app.core.ui.theme.TellyoTheme
import com.tellyo.app.core.update.AppUpdate
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class UpdatePromptFocusTest {
    @get:Rule val composeRule = createComposeRule()
    private val update = AppUpdate(1001, "0.1.1001", "https://github.com/example/app.apk", "a".repeat(64), 4)

    @Test fun updateReceivesFocusAndHandlesRemoteOk() {
        var accepted = false
        composeRule.setContent {
            TellyoTheme {
                UpdatePrompt(UpdateUiState(update = update), { accepted = true }, {}, {})
            }
        }
        composeRule.onNodeWithContentDescription("Aktualizovať").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertTrue(accepted) }
    }

    @Test fun laterIsReachableWithRemoteAndDismisses() {
        var dismissed = false
        composeRule.setContent {
            TellyoTheme {
                UpdatePrompt(UpdateUiState(update = update), {}, {}, { dismissed = true })
            }
        }
        composeRule.onNodeWithContentDescription("Aktualizovať")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithContentDescription("Neskôr").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun downloadCanBeCancelledWithRemote() {
        var dismissed = false
        composeRule.setContent {
            TellyoTheme {
                UpdatePrompt(UpdateUiState(update = update, phase = UpdatePhase.DOWNLOADING, progress = .5f),
                    {}, {}, { dismissed = true })
            }
        }
        composeRule.onNodeWithContentDescription("Zrušiť").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun verifiedDownloadOffersInstallerWithRemote() {
        var installed = false
        composeRule.setContent {
            TellyoTheme {
                UpdatePrompt(UpdateUiState(update = update, phase = UpdatePhase.READY, apk = File("update.apk")),
                    {}, { installed = true }, {})
            }
        }
        composeRule.onNodeWithContentDescription("Nainštalovať").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertTrue(installed) }
    }
}
