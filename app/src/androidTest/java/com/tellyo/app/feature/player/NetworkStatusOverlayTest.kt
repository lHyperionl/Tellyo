package com.tellyo.app.feature.player

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.tellyo.app.core.ui.components.NetworkStatusOverlay
import com.tellyo.app.core.ui.theme.TellyoTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NetworkStatusOverlayTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun offlineErrorAppearsOverContentAndDisappearsOnReconnect() {
        val online = mutableStateOf(true)
        rule.setContent {
            TellyoTheme {
                NetworkStatusOverlay(isInternetAvailable = online.value) {
                    Text("Player content")
                }
            }
        }
        rule.onNodeWithText("Internetové pripojenie je nedostupné.").assertDoesNotExist()
        rule.runOnIdle { online.value = false }
        rule.onNodeWithText("Internetové pripojenie je nedostupné.").assertIsDisplayed()
        rule.onNodeWithText("Čakám na obnovenie pripojenia…").assertIsDisplayed()
        rule.onNodeWithText("Player content").assertIsDisplayed()
        rule.runOnIdle { online.value = true }
        rule.onNodeWithText("Internetové pripojenie je nedostupné.").assertDoesNotExist()
        rule.onNodeWithText("Player content").assertIsDisplayed()
    }

    @Test fun reconnectReloadsBroadcasterPageEvenWhenUrlIsUnchanged() {
        val generation = mutableStateOf(0)
        rule.setContent {
            OfficialEmbedPlayerScreen(url = "about:blank", onBack = {}, reloadGeneration = generation.value)
        }
        lateinit var view: WebView
        rule.runOnIdle { view = requireNotNull(findWebView(rule.activity.window.decorView)) }
        rule.waitUntil(5_000) { script(view, "document.readyState") == "\"complete\"" }
        assertEquals("1", script(view, "window.__tellyoReloadMarker = 1"))
        rule.runOnIdle { generation.value++ }
        rule.waitUntil(5_000) { script(view, "typeof window.__tellyoReloadMarker") == "\"undefined\"" }
        rule.runOnIdle {
            assertSame(view, findWebView(rule.activity.window.decorView))
            assertEquals(Pair("about:blank", 1), view.tag)
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun script(view: WebView, code: String): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        rule.runOnUiThread { view.evaluateJavascript(code) { result = it; latch.countDown() } }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return result
    }
}
