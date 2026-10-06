package com.tellyo.app.core.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderConfigCodecTest {
    @Test
    fun decodesAuthorizedHttpsStreamAndXmlTvFeed() {
        val document = ProviderConfigCodec.decode(
            """
            {
              "schemaVersion": 1,
              "streams": [
                {"channelId":"joj","uri":"https://media.example/joj.m3u8","type":"HLS"}
              ],
              "epg": {"uri":"https://metadata.example/guide.xml.gz","format":"XMLTV"}
            }
            """.trimIndent(),
        )

        assertEquals("joj", document.streams.single().channelId)
        assertEquals("https://metadata.example/guide.xml.gz", document.epg?.uri)
    }

    @Test
    fun rejectsInsecureOrUnknownProviderRoutes() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderConfigCodec.decode(
                """{"schemaVersion":1,"streams":[{"channelId":"joj","uri":"http://example/joj.m3u8","type":"HLS"}]}""",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderConfigCodec.decode(
                """{"schemaVersion":1,"streams":[{"channelId":"joj","uri":"https://example/joj.mp4","type":"MP4"}]}""",
            )
        }
    }
}
