package com.tellyo.app.core.provider

import android.content.Intent
import android.content.ContentValues
import android.provider.MediaStore
import android.graphics.Bitmap
import android.view.KeyEvent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tellyo.app.MainActivity
import com.tellyo.app.core.player.PlaybackState
import com.tellyo.app.feature.home.HomeAction
import com.tellyo.app.feature.home.HomeViewModel
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Audits the actual HomeViewModel, Compose WebView and external-launch routes. */
@RunWith(AndroidJUnit4::class)
class BroadcasterPlaybackAuditTest {
    @Test
    fun auditCurrentJojAndMarkizaPlaybackRoutes() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveBroadcasters") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        val report = JSONArray()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var activity: MainActivity
        lateinit var model: HomeViewModel
        scenario.onActivity {
            activity = it
            model = ViewModelProvider(it)[HomeViewModel::class.java]
        }
        try {
            val startupDeadline = System.currentTimeMillis() + 45_000
            while (model.uiState.value.isLoading && System.currentTimeMillis() < startupDeadline) {
                Thread.sleep(100)
            }
            val selectedIds = InstrumentationRegistry.getArguments().getString("broadcasterIds")
                ?.split(',')?.toSet()
            val channels = model.uiState.value.channels.filter {
                if (selectedIds != null) it.id in selectedIds
                else it.providerId in setOf("joj", "markiza")
            }
                .sortedBy { if (it.providerId == "markiza") it.number else it.number + 100 }
            assertEquals("Broadcaster catalogue must load before the audit", selectedIds?.size ?: 13, channels.size)
            channels.forEach { channel ->
                val row = JSONObject().put("id", channel.id).put("name", channel.name)
                    .put("checked_at_ms", System.currentTimeMillis())
                instrumentation.runOnMainSync { model.onAction(HomeAction.PlayChannel(channel.id)) }
                val resolveDeadline = System.currentTimeMillis() + 15_000
                while (model.uiState.value.playbackState == PlaybackState.Resolving &&
                    System.currentTimeMillis() < resolveDeadline
                ) Thread.sleep(100)
                when (val state = model.uiState.value.playbackState) {
                    is PlaybackState.OfficialEmbed -> {
                        row.put("route", "official_embed").put("url", state.url)
                        probePage(client, state.url, row)
                        var observation = JSONObject()
                        val previousTimes = mutableMapOf<Int, Double>()
                        var progressingVideo = false
                        val started = System.currentTimeMillis()
                        var deadline = started + 60_000
                        var nextProgress = started + 30_000
                        var pressedPlay = false
                        var movedFocus = false
                        var touchedPlay = false
                        while (System.currentTimeMillis() < deadline) {
                            val webView = AtomicReference<WebView?>(null)
                            instrumentation.runOnMainSync {
                                webView.set(findWebView(activity.window.decorView))
                            }
                            webView.get()?.let { view ->
                                observation = inspectVideo(view)
                                val isAdvertisement = observation.optString("text").contains("Reklama", ignoreCase = true)
                                val videos = observation.optJSONArray("videos") ?: JSONArray()
                                for (index in 0 until videos.length()) {
                                    val video = videos.getJSONObject(index)
                                    val time = video.optDouble("time", 0.0)
                                    val frames = video.optInt("frames", 0)
                                    val oldTime = previousTimes[index]
                                    if (!isAdvertisement && oldTime != null && time - oldTime > 0.25 &&
                                        !video.optBoolean("paused", true) &&
                                        video.optInt("width", 0) > 0 && frames > 0
                                    ) progressingVideo = true
                                    previousTimes[index] = time
                                }
                            }
                            if (System.currentTimeMillis() >= nextProgress) {
                                android.util.Log.i("TellyoBroadcasterProgress", JSONObject()
                                    .put("id", channel.id)
                                    .put("elapsed_seconds", (System.currentTimeMillis() - started) / 1_000)
                                    .put("advertisement", observation.optString("text").contains("Reklama", ignoreCase = true))
                                    .put("videos", observation.optJSONArray("videos"))
                                    .toString())
                                nextProgress = System.currentTimeMillis() + 30_000
                            }
                            if (progressingVideo) break
                            // One normal TV-remote play press, without bypassing any page controls.
                            if (!pressedPlay && System.currentTimeMillis() > started + 15_000) {
                                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                                pressedPlay = true
                            }
                            if (!movedFocus && System.currentTimeMillis() > started + 30_000) {
                                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                                movedFocus = true
                            }
                            if (!touchedPlay && System.currentTimeMillis() > started + 45_000) {
                                // Diagnose a TV focus problem separately from a broken stream.
                                // This is a normal tap on the visible play control, with page rules intact.
                                val coordinates = FloatArray(2)
                                instrumentation.runOnMainSync {
                                    coordinates[0] = activity.window.decorView.width / 2f
                                    coordinates[1] = activity.window.decorView.height / 2f
                                }
                                val now = SystemClock.uptimeMillis()
                                MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                                    coordinates[0], coordinates[1], 0).also {
                                    instrumentation.uiAutomation.injectInputEvent(it, true)
                                    it.recycle()
                                }
                                MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP,
                                    coordinates[0], coordinates[1], 0).also {
                                    instrumentation.uiAutomation.injectInputEvent(it, true)
                                    it.recycle()
                                }
                                touchedPlay = true
                                // Preserve all pre-roll advertising and allow time for the live programme.
                                deadline = System.currentTimeMillis() + 180_000
                            }
                            Thread.sleep(1_000)
                        }
                        row.put("video_observation", observation)
                            .put("touch_fallback_used", touchedPlay)
                            .put("status", when {
                                progressingVideo && touchedPlay -> "video_playing_after_touch"
                                progressingVideo -> "video_playing"
                                else -> "no_verified_video"
                            })
                            .put("detail", when {
                                progressingVideo && touchedPlay -> "Live video decoded after a touch tap and pre-roll; remote play controls did not start it"
                                progressingVideo -> "Decoded frames and advancing HTML5 video time outside pre-roll"
                                else -> "No verified live programme after remote play attempts and up to 180 seconds following a touch tap"
                            })
                        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                            File(context.filesDir, "${channel.id}-audit.png").outputStream().use {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                            val imageUri = context.contentResolver.insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, "${channel.id}-audit.png")
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TellyoAudit")
                                },
                            )
                            imageUri?.let { uri ->
                                context.contentResolver.openOutputStream(uri)?.use {
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                }
                            }
                            bitmap.recycle()
                        }
                    }
                    is PlaybackState.External -> {
                        val url = state.deepLink?.toString().orEmpty()
                        row.put("route", "external").put("url", url)
                        probePage(client, url, row)
                        val intent = Intent(Intent.ACTION_VIEW, state.deepLink)
                        val handler = intent.resolveActivity(context.packageManager)
                        row.put("external_handler", handler?.flattenToShortString() ?: JSONObject.NULL)
                        // Exercise the production button with the TV remote as well as resolving its intent.
                        Thread.sleep(300)
                        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                        Thread.sleep(500)
                        val stub = handler?.packageName == "com.android.tv.frameworkpackagestubs"
                        row.put("status", if (handler == null || stub) "no_external_browser" else "external_playback_unverified")
                            .put("detail", when {
                                handler == null -> "No installed app handles the official HTTPS destination"
                                stub -> "Android TV image only supplies a BrowserStub; no real browser/player is installed"
                                else -> "External destination opened; video playback is outside Tellyo and remains unverified"
                            })
                    }
                    else -> row.put("route", "other").put("status", "unavailable")
                        .put("detail", state.toString())
                }
                report.put(row)
                android.util.Log.i("TellyoBroadcasterTest", row.toString())
                File(context.filesDir, "broadcaster-playback-report.json").writeText(report.toString(2))
                instrumentation.runOnMainSync { model.onAction(HomeAction.ClosePlayer) }
                Thread.sleep(300)
            }
            assertEquals(channels.size, report.length())
        } finally {
            scenario.close()
        }
    }

    private fun probePage(client: OkHttpClient, url: String, row: JSONObject) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use {
                row.put("page_http", it.code).put("page_final_url", it.request.url.toString())
            }
        } catch (error: Exception) {
            row.put("page_error", "${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun inspectVideo(view: WebView): JSONObject {
        val result = AtomicReference("{}")
        val ready = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.evaluateJavascript("""
                (function() {
                    var videos = [];
                    function inspect(doc) {
                        Array.from(doc.querySelectorAll('video')).forEach(function(v) {
                            var q = v.getVideoPlaybackQuality ? v.getVideoPlaybackQuality() : {};
                            videos.push({time:v.currentTime, paused:v.paused, ready:v.readyState,
                                width:v.videoWidth, height:v.videoHeight,
                                frames:q.totalVideoFrames || v.webkitDecodedFrameCount || 0,
                                error:v.error ? v.error.code : null});
                        });
                        Array.from(doc.querySelectorAll('iframe')).forEach(function(f) {
                            try { if (f.contentDocument) inspect(f.contentDocument); } catch(e) {}
                        });
                        Array.from(doc.querySelectorAll('*')).forEach(function(e) {
                            if (e.shadowRoot) inspect(e.shadowRoot);
                        });
                    }
                    inspect(document);
                    return {title:document.title, ready:document.readyState,
                        text:(document.body ? document.body.innerText : '').slice(0,1800),
                        iframe_count:document.querySelectorAll('iframe').length, videos:videos};
                })()
            """.trimIndent()) { value ->
                result.set(value ?: "{}")
                ready.countDown()
            }
        }
        ready.await(3, TimeUnit.SECONDS)
        return runCatching { JSONObject(result.get()) }.getOrDefault(JSONObject())
    }
}
