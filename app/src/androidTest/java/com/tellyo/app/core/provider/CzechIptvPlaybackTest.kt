package com.tellyo.app.core.provider

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tellyo.app.core.model.StreamType
import com.tellyo.app.core.player.StreamMediaSourceFactory
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in live test: -Pandroid.testInstrumentationRunnerArguments.liveCzechIptv=true */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class CzechIptvPlaybackTest {
    @Test
    fun bundledProviderResolvesRequestedFeedsWithTheirHttpOptions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entries = context.assets.open(CachedCzechPlaylistRepository.BUNDLED_FILE)
            .bufferedReader().use { M3uPlaylistParser.parse(it.readText(), CzechIptvProvider.PROVIDER_ID) }
        val provider = CzechIptvProvider(object : CzechPlaylistRepository {
            override suspend fun getEntries() = entries
        }, object : SlovakPlaylistRepository {
            override suspend fun getEntries() = emptyList<M3uChannel>()
        })
        assertEquals(49, provider.getChannels().size)
        provider.getChannels().forEach { channel ->
            val resolution = provider.resolveStream(channel)
            assertTrue(resolution is com.tellyo.app.core.model.StreamResolution.Playable)
            val source = (resolution as com.tellyo.app.core.model.StreamResolution.Playable).source
            val entry = entries.single { CzechIptvProvider.appChannelId(it.channel.id) == channel.id }
            assertEquals(entry.url, source.uri.toString())
            assertEquals(entry.headers, source.headers)
        }
    }

    @Test
    fun liveCzechStreamsProduceDecodedVideoFrames() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveCzechIptv") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val entries = context.assets.open(CachedCzechPlaylistRepository.BUNDLED_FILE)
            .bufferedReader().use { M3uPlaylistParser.parse(it.readText(), CzechIptvProvider.PROVIDER_ID) }
        val provider = CzechIptvProvider(object : CzechPlaylistRepository {
            override suspend fun getEntries() = entries
        }, object : SlovakPlaylistRepository {
            override suspend fun getEntries() = emptyList<M3uChannel>()
        })
        val report = JSONArray()
        var played = 0
        provider.getChannels().forEach { channel ->
            val entry = entries.single { CzechIptvProvider.appChannelId(it.channel.id) == channel.id }
            val renderedFrame = AtomicBoolean(false)
            val failure = AtomicReference<String?>(null)
            val advancing = AtomicBoolean(false)
            var player: ExoPlayer? = null
            var texture: SurfaceTexture? = null
            var surface: Surface? = null
            val started = System.currentTimeMillis()
            try {
                instrumentation.runOnMainSync {
                    texture = SurfaceTexture(0).apply { setDefaultBufferSize(1920, 1080) }
                    surface = Surface(texture)
                    player = ExoPlayer.Builder(context)
                        .setMediaSourceFactory(StreamMediaSourceFactory(context))
                        .build().apply {
                            volume = 0f
                            setVideoSurface(surface)
                            addListener(object : Player.Listener {
                                override fun onRenderedFirstFrame() { renderedFrame.set(true) }
                                override fun onPlayerError(error: PlaybackException) {
                                    failure.set("${error.errorCodeName}: ${error.cause?.message ?: error.message}")
                                }
                            })
                            setMediaItem(MediaItem.Builder()
                                .setMediaId(channel.id)
                                .setUri(entry.url)
                                .setMimeType(if (entry.type == StreamType.HLS) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MPD)
                                .setRequestMetadata(StreamMediaSourceFactory.requestMetadata(entry.headers))
                                .build())
                            prepare()
                            play()
                        }
                }
                while (System.currentTimeMillis() - started < 15_000 && failure.get() == null) {
                    instrumentation.runOnMainSync {
                        advancing.set(player?.isPlaying == true && (player?.currentPosition ?: 0) > 0)
                    }
                    if (renderedFrame.get() && advancing.get()) break
                    Thread.sleep(100)
                }
                val success = renderedFrame.get() && advancing.get()
                if (success) played++
                val row = JSONObject()
                    .put("id", channel.id)
                    .put("name", channel.name)
                    .put("status", if (success) "played" else "failed")
                    .put("rendered_video_frame", renderedFrame.get())
                    .put("elapsed_ms", System.currentTimeMillis() - started)
                    .put("detail", failure.get() ?: if (success) "Decoded frame and advancing playback" else "No decoded frame within 15 seconds")
                report.put(row)
                android.util.Log.i("TellyoCzechIptvTest", row.toString())
            } finally {
                instrumentation.runOnMainSync {
                    player?.release()
                    surface?.release()
                    texture?.release()
                }
                File(context.filesDir, "czech-iptv-playback-report.json").writeText(report.toString(2))
            }
        }
        assertTrue("No stream produced decoded video; see czech-iptv-playback-report.json", played > 0)
    }
}
