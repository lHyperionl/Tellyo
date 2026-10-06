package com.tellyo.app.core.provider

import android.net.Uri
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.StreamResolution
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Official Markíza channel catalogue and legal playback boundary.
 *
 * A broadcaster-approved HLS/DASH route supplied by the authorized metadata
 * service plays in Tellyo. All five channels also have published embed players,
 * preserving the broadcaster's advertising, tracking, and access controls.
 */
@Singleton
class MarkizaTvProvider @Inject constructor(
    private val channelCatalog: ChannelCatalogRepository,
    private val authorizedConfig: AuthorizedProviderConfigRepository = EmptyAuthorizedProviderConfigRepository,
) : TvProvider {
    override val id: String = PROVIDER_ID

    override suspend fun getChannels(): List<Channel> = channelCatalog.getChannels()
        .filter { it.providerId == id }
        .filter(Channel::enabled)

    override suspend fun resolveStream(channel: Channel): StreamResolution {
        if (channel.providerId != id || !channel.enabled) {
            return StreamResolution.Unavailable("Channel is not available in the Markíza provider")
        }

        authorizedConfig.getStream(channel.id)?.let { source ->
            return StreamResolution.Playable(source)
        }

        officialEmbedUrl(channel)?.let { embedUrl ->
            return StreamResolution.OfficialEmbed(embedUrl)
        }

        val destination = officialLiveUrl(channel)
            ?: return StreamResolution.Unavailable("Markíza has no official destination for ${channel.name}")
        return StreamResolution.ExternalApp(
            packageName = null,
            deepLink = Uri.parse(destination),
        )
    }

    companion object {
        const val PROVIDER_ID = "markiza"

        const val MARKIZA_ID = "markiza"
        const val DOMA_ID = "markiza-doma"
        const val DAJTO_ID = "markiza-dajto"
        const val KRIMI_ID = "markiza-krimi"
        const val KLASIK_ID = "markiza-klasik"

        /** Published embeds with the broadcaster's autoplay option, including pre-roll ads. */
        const val MARKIZA_EMBED_URL = "https://media.cms.markiza.sk/embed/CQ8HW3n7gyI?autoplay=1"
        const val DOMA_EMBED_URL = "https://media.cms.markiza.sk/embed/3nUCb6yPRpm?autoplay=1"
        const val DAJTO_EMBED_URL = "https://media.cms.markiza.sk/embed/DXOmzXWnYIU?autoplay=1"
        const val KRIMI_EMBED_URL = "https://media.cms.markiza.sk/embed/I9DRrSWXiao?autoplay=1"
        const val KLASIK_EMBED_URL = "https://media.cms.markiza.sk/embed/AWoXanGioN3?autoplay=1"

        private val officialEmbedUrls = mapOf(
            MARKIZA_ID to MARKIZA_EMBED_URL,
            DOMA_ID to DOMA_EMBED_URL,
            DAJTO_ID to DAJTO_EMBED_URL,
            KRIMI_ID to KRIMI_EMBED_URL,
            KLASIK_ID to KLASIK_EMBED_URL,
        )

        private val officialLiveUrls = mapOf(
            MARKIZA_ID to "https://www.markiza.sk/live/1-markiza",
            DOMA_ID to "https://www.markiza.sk/live/3-doma",
            DAJTO_ID to "https://www.markiza.sk/live/2-dajto",
            KRIMI_ID to "https://www.markiza.sk/live/22-krimi",
            KLASIK_ID to "https://www.markiza.sk/live/44-klasik",
        )

        fun officialLiveUrl(channel: Channel): String? = officialLiveUrls[channel.id]

        fun officialEmbedUrl(channel: Channel): String? = officialEmbedUrls[channel.id]
    }
}
