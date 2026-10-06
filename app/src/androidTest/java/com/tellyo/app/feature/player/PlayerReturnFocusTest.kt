package com.tellyo.app.feature.player

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.tellyo.app.MainActivity
import com.tellyo.app.feature.home.HomeAction
import com.tellyo.app.feature.home.HomeViewModel
import org.junit.Rule
import org.junit.Test

class PlayerReturnFocusTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun remoteBackReturnsToWatchedChannelForWebAndNativePlayers() {
        val model = ViewModelProvider(rule.activity)[HomeViewModel::class.java]
        rule.waitUntil(30_000) { !model.uiState.value.isLoading }
        val channels = model.uiState.value.channels
        val watched = listOf(channels.first { it.id == "markiza" }, channels.last())
        for (channel in watched) {
            rule.runOnIdle { model.onAction(HomeAction.PlayChannel(channel.id)) }
            rule.waitForIdle()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            rule.waitForIdle()
            rule.onNode(hasContentDescription("${channel.name},", substring = true))
                .assertIsDisplayed().assertIsFocused()
        }
    }
}
