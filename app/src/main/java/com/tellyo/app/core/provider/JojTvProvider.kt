package com.tellyo.app.core.provider

import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.StreamResolution
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Official JOJ channel catalogue and legal playback boundary.
 *
 * A broadcaster-approved HLS/DASH route supplied by the authorized metadata
 * service plays in Tellyo. Otherwise Tellyo hosts the broadcaster's live player,
 * including its login, subscriptions, advertising and current source lookup.
 */
@Singleton
class JojTvProvider @Inject constructor(
    private val channelCatalog: ChannelCatalogRepository,
    private val authorizedConfig: AuthorizedProviderConfigRepository = EmptyAuthorizedProviderConfigRepository,
) : TvProvider {
    override val id: String = PROVIDER_ID

    override suspend fun getChannels(): List<Channel> = channelCatalog.getChannels()
        .filter { it.providerId == id }
        .filter(Channel::enabled)

    override suspend fun resolveStream(channel: Channel): StreamResolution {
        if (channel.providerId != id || !channel.enabled) {
            return StreamResolution.Unavailable("Channel is not available in the JOJ provider")
        }

        authorizedConfig.getStream(channel.id)?.let { source ->
            return StreamResolution.Playable(source)
        }

        val destination = officialLiveUrl(channel)
            ?: return StreamResolution.Unavailable("JOJ has no official destination for ${channel.name}")
        return StreamResolution.OfficialEmbed(
            if (channel.id == NEWS_24_ID) NEWS_24_EMBED_URL else destination,
        )
    }

    companion object {
        const val PROVIDER_ID = "joj"

        const val JOJ_ID = "joj"
        const val PLUS_ID = "joj-plus"
        const val KRIMI_ID = "joj-krimi"
        const val NEWS_24_ID = "joj-24"

        /** Embed published by the official JOJ 24 homepage. */
        const val NEWS_24_EMBED_URL = "https://media.joj.sk/embed/03f227a4-ead6-4cca-af74-437c64462f27/080297fa-3ed8-41c2-a220-94166d540c9b?autoplay=1"

        private val officialLiveUrls = mapOf(
            JOJ_ID to "https://www.joj.sk/live/1-joj",
            PLUS_ID to "https://www.joj.sk/live/2-plus",
            KRIMI_ID to "https://www.joj.sk/live/3-jojkrimi",
            NEWS_24_ID to "https://joj24.noviny.sk/",
        )

        fun officialLiveUrl(channel: Channel): String? = officialLiveUrls[channel.id]
    }
}
