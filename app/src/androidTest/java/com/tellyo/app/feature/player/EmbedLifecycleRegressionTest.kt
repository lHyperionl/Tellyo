package com.tellyo.app.feature.player

import android.util.Base64
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises real HTML media without depending on a broadcaster or network. */
class EmbedLifecycleRegressionTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var webView: WebView

    @After fun destroyWebView() {
        if (::webView.isInitialized) onMain {
            webView.resumeTimers()
            webView.destroy()
        }
    }

    @Test fun backgroundPausesMediaAndResumePreservesManualPause() {
        val pcm = ByteBuffer.allocate(44 + 16_000).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + 16_000); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8_000); putInt(16_000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(16_000)
        }.array()
        val audio = Base64.encodeToString(pcm, Base64.NO_WRAP)
        rule.setContent {
            AndroidView(factory = { context -> WebView(context).also {
                webView = it
                it.settings.javaScriptEnabled = true
                it.settings.mediaPlaybackRequiresUserGesture = false
                it.loadDataWithBaseURL("https://fixture.invalid/", """
                    <audio id="playing" loop src="data:audio/wav;base64,$audio"></audio>
                    <audio id="manual" loop src="data:audio/wav;base64,$audio"></audio>
                    <script>document.getElementById('playing').play();</script>
                """.trimIndent(), "text/html", "UTF-8", null)
            } })
        }
        awaitScript("document.getElementById('playing') && !document.getElementById('playing').paused", "true")
        onMain { webView.setHostActive(false) }
        awaitScript("document.getElementById('playing').paused", "true")
        onMain { webView.setHostActive(true) }
        awaitScript("document.getElementById('playing').paused", "false")
        assertEquals("true", script("document.getElementById('manual').paused"))
    }

    private fun awaitScript(code: String, expected: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (script(code) == expected) return
            Thread.sleep(100)
        }
        assertEquals(expected, script(code))
    }

    private fun script(code: String): String? {
        val latch = CountDownLatch(1)
        var value: String? = null
        onMain { webView.evaluateJavascript(code) { value = it; latch.countDown() } }
        assertTrue("WebView must respond to JavaScript", latch.await(5, TimeUnit.SECONDS))
        return value
    }

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
