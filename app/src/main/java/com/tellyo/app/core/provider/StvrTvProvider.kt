package com.tellyo.app.core.provider

import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.StreamResolution
import com.tellyo.app.core.model.StreamType
import javax.inject.Inject
import javax.inject.Singleton

/** Official STVR stream-resolution boundary. Channel metadata comes from the local catalogue. */
@Singleton
class StvrTvProvider @Inject constructor(
    private val officialApi: StvrOfficialApi,
    private val channelCatalog: ChannelCatalogRepository,
    private val authorizedConfig: AuthorizedProviderConfigRepository = EmptyAuthorizedProviderConfigRepository,
) : TvProvider {
    override val id: String = PROVIDER_ID

    override suspend fun getChannels(): List<Channel> = channelCatalog.getChannels()
        .filter { it.providerId == id }
        .filter(Channel::enabled)

    override suspend fun resolveStream(channel: Channel): StreamResolution {
        if (channel.providerId != id || !channel.enabled) {
            return StreamResolution.Unavailable("Channel is not available in the STVR provider")
        }

        authorizedConfig.getStream(channel.id)?.let { source ->
            return StreamResolution.Playable(source)
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
    }
}
