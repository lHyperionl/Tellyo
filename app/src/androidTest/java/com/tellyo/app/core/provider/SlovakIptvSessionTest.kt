package com.tellyo.app.core.provider

import android.net.Uri
import androidx.media3.session.MediaController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tellyo.app.MainActivity
import com.tellyo.app.core.model.StreamSource
import com.tellyo.app.core.model.StreamType
import com.tellyo.app.core.network.NetworkMonitor
import com.tellyo.app.core.network.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import com.tellyo.app.core.player.AndroidPlaybackLogger
import com.tellyo.app.core.player.Media3PlaybackClient
import com.tellyo.app.core.player.PlaybackTelemetryStore
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SlovakIptvSessionTest {
    @Test
    fun mediaSessionCarriesVlcHeadersAndClearsThemWhenChangingChannels() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val requests = CopyOnWriteArrayList<Map<String, String>>()
        val server = ServerSocket(0)
        val serverThread = thread(isDaemon = true) {
            try {
                repeat(2) {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        reader.readLine()
                        val headers = linkedMapOf<String, String>()
                        while (true) {
                            val line = reader.readLine()?.takeIf { it.isNotEmpty() } ?: break
                            headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                        }
                        requests.add(headers)
                        // A local empty manifest is enough to inspect the actual service HTTP request.
                        val body = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-ENDLIST\n"
                        socket.getOutputStream().write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\n" +
                                "Content-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n" + body).toByteArray(),
                        )
                    }
                }
            } catch (_: java.io.IOException) {
                // Closing the test server unblocks accept during cleanup.
            }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var client: Media3PlaybackClient? = null
        try {
            instrumentation.runOnMainSync {
                client = Media3PlaybackClient(context, AndroidPlaybackLogger(), PlaybackTelemetryStore(),
                    object : NetworkMonitor {
                        override val status = MutableStateFlow(NetworkStatus.Available)
                    })
            }
            repeat(2) { index ->
                withContext(Dispatchers.Main) {
                    requireNotNull(client).play(StreamSource(
                        channelId = "session-test-$index",
                        uri = Uri.parse("http://127.0.0.1:${server.localPort}/channel-$index.m3u8"),
                        type = StreamType.HLS,
                        headers = if (index == 0) mapOf(
                            "User-Agent" to "Tellyo IPTV Header Test",
                            "Referer" to "https://example.com/test",
                        ) else emptyMap(),
                    ))
                }
                val deadline = System.currentTimeMillis() + 10_000
                while (requests.size <= index && System.currentTimeMillis() < deadline) Thread.sleep(50)
                assertTrue("MediaSession service did not request channel $index", requests.size > index)
            }
            assertEquals("Tellyo IPTV Header Test", requests[0]["user-agent"])
            assertEquals("https://example.com/test", requests[0]["referer"])
            assertFalse(requests[1]["user-agent"] == "Tellyo IPTV Header Test")
            assertFalse(requests[1].containsKey("referer"))
        } finally {
            instrumentation.runOnMainSync {
                client?.stop()
                (client?.player?.value as? MediaController)?.release()
            }
            server.close()
            serverThread.join(1_000)
            scenario.close()
        }
    }
}
