package com.tellyo.app.core.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelCatalogCodecTest {
    @Test
    fun decodesAndOrdersBundledShape() {
        val channels = ChannelCatalogCodec.decode(
            """
            {
              "schemaVersion": 1,
              "channels": [
                {"id":"late","number":2,"name":"Late","shortName":"Late","provider":"x","category":"NEWS","enabled":true},
                {"id":"first","number":1,"name":"First","shortName":"First","provider":"x","category":"GENERAL","enabled":true}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("first", "late"), channels.map { it.id })
        assertEquals("x", channels.first().providerId)
        assertEquals("NEWS", channels.last().category.name)
    }

    @Test
    fun rejectsUnknownSchema() {
        val result = runCatching {
            ChannelCatalogCodec.decode("{\"schemaVersion\":99,\"channels\":[]}")
        }

        assertTrue(result.isFailure)
    }
}
