package com.tellyo.app.core.provider

import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.StreamResolution
import com.tellyo.app.core.model.StreamType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Official STVR catalogue and stream-resolution boundary.
 */
@Singleton
class StvrTvProvider @Inject constructor(
    private val officialApi: StvrOfficialApi,
) : TvProvider {
    override val id: String = PROVIDER_ID

    override suspend fun getChannels(): List<Channel> = channels

    override suspend fun resolveStream(channel: Channel): StreamResolution {
        if (channel.providerId != id || !channel.enabled) {
            return StreamResolution.Unavailable("Channel is not available in the STVR provider")
        }

        val live = officialApi.fetchLive(channel)
        val source = live.sources.firstOrNull { it.type == StreamType.HLS }
            ?: live.sources.firstOrNull()
            ?: return StreamResolution.Unavailable("STVR has no playable source for ${channel.name}")
        return StreamResolution.Playable(source)
    }

    companion object {
        const val PROVIDER_ID = "stvr"

        const val JEDNOTKA_ID = "stvr-jednotka"
        const val DVOJKA_ID = "stvr-dvojka"
        const val NEWS_24_ID = "stvr-24"
        const val SPORT_ID = "stvr-sport"

        fun officialChannelId(channel: Channel): String? = when (channel.id) {
            JEDNOTKA_ID -> "1"
            DVOJKA_ID -> "2"
            NEWS_24_ID -> "3"
            SPORT_ID -> "4"
            else -> null
        }

        val channels: List<Channel> = listOf(
            Channel(
                id = JEDNOTKA_ID,
                number = 1,
                name = "Jednotka",
                shortName = "Jednotka",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.GENERAL,
                enabled = true,
            ),
            Channel(
                id = DVOJKA_ID,
                number = 2,
                name = "Dvojka",
                shortName = "Dvojka",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.CULTURE,
                enabled = true,
            ),
            Channel(
                id = NEWS_24_ID,
                number = 3,
                name = ":24",
                shortName = ":24",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.NEWS,
                enabled = true,
            ),
            Channel(
                id = SPORT_ID,
                number = 4,
                name = "Šport",
                shortName = "Šport",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.SPORTS,
                enabled = true,
            ),
        )
    }
}
