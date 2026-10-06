package com.tellyo.app.feature.home

import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.ChannelLineup
import com.tellyo.app.core.settings.FavoriteSettings
import com.tellyo.app.core.settings.withChildrenAfterSport2
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesOrderingTest {
    @Test
    fun existingSavedOrderMovesAllSixChildrenAfterSport2AndPreservesOtherFavorites() {
        val kids = ChannelLineup.CHILDREN_IDS
        val anchor = ChannelLineup.CHILDREN_ANCHOR_ID
        val ids = listOf("before", anchor) + kids + listOf("after")
        val saved = FavoriteSettings(true, setOf("before", anchor, "after", kids.last()),
            listOf("before", "after", anchor, kids.last()))
        val migrated = saved.withChildrenAfterSport2(ids)
        val ordered = ids.map(::channel).orderedForHome(migrated).map(Channel::id)
        assertEquals(listOf("before", "after", anchor) + kids, ordered)
        assertEquals(migrated, migrated.withChildrenAfterSport2(ids))
    }

    @Test
    fun childrenFollowNonFavoriteSport2WithoutChangingOtherFavoritePositions() {
        val kids = ChannelLineup.CHILDREN_IDS
        val anchor = ChannelLineup.CHILDREN_ANCHOR_ID
        val ids = listOf("before", anchor) + kids + listOf("after")
        val saved = FavoriteSettings(true, setOf("before", kids.first()), listOf(kids.first(), "before"))
        val migrated = saved.withChildrenAfterSport2(ids)
        assertEquals(listOf("before", anchor) + kids + listOf("after"),
            ids.map(::channel).orderedForHome(migrated).map(Channel::id))
    }

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
