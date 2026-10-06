package com.tellyo.app.core.provider

import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory

object ChannelCatalogFixtures {
    fun stvr(): List<Channel> = listOf(
        Channel("stvr-jednotka", 1, "Jednotka", "Jednotka", null, "stvr", ChannelCategory.GENERAL, true),
        Channel("stvr-dvojka", 2, "Dvojka", "Dvojka", null, "stvr", ChannelCategory.CULTURE, true),
        Channel("stvr-24", 3, ":24", ":24", null, "stvr", ChannelCategory.NEWS, true),
        Channel("stvr-sport", 4, "Šport", "Šport", null, "stvr", ChannelCategory.SPORTS, true),
    )

    fun joj(): List<Channel> = listOf(
        Channel("joj", 5, "JOJ", "JOJ", null, "joj", ChannelCategory.GENERAL, true),
        Channel("joj-plus", 6, "JOJ Plus", "JOJ Plus", null, "joj", ChannelCategory.GENERAL, true),
        Channel("joj-krimi", 7, "JOJ Krimi (WAU)", "JOJ Krimi", null, "joj", ChannelCategory.GENERAL, true),
        Channel("joj-24", 8, "JOJ 24", "JOJ 24", null, "joj", ChannelCategory.NEWS, true),
    )

    fun markiza(): List<Channel> = listOf(
        Channel("markiza", 13, "Markíza", "Markíza", null, "markiza", ChannelCategory.GENERAL, true),
        Channel("markiza-doma", 14, "Doma", "Doma", null, "markiza", ChannelCategory.GENERAL, true),
        Channel("markiza-dajto", 15, "Dajto", "Dajto", null, "markiza", ChannelCategory.GENERAL, true),
        Channel("markiza-krimi", 16, "Markíza Krimi", "Krimi", null, "markiza", ChannelCategory.GENERAL, true),
        Channel("markiza-klasik", 17, "Markíza Klasik", "Klasik", null, "markiza", ChannelCategory.GENERAL, true),
    )

    fun ta3(): List<Channel> = listOf(
        Channel("ta3", 18, "TA3", "TA3", null, "ta3", ChannelCategory.NEWS, true),
    )
}
