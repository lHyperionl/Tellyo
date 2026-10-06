package com.tellyo.app.core.provider

import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.StreamType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uPlaylistParserTest {
    @Test
    fun parsesHeadersWithoutLeakingThemAndKeepsStableIds() {
        val entries = M3uPlaylistParser.parse("""
            #EXTM3U
            #EXTINF:-1 tvg-id="Test.sk" tvg-name="Test, TV" group-title="Sports",Test TV (HD)
            #EXTVLCOPT:http-user-agent=TV Test Agent
            #EXTVLCOPT:http-referrer=https://example.com/live
            http://example.com/live.m3u8?token=temporary
            #EXTINF:-1 tvg-id="News.sk" group-title="News",News TV
            https://example.com/live.mpd
        """.trimIndent())
        assertEquals(2, entries.size)
        assertEquals("iptv-Test.sk", entries[0].channel.id)
        assertEquals("Test, TV", entries[0].channel.name)
        assertEquals(ChannelCategory.SPORTS, entries[0].channel.category)
        assertEquals("TV Test Agent", entries[0].headers["User-Agent"])
        assertEquals("https://example.com/live", entries[0].headers["Referer"])
        assertEquals(StreamType.HLS, entries[0].type)
        assertEquals(StreamType.DASH, entries[1].type)
        assertTrue(entries[1].headers.isEmpty())
        assertEquals(ChannelCategory.NEWS, entries[1].channel.category)
    }

    @Test
    fun skipsUnsupportedUrlsAndDeduplicatesChannels() {
        val entries = M3uPlaylistParser.parse("""
            #EXTM3U
            #EXTINF:-1 tvg-id="Bad.sk",Bad
            file:///local.m3u8
            #EXTINF:-1 tvg-id="Bad2.sk",Bad 2
            http://example.com/raw-ts
            #EXTINF:-1 tvg-id="Good.sk",Good
            https://example.com/good.m3u8
            #EXTINF:-1 tvg-id="Good.sk",Backup
            https://example.com/backup.m3u8
        """.trimIndent())
        assertEquals(1, entries.size)
        assertEquals("https://example.com/good.m3u8", entries.single().url)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHtmlErrorPagesInsteadOfReplacingTheCache() {
        M3uPlaylistParser.parse("<html>Service unavailable</html>")
    }

    @Test
    fun bundledPlaylistHasSupportedStreamsAndVlcHeaders() {
        val file = File("src/main/assets/slovak-iptv.m3u").let {
            if (it.exists()) it else File("app/src/main/assets/slovak-iptv.m3u")
        }
        val entries = M3uPlaylistParser.parse(file.readText())
        assertEquals(54, entries.size)
        assertEquals(entries.size, entries.map { it.channel.id }.distinct().size)
        assertFalse(entries.any { it.channel.id == "iptv-TVPoprad.sk" })
        assertTrue(entries.any { it.url.startsWith("http://") })
        assertTrue(entries.any { it.headers.containsKey("User-Agent") })
    }
}
