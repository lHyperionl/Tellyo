package com.tellyo.app.feature.home

import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.settings.FavoriteSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesOrderingTest {
    @Test
    fun homeKeepsEveryChannelAndPlacesFavoritesFirst() {
        val channels = listOf(
            channel("one"),
            channel("two"),
            channel("three"),
            channel("four"),
        )

        val ordered = channels.orderedForHome(
            FavoriteSettings(
                configured = true,
                channelIds = setOf("three", "one"),
                orderedChannelIds = listOf("three", "one"),
            ),
        )

        assertEquals(listOf("three", "one", "two", "four"), ordered.map(Channel::id))
    }

    private fun channel(id: String) = Channel(
        id = id,
        number = 1,
        name = id,
        shortName = id,
        logoUrl = null,
        providerId = "test",
        category = ChannelCategory.GENERAL,
        enabled = true,
    )
}
