package com.tellyo.app.feature.player

import android.view.KeyEvent
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.ui.theme.TellyoTheme
import java.time.Instant
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuickChannelSwitcherTest {
    @get:Rule val rule = createComposeRule()
    private var handler: ((KeyEvent) -> Boolean)? = null
    private val switched = mutableListOf<String>()
    private var backs = 0
    private var playerDisposals = 0
    private val now = Instant.parse("2026-10-12T18:00:00Z")
    private val channels = listOf(
        channel("stvr-jednotka", 1, "Jednotka"),
        channel("stvr-dvojka", 2, "Dvojka"),
        channel("disabled", 3, "Disabled", enabled = false),
        channel("stvr-sport", 4, "Šport"),
    )

    private fun channel(id: String, number: Int, name: String, enabled: Boolean = true) =
        Channel(id, number, name, name, null, "fixture", ChannelCategory.GENERAL, enabled)

    private fun show(lineup: List<Channel> = channels, allowDirectionalSwitching: Boolean = true) {
        rule.setContent { TellyoTheme {
            QuickChannelSwitcher(lineup, "stvr-jednotka", mapOf("stvr-dvojka" to listOf(
                Program("program", "stvr-dvojka", "Večerný program", null,
                    now.minusSeconds(1800), now.plusSeconds(1800), null))), now,
                onSwitchChannel = { switched += it }, onBack = { backs++ },
                registerKeyHandler = { handler = it },
                allowDirectionalSwitching = allowDirectionalSwitching) {
                val focus = remember { FocusRequester() }
                DisposableEffect(Unit) { onDispose { playerDisposals++ } }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Box(Modifier.fillMaxSize().testTag("stream").focusRequester(focus).focusable()) {
                    Text("Stream remains mounted")
                }
            }
        } }
        rule.waitForIdle()
    }

    private fun press(key: Int): Boolean {
        var consumed = false
        rule.runOnIdle { consumed = requireNotNull(handler)(KeyEvent(KeyEvent.ACTION_DOWN, key)) }
        rule.waitForIdle()
        rule.runOnIdle { consumed = requireNotNull(handler)(KeyEvent(KeyEvent.ACTION_UP, key)) && consumed }
        rule.waitForIdle()
        return consumed
    }

    @Test fun arrowsBrowseWithoutSwitchingOrDisposingPlaybackAndOkCommits() {
        show()
        assertTrue(press(KeyEvent.KEYCODE_DPAD_DOWN))
        rule.onNodeWithText("Prepnúť kanál").assertDoesNotExist()
        rule.onNodeWithText("Vysielanie pokračuje počas výberu").assertDoesNotExist()
        rule.onNode(hasText("Dvojka") and isSelected()).assertExists()
        rule.onNodeWithText("Večerný program").assertExists()
        rule.onNodeWithTag("stream").assertIsFocused()
        assertTrue(switched.isEmpty())
        assertEquals(0, playerDisposals)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "quick-channel-switcher.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue(press(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(listOf("stvr-dvojka"), switched)
        rule.onNodeWithTag("quick-channel-switcher").assertDoesNotExist()
    }

    @Test fun arrowsWrapAndSkipDisabledChannels() {
        show()
        press(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNode(hasText("Šport") and isSelected()).assertExists()
        press(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNode(hasText("Dvojka") and isSelected()).assertExists()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNode(hasText("Jednotka") and isSelected()).assertExists()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(switched.isEmpty()) // OK on the playing channel does not reload it.
    }

    @Test fun backClosesBrowserAndReturnsKeysToPlayer() {
        show()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(press(KeyEvent.KEYCODE_BACK))
        assertEquals(0, backs)
        assertTrue(switched.isEmpty())
        rule.onNodeWithTag("stream").assertIsFocused()
        assertFalse(press(KeyEvent.KEYCODE_BACK))
        assertFalse(press(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test fun idleTimeoutResetsOnInputEvenWhenTheHighlightedChannelDoesNotChange() {
        show()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitForIdle()
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitForIdle()
        // The original 10-second deadline has passed, but the new one has not.
        rule.onNodeWithTag("quick-channel-switcher").assertIsDisplayed()
        rule.onNode(hasText("Dvojka") and isSelected()).assertExists()
        rule.mainClock.advanceTimeBy(4_200)
        rule.waitForIdle()
        rule.onNodeWithTag("quick-channel-switcher").assertDoesNotExist()
        assertTrue(switched.isEmpty())
        assertEquals(0, backs)
        assertEquals(0, playerDisposals)
        rule.onNodeWithTag("stream").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithTag("quick-channel-switcher").assertIsDisplayed()
        rule.onNode(hasText("Šport") and isSelected()).assertExists()
    }

    @Test fun emptyLineupLeavesPlayerKeysAlone() {
        show(emptyList())
        assertFalse(press(KeyEvent.KEYCODE_DPAD_DOWN))
        rule.onNodeWithTag("quick-channel-switcher").assertDoesNotExist()
    }

    @Test fun broadcasterArrowsRemainAvailableUntilAChannelKeyOpensTheRail() {
        show(allowDirectionalSwitching = false)
        assertFalse(press(KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(press(KeyEvent.KEYCODE_DPAD_UP))
        assertFalse(press(KeyEvent.KEYCODE_DPAD_CENTER))
        rule.onNodeWithTag("quick-channel-switcher").assertDoesNotExist()
        assertTrue(switched.isEmpty())

        assertTrue(press(KeyEvent.KEYCODE_CHANNEL_UP))
        rule.onNode(hasText("Dvojka") and isSelected()).assertExists()
        assertTrue(press(KeyEvent.KEYCODE_DPAD_DOWN))
        rule.onNode(hasText("Šport") and isSelected()).assertExists()
        assertTrue(press(KeyEvent.KEYCODE_BACK))
        assertFalse(press(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(0, playerDisposals)

        assertTrue(press(KeyEvent.KEYCODE_CHANNEL_DOWN))
        rule.onNode(hasText("Šport") and isSelected()).assertExists()
        assertTrue(press(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(listOf("stvr-sport"), switched)
    }

    @Test fun browserOverlaysOfficialWebViewAndBackKeepsItMounted() {
        rule.setContent { TellyoTheme {
            QuickChannelSwitcher(channels, "stvr-jednotka", emptyMap(), now,
                { switched += it }, { backs++ }, { handler = it },
                allowDirectionalSwitching = false) { onPlayerBack ->
                DisposableEffect(Unit) { onDispose { playerDisposals++ } }
                OfficialEmbedPlayerScreen(url = "about:blank", onBack = onPlayerBack)
            }
        } }
        assertFalse(press(KeyEvent.KEYCODE_DPAD_DOWN))
        press(KeyEvent.KEYCODE_CHANNEL_DOWN)
        rule.onNodeWithTag("quick-channel-switcher").assertIsDisplayed()
        press(KeyEvent.KEYCODE_BACK)
        rule.onNodeWithTag("quick-channel-switcher").assertDoesNotExist()
        assertTrue(switched.isEmpty())
        assertEquals(0, backs)
        assertEquals(0, playerDisposals)
    }

    @Test fun removingBrowsedChannelDismissesSafely() {
        val lineup = mutableStateOf(channels)
        rule.setContent { TellyoTheme {
            QuickChannelSwitcher(lineup.value, "stvr-jednotka", emptyMap(), now,
                { switched += it }, {}, { handler = it }) { Text("Stream") }
        } }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.runOnIdle { lineup.value = channels.filterNot { it.id == "stvr-dvojka" } }
        rule.waitForIdle()
        rule.onNodeWithTag("quick-channel-switcher").assertDoesNotExist()
        assertTrue(switched.isEmpty())
    }
}
