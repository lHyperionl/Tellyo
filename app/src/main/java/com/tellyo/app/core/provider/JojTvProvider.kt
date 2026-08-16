package com.tellyo.app.core.provider

import android.net.Uri
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.StreamResolution
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Official JOJ channel catalogue.
 *
 * JOJ does not expose a stable, provider-neutral public HLS contract here.
 * The provider therefore returns the official live destination and leaves
 * playback/authentication to JOJ's own service instead of guessing a stream
 * URL or bypassing access controls.
 */
@Singleton
class JojTvProvider @Inject constructor() : TvProvider {
    override val id: String = PROVIDER_ID

    override suspend fun getChannels(): List<Channel> = channels

    override suspend fun resolveStream(channel: Channel): StreamResolution {
        if (channel.providerId != id || !channel.enabled) {
            return StreamResolution.Unavailable("Channel is not available in the JOJ provider")
        }

        val destination = officialLiveUrl(channel)
            ?: return StreamResolution.Unavailable("JOJ has no official destination for ${channel.name}")
        return StreamResolution.ExternalApp(
            packageName = null,
            deepLink = Uri.parse(destination),
        )
    }

    companion object {
        const val PROVIDER_ID = "joj"

        const val JOJ_ID = "joj"
        const val PLUS_ID = "joj-plus"
        const val KRIMI_ID = "joj-krimi"
        const val NEWS_24_ID = "joj-24"
        const val SPORT_ID = "joj-sport"
        const val SPORT_2_ID = "joj-sport-2"
        const val JOJKO_ID = "jojko"
        const val SVET_ID = "joj-svet"

        private val officialLiveUrls = mapOf(
            JOJ_ID to "https://www.joj.sk/live/1-joj",
            PLUS_ID to "https://www.joj.sk/live/1-jojplus",
            // The official page retains the WAU path after the channel's rebrand.
            KRIMI_ID to "https://www.joj.sk/live/3-wau.",
            NEWS_24_ID to "https://joj24.noviny.sk/",
            SPORT_ID to "https://jojsport.joj.sk/live/15-jojsport",
            SPORT_2_ID to "https://jojsport.joj.sk/live",
            JOJKO_ID to "https://play.joj.sk/",
            SVET_ID to "https://play.joj.sk/",
        )

        fun officialLiveUrl(channel: Channel): String? = officialLiveUrls[channel.id]

        val channels: List<Channel> = listOf(
            Channel(
                id = JOJ_ID,
                number = 5,
                name = "JOJ",
                shortName = "JOJ",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.GENERAL,
                enabled = true,
            ),
            Channel(
                id = PLUS_ID,
                number = 6,
                name = "JOJ Plus",
                shortName = "JOJ Plus",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.GENERAL,
                enabled = true,
            ),
            Channel(
                id = KRIMI_ID,
                number = 7,
                name = "JOJ Krimi (WAU)",
                shortName = "JOJ Krimi",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.GENERAL,
                enabled = true,
            ),
            Channel(
                id = NEWS_24_ID,
                number = 8,
                name = "JOJ 24",
                shortName = "JOJ 24",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.NEWS,
                enabled = true,
            ),
            Channel(
                id = SPORT_ID,
                number = 9,
                name = "JOJ Šport",
                shortName = "JOJ Šport",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.SPORTS,
                enabled = true,
            ),
            Channel(
                id = SPORT_2_ID,
                number = 10,
                name = "JOJ Šport 2",
                shortName = "JOJ Šport 2",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.SPORTS,
                enabled = true,
            ),
            Channel(
                id = JOJKO_ID,
                number = 11,
                name = "JOJko",
                shortName = "JOJko",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.GENERAL,
                enabled = true,
            ),
            Channel(
                id = SVET_ID,
                number = 12,
                name = "JOJ Svet",
                shortName = "JOJ Svet",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.GENERAL,
                enabled = true,
            ),
        )
    }
}
