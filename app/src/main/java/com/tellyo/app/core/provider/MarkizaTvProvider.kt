package com.tellyo.app.core.provider

import android.net.Uri
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.StreamResolution
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Official Markíza channel catalogue.
 *
 * The provider intentionally resolves to Markíza's official live/Voyo
 * destination. It does not scrape pages, manufacture HLS URLs, or bypass
 * programme, subscription, or DRM restrictions.
 */
@Singleton
class MarkizaTvProvider @Inject constructor() : TvProvider {
    override val id: String = PROVIDER_ID

    override suspend fun getChannels(): List<Channel> = channels

    override suspend fun resolveStream(channel: Channel): StreamResolution {
        if (channel.providerId != id || !channel.enabled) {
            return StreamResolution.Unavailable("Channel is not available in the Markíza provider")
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

        private val officialLiveUrls = mapOf(
            MARKIZA_ID to "https://www.markiza.sk/live/1-markiza",
            DOMA_ID to "https://www.markiza.sk/live/3-doma",
            DAJTO_ID to "https://www.markiza.sk/live/2-dajto",
            KRIMI_ID to "https://www.markiza.sk/live/22-krimi",
            KLASIK_ID to "https://www.markiza.sk/live",
        )

        fun officialLiveUrl(channel: Channel): String? = officialLiveUrls[channel.id]

        val channels: List<Channel> = listOf(
            Channel(
                id = MARKIZA_ID,
                number = 13,
                name = "Markíza",
                shortName = "Markíza",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.GENERAL,
                enabled = true,
            ),
            Channel(
                id = DOMA_ID,
                number = 14,
                name = "Doma",
                shortName = "Doma",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.GENERAL,
                enabled = true,
            ),
            Channel(
                id = DAJTO_ID,
                number = 15,
                name = "Dajto",
                shortName = "Dajto",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.GENERAL,
                enabled = true,
            ),
            Channel(
                id = KRIMI_ID,
                number = 16,
                name = "Markíza Krimi",
                shortName = "Krimi",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.GENERAL,
                enabled = true,
            ),
            Channel(
                id = KLASIK_ID,
                number = 17,
                name = "Markíza Klasik",
                shortName = "Klasik",
                logoUrl = null,
                providerId = PROVIDER_ID,
                category = ChannelCategory.GENERAL,
                enabled = true,
            ),
        )
    }
}
