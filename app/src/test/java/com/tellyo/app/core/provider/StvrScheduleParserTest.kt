package com.tellyo.app.core.provider

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StvrScheduleParserTest {
    @Test
    fun parsesUpcomingProgrammesFromPublishedChannelSections() {
        val programmes = StvrScheduleParser().parse(
            html = """
                <main>
                  <div data-content-list="list" class="tv item tv--jednotka">
                    <div data-list-item class="event-present">
                      <div class="programm time--start">23:10</div>
                      <h5 class="title"><a>Sieň slávy</a></h5>
                      <span class="genre">zábava</span>
                      <p class="perex">Peter Nagy</p>
                      <picture><img src="https://example.invalid/sien.jpg" /></picture>
                    </div>
                    <div data-list-item>
                      <div class="programm time--start">00:40</div>
                      <h5 class="title"><a>Hymna SR</a></h5>
                    </div>
                  </div>
                  <div data-content-list="list" class="tv item tv--dvojka">
                    <div data-list-item>
                      <div class="programm time--start">23:35</div>
                      <h5 class="title"><a>Lada svetom</a></h5>
                    </div>
                  </div>
                </main>
            """.trimIndent(),
            date = LocalDate.of(2026, 8, 15),
        )

        assertEquals(3, programmes.size)
        assertEquals(StvrTvProvider.JEDNOTKA_ID, programmes[0].channelId)
        assertEquals("Sieň slávy", programmes[0].title)
        assertEquals("zábava", programmes[0].category)
        assertEquals("https://example.invalid/sien.jpg", programmes[0].artworkUrl)
        assertEquals(23, programmes[0].start.atZone(SOURCE_ZONE).hour)
        assertEquals(StvrTvProvider.DVOJKA_ID, programmes[2].channelId)
        assertEquals("Lada svetom", programmes[2].title)
        assertTrue(programmes[1].start.isAfter(programmes[0].start))
    }

    private companion object {
        val SOURCE_ZONE: ZoneId = ZoneId.of("Europe/Bratislava")
    }
}
