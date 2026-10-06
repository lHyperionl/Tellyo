package com.tellyo.app.core.epg

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JojProgrammeApiParserTest {
    @Test
    fun mapsOfficialChannelKeysAndProgrammeMetadata() {
        val programs = JojProgrammeApiParser().parse(
            """
            {
              "1": [{
                "id": 100,
                "title": "Noviny TV JOJ",
                "description": "Najdôležitejšie správy dňa.",
                "short_description": "Správy",
                "start_at": "2026-08-16T19:30:00+02:00",
                "end_at": "2026-08-16T20:40:00+02:00",
                "image": "https://img.joj.sk/news"
              }],
              "2": [{
                "id": 101,
                "title": "Profesionáli",
                "description": null,
                "short_description": "Komediálny seriál",
                "start_at": "2026-08-16T20:00:00+02:00",
                "end_at": "2026-08-16T20:30:00+02:00",
                "image": "http://insecure.invalid/image"
              }]
            }
            """.trimIndent(),
        )

        assertEquals(2, programs.size)
        assertEquals("joj", programs[0].channelId)
        assertEquals("Noviny TV JOJ", programs[0].title)
        assertEquals(Instant.parse("2026-08-16T17:30:00Z"), programs[0].start)
        assertEquals("https://img.joj.sk/news", programs[0].artworkUrl)
        assertEquals("joj-plus", programs[1].channelId)
        assertEquals("Komediálny seriál", programs[1].description)
        assertNull(programs[1].artworkUrl)
    }

    @Test
    fun ignoresUnknownChannelsAndInvalidTimeRanges() {
        val programs = JojProgrammeApiParser().parse(
            """
            {
              "999": [{
                "id": 1,
                "title": "Unknown",
                "start_at": "2026-08-16T20:00:00+02:00",
                "end_at": "2026-08-16T21:00:00+02:00"
              }],
              "16": [{
                "id": 2,
                "title": "Invalid",
                "start_at": "2026-08-16T20:00:00+02:00",
                "end_at": "2026-08-16T20:00:00+02:00"
              }]
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<com.tellyo.app.core.model.Program>(), programs)
    }
}
