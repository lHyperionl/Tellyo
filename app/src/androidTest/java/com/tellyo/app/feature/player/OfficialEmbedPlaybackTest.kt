package com.tellyo.app.feature.player

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.webkit.WebView
import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tellyo.app.MainActivity
import com.tellyo.app.core.player.PlaybackState
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.tellyo.app.core.provider.MarkizaTvProvider
import com.tellyo.app.core.provider.JojTvProvider
import com.tellyo.app.feature.home.HomeAction
import com.tellyo.app.feature.home.HomeViewModel
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in real-player regression: no click, remote confirmation or touch starts playback. */
@RunWith(AndroidJUnit4::class)
class OfficialEmbedPlaybackTest {
    @Test
    fun filteredPrerollStillReachesLiveProgramme() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("filteredPreroll") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Exercise the production embed independently of catalogue refresh and
        // Android's validated-network gate; only page/video loading is under test.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            val selectedUrl = mutableStateOf<String?>(null)
            scenario.onActivity {
                activity = it
                it.setContent {
                    selectedUrl.value?.let { url ->
                        OfficialEmbedPlayerScreen(url, onBack = { selectedUrl.value = null }, blockAds = true)
                    }
                }
            }
            listOf(
                "markiza" to MarkizaTvProvider.MARKIZA_EMBED_URL,
                "markiza-doma" to MarkizaTvProvider.DOMA_EMBED_URL,
                "joj-24" to JojTvProvider.NEWS_24_EMBED_URL,
            ).forEach { (channel, url) ->
                instrumentation.runOnMainSync { selectedUrl.value = url }
                val start = System.currentTimeMillis()
                val observation = waitForVideo(activity, requireLive = true, timeoutMs = 45_000)
                android.util.Log.i("TellyoAdFilter", "$channel live after ${System.currentTimeMillis() - start}ms: $observation")
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                assertTrue(waitUntil(5_000) {
                    val view = AtomicReference<WebView?>()
                    instrumentation.runOnMainSync { view.set(findWebView(activity.window.decorView)) }
                    view.get() == null
                })
            }
        }
    }

    @Test
    fun jojAccountGatesAndBackStayInsideTellyo() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("jojAccountGates") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val report = JSONObject()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            lateinit var model: HomeViewModel
            scenario.onActivity {
                activity = it
                model = ViewModelProvider(it)[HomeViewModel::class.java]
            }
            assertTrue(waitUntil(45_000) { !model.uiState.value.isLoading })
            listOf("joj", "joj-plus").forEach { channel ->
                instrumentation.runOnMainSync { model.onAction(HomeAction.PlayChannel(channel)) }
                assertTrue(waitUntil(15_000) { model.uiState.value.playbackState is PlaybackState.OfficialEmbed })
                handleTa3Consent(activity)
                assertTrue("$channel must show the broadcaster's account gate", waitUntil(30_000) {
                    currentVideo(activity).optString("text").contains("PRIHLÁSIŤ SA")
                })
                var loginFocused = false
                repeat(16) {
                    if (!loginFocused) {
                        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                        Thread.sleep(250)
                        loginFocused = currentVideo(activity).optString("focusedText").trim() == "PRIHLÁSIŤ SA"
                    }
                }
                assertTrue("$channel sign-in button must be reachable with the remote", loginFocused)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                assertTrue("$channel must present JOJ's login form, without launching a browser: ${currentVideo(activity)}",
                    waitUntil(45_000) { currentVideo(activity).optBoolean("loginVisible") })
                val observation = currentVideo(activity)
                report.put(channel, observation.put("result", "broadcaster_login_required"))
                // The viewer can navigate to the real account form with a TV remote.
                var emailFocused = observation.optString("focusedInputType") == "email"
                repeat(12) {
                    if (!emailFocused) {
                        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                        Thread.sleep(250)
                        emailFocused = currentVideo(activity).optString("focusedInputType") == "email"
                    }
                }
                assertTrue("$channel email field must be reachable with the remote", emailFocused)
                assertTrue("$channel account form must open the TV keyboard",
                    waitUntil(5_000) { currentVideo(activity).optBoolean("keyboardVisible") })
                report.put("${channel}_keyboard", "email_focus_opened_tv_keyboard")
                saveReport(report)
                // Android first dismisses the account editor's keyboard.
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                assertTrue(waitUntil(5_000) { !currentVideo(activity).optBoolean("keyboardVisible") })
                assertTrue("Keyboard Back must leave the account form open", model.uiState.value.playerVisible)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                assertTrue(waitUntil(5_000) {
                    val view = AtomicReference<WebView?>()
                    instrumentation.runOnMainSync { view.set(findWebView(activity.window.decorView)) }
                    !model.uiState.value.playerVisible && view.get() == null
                })
                instrumentation.runOnMainSync {
                    assertTrue(findWebView(activity.window.decorView) == null)
                    assertFalse(activity.isFinishing)
                }
                report.put("${channel}_back", "returned_home_and_disposed_webview")
                saveReport(report)
            }
        }
    }

    @Test
    fun automaticPlaybackPreservesAdsAndBackReturnsHome() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveBroadcasters") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val report = JSONObject()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            lateinit var model: HomeViewModel
            scenario.onActivity {
                activity = it
                model = ViewModelProvider(it)[HomeViewModel::class.java]
            }
            waitUntil(45_000) { !model.uiState.value.isLoading }
            assertTrue(model.uiState.value.channels.any { it.id == "markiza" })

            fun open(channelId: String) {
                instrumentation.runOnMainSync { model.onAction(HomeAction.PlayChannel(channelId)) }
                waitUntil(15_000) { model.uiState.value.playbackState is PlaybackState.OfficialEmbed }
                assertTrue(model.uiState.value.playbackState is PlaybackState.OfficialEmbed)
                if (channelId.startsWith("markiza")) {
                    assertTrue((model.uiState.value.playbackState as PlaybackState.OfficialEmbed).url.endsWith("?autoplay=1"))
                }
            }

            fun closeWith(key: Int) {
                instrumentation.sendKeyDownUpSync(key)
                waitUntil(5_000) {
                    val view = AtomicReference<WebView?>()
                    instrumentation.runOnMainSync { view.set(findWebView(activity.window.decorView)) }
                    !model.uiState.value.playerVisible && view.get() == null
                }
                assertFalse("Back must close the embed", model.uiState.value.playerVisible)
                instrumentation.runOnMainSync {
                    assertTrue("WebView must be removed so audio stops", findWebView(activity.window.decorView) == null)
                    assertFalse("Back must leave Tellyo running", activity.isFinishing)
                }
                assertTrue("Native playback must also stop", activity.playbackClient.state.value is PlaybackState.Idle)
            }

            val selectedIds = InstrumentationRegistry.getArguments().getString("embedIds")?.split(',')
            if (selectedIds != null) {
                selectedIds.forEach { channel ->
                    open(channel)
                    if (channel == "ta3" || channel.startsWith("joj")) {
                        report.put("${channel}_consent", handleTa3Consent(activity))
                    }
                    val live = waitForVideo(activity, requireLive = true, timeoutMs = 240_000)
                    report.put(channel, live.put("channel", channel))
                    instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
                        File(instrumentation.targetContext.filesDir, "$channel-live.png").outputStream().use {
                            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        screenshot.recycle()
                    }
                    saveReport(report)
                    if (channel == "ta3" || channel.startsWith("joj")) {
                        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                        assertTrue("$channel must pause with OK", waitUntil(5_000) { currentVideo(activity).optBoolean("paused", false) })
                        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                        report.put("${channel}_remote_resume", waitForVideo(activity, requireLive = true, timeoutMs = 15_000))
                        saveReport(report)
                    }
                    closeWith(KeyEvent.KEYCODE_BACK)
                    report.put("${channel}_back", "returned_home_and_disposed_webview")
                    saveReport(report)
                    if (channel == "ta3") {
                        open(channel)
                        report.put("ta3_reopen", waitForVideo(activity, requireLive = true, timeoutMs = 120_000))
                        closeWith(KeyEvent.KEYCODE_BACK)
                        report.put("ta3_reopen_back", "returned_home_and_disposed_webview")
                        saveReport(report)
                    }
                }
                return@use
            }

            // Check both embedded channels and emulator keyboard variants while pre-roll plays.
            listOf("markiza" to KeyEvent.KEYCODE_BACK, "markiza-doma" to KeyEvent.KEYCODE_ESCAPE,
                "markiza" to KeyEvent.KEYCODE_DEL).forEachIndexed { index, (channel, key) ->
                open(channel)
                val observation = waitForVideo(activity, requireLive = false, timeoutMs = 60_000)
                report.put("autoplay_$index", observation.put("channel", channel).put("back_key", key))
                closeWith(key)
                report.put("back_$index", "returned_home_and_disposed_webview")
                saveReport(report)
            }

            // Preserve the complete ad break and verify the live programme starts without input.
            open("markiza")
            val live = waitForVideo(activity, requireLive = true, timeoutMs = 240_000)
            report.put("markiza_live_without_input", live)
            closeWith(KeyEvent.KEYCODE_BACK)
            report.put("back_during_live", "returned_home_and_disposed_webview")
            saveReport(report)
        }
    }

    private fun handleTa3Consent(activity: MainActivity): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun observation() = currentVideo(activity)
        waitUntil(30_000) {
            val current = observation()
            current.optBoolean("consentVisible", false) ||
                (current.optString("url").contains("joj.sk") &&
                    (current.optString("text").contains("PRIHLÁSIŤ SA") || current.optInt("width") > 0))
        }
        if (!observation().optBoolean("consentVisible", false)) return "not_shown"
        assertTrue("Initial TV focus must select the necessary-cookies option",
            waitUntil(5_000) { observation().optString("consentFocus") == "didomi-notice-disagree-button" })
        // Let the broadcaster's initial notice render complete before sending a key.
        Thread.sleep(1_000)
        // Exercise all three actual broadcaster choices using only the remote.
        listOf("didomi-notice-learn-more-button", "didomi-notice-agree-button",
            "didomi-notice-disagree-button").forEach { expected ->
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            val focused = waitUntil(5_000) { observation().optString("consentFocus") == expected }
            assertTrue("Consent choice must be reachable with the TV remote: $expected; ${observation()}", focused)
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue("A normal remote confirmation must dismiss the broadcaster's notice",
            waitUntil(5_000) { !observation().optBoolean("consentVisible", true) })
        return "necessary_cookies_selected_with_remote"
    }

    private fun currentVideo(activity: MainActivity): JSONObject {
        val view = AtomicReference<WebView?>()
        val keyboardVisible = AtomicReference(false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.set(findWebView(activity.window.decorView))
            keyboardVisible.set(ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
        }
        return (view.get()?.let(::inspectVideo) ?: JSONObject()).put("keyboardVisible", keyboardVisible.get())
    }

    private fun waitForVideo(activity: MainActivity, requireLive: Boolean, timeoutMs: Long): JSONObject {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var previous = JSONObject()
        var latest = JSONObject()
        var nextLog = System.currentTimeMillis() + 15_000
        val succeeded = waitUntil(timeoutMs) {
            val view = AtomicReference<WebView?>()
            instrumentation.runOnMainSync { view.set(findWebView(activity.window.decorView)) }
            latest = view.get()?.let(::inspectVideo) ?: JSONObject()
            val progressing = latest.optDouble("time", 0.0) - previous.optDouble("time", 0.0) > 0.25 &&
                latest.optInt("frames", 0) > previous.optInt("frames", 0) &&
                latest.optInt("width", 0) > 0 && !latest.optBoolean("paused", true) &&
                !latest.optBoolean("muted", true) && latest.optDouble("volume", 0.0) > 0 &&
                latest.optLong("audioDecodedBytes", 0) > previous.optLong("audioDecodedBytes", 0)
            previous = latest
            if (System.currentTimeMillis() >= nextLog) {
                android.util.Log.i("TellyoEmbedAutoplay", latest.toString())
                nextLog = System.currentTimeMillis() + 15_000
            }
            progressing && (!requireLive || latest.optBoolean("programme", false))
        }
        assertTrue("Expected audible ${if (requireLive) "live programme" else "video"} without input: $latest", succeeded)
        assertTrue("Player must be visible above page overlays: $latest", latest.optBoolean("surfaceVisible", false))
        val layout = latest.getJSONObject("layout")
        assertTrue("Video must fit without cropping: $layout", layout.getString("fit") == "contain")
        assertTrue("Video must be centered: $layout", layout.getString("position") == "50% 50%")
        assertTrue("Video center must match viewport: $layout",
            kotlin.math.abs(layout.getDouble("centerX") - layout.getDouble("viewportWidth") / 2) < 1 &&
                kotlin.math.abs(layout.getDouble("centerY") - layout.getDouble("viewportHeight") / 2) < 1)
        assertTrue("Page must not zoom or scroll: $layout",
            kotlin.math.abs(layout.getDouble("scale") - 1) < 0.01 &&
                layout.getDouble("scrollX") == 0.0 && layout.getDouble("scrollY") == 0.0)
        instrumentation.runOnMainSync {
            val settings = requireNotNull(findWebView(activity.window.decorView)).settings
            assertFalse("TV video must not allow pinch zoom", settings.supportZoom())
        }
        android.util.Log.i("TellyoEmbedAutoplay", latest.toString())
        return latest
    }

    private fun inspectVideo(view: WebView): JSONObject {
        val result = AtomicReference("{}")
        val ready = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.evaluateJavascript("""
                (function() {
                    var v = Array.from(document.querySelectorAll('video')).find(function(v) {
                        return !v.paused && v.videoWidth > 0;
                    }) || document.querySelector('video');
                    var q = v && v.getVideoPlaybackQuality ? v.getVideoPlaybackQuality() : {};
                    var r = v ? v.getBoundingClientRect() : {left:0,top:0,width:0,height:0};
                    var s = v ? getComputedStyle(v) : {};
                    var root = document.querySelector(location.host === 'www.ta3.com' ? '.live_embed' :
                        location.host === 'www.joj.sk' ? '.joj-sticky-player-wrapper' :
                        location.host === 'media.joj.sk' ? '#player-1' : '#ef-player');
                    var top = document.elementFromPoint(innerWidth / 2, innerHeight / 2);
                    var notice = document.querySelector('.didomi-popup-notice');
                    var password = Array.from(document.querySelectorAll('input[type=password]')).find(function(e) {
                        return e.getBoundingClientRect().width > 0 && getComputedStyle(e).visibility !== 'hidden';
                    });
                    var text = (root ? root.innerText : (document.body ? document.body.innerText : '')).slice(0,1800);
                    var programme = text.includes('LIVE') && !text.toLowerCase().includes('reklama');
                    if (location.host === 'www.ta3.com') {
                        var p = window.LBXPlayer && LBXPlayer.KalturaPlayer.getPlayer('player-liveplayer');
                        programme = !!(p && p.isLive() && p.ads && !p.ads.isAdBreak());
                    }
                    if (location.host === 'www.joj.sk') {
                        programme = !!(v && v.id === 'tivio-video-player' && text.includes('NAŽIVO'));
                    }
                    if (location.host === 'media.joj.sk') {
                        var p = window.Rmp && Rmp.player;
                        if (p && p.getStreamMode) p.getStreamMode().then(function(mode) {
                            window.__tellyoObservedStreamMode = mode;
                        });
                        var mode = p && (p.streamingMode || window.__tellyoObservedStreamMode);
                        programme = !!(p && ['live','livedvr'].includes(mode) && !p.adOnStage);
                    }
                    return {time:v ? v.currentTime : 0, paused:v ? v.paused : true,
                        muted:v ? v.muted : true, volume:v ? v.volume : 0,
                        audioDecodedBytes:v ? v.webkitAudioDecodedByteCount : 0,
                        frames:q.totalVideoFrames || (v && v.webkitDecodedFrameCount) || 0,
                        width:v ? v.videoWidth : 0,
                        layout:{fit:s.objectFit,position:s.objectPosition,
                            centerX:r.left+r.width/2,centerY:r.top+r.height/2,
                            viewportWidth:innerWidth,viewportHeight:innerHeight,
                            scale:visualViewport.scale,scrollX:scrollX,scrollY:scrollY},
                        title:document.title,url:location.href,programme:programme,
                        consentVisible:!!(notice && notice.getBoundingClientRect().width > 0),
                        consentFocus:document.activeElement ? document.activeElement.id : '',
                        loginVisible:!!password,
                        focusedInputType:document.activeElement && document.activeElement.tagName === 'INPUT' ? document.activeElement.type : '',
                        focusedText:document.activeElement ? (document.activeElement.innerText || '') : '',
                        surfaceVisible:!!(root && root.contains(top)),text:text};
                })()
            """.trimIndent()) {
                result.set(it ?: "{}")
                ready.countDown()
            }
        }
        assertTrue("WebView observation timed out", ready.await(3, TimeUnit.SECONDS))
        return JSONObject(result.get())
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun waitUntil(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return true
            Thread.sleep(500)
        }
        return false
    }

    private fun saveReport(report: JSONObject) {
        android.util.Log.i("TellyoEmbedReport", report.toString())
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "embed-autoplay-report.json").writeText(report.toString(2))
    }
}
