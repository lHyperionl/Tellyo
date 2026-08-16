package com.tellyo.app.core.epg

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlTvParserTest {
    @Test
    fun parsesXmlTvProgrammeIntoDomainModel() {
        val programs = XmlTvParser().parse(
            """
            <tv>
              <programme channel="stvr-jednotka" start="20260814180000 +0000" stop="20260814210000 +0000">
                <icon src="https://example.invalid/program.png"/>
                <title>Večerné správy</title>
                <desc>Najdôležitejšie udalosti dňa.</desc>
                <category>news</category>
              </programme>
            </tv>
            """.trimIndent(),
        )

        assertEquals(1, programs.size)
        assertEquals("stvr-jednotka", programs.single().channelId)
        assertEquals("Večerné správy", programs.single().title)
        assertEquals("Najdôležitejšie udalosti dňa.", programs.single().description)
        assertEquals("news", programs.single().category)
        assertEquals("https://example.invalid/program.png", programs.single().artworkUrl)
        assertEquals(Instant.parse("2026-08-14T18:00:00Z"), programs.single().start)
        assertTrue(programs.single().end.isAfter(programs.single().start))
    }

    @Test
    fun ignoresProgrammeWithoutValidTimeRange() {
        val programs = XmlTvParser().parse(
            """
            <tv>
              <programme channel="stvr-jednotka" start="bad" stop="bad"><title>Invalid</title></programme>
              <programme channel="stvr-jednotka" start="20260814210000 +0000" stop="20260814180000 +0000"><title>Backwards</title></programme>
            </tv>
            """.trimIndent(),
        )

        assertTrue(programs.isEmpty())
    }
}
