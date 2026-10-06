package com.tellyo.app.core.provider

import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.StreamResolution
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TA3 catalogue and legal playback boundary.
 *
 * A TA3-approved HLS/DASH route supplied by the authorized metadata service
 * plays in Tellyo. Otherwise Tellyo hosts TA3's official live web player,
 * which obtains its own current source and preserves broadcaster advertising.
 */
@Singleton
class Ta3TvProvider @Inject constructor(
    private val channelCatalog: ChannelCatalogRepository,
    private val authorizedConfig: AuthorizedProviderConfigRepository = EmptyAuthorizedProviderConfigRepository,
) : TvProvider {
    override val id: String = PROVIDER_ID

    override suspend fun getChannels(): List<Channel> = channelCatalog.getChannels()
        .filter { it.providerId == id }
        .filter(Channel::enabled)

    override suspend fun resolveStream(channel: Channel): StreamResolution {
        if (channel.providerId != id || !channel.enabled) {
            return StreamResolution.Unavailable("Channel is not available in the TA3 provider")
        }
        authorizedConfig.getStream(channel.id)?.let { source ->
            return StreamResolution.Playable(source)
        }
        return StreamResolution.OfficialEmbed(OFFICIAL_LIVE_URL)
    }

    companion object {
        const val PROVIDER_ID = "ta3"
        const val TA3_ID = "ta3"
        const val OFFICIAL_LIVE_URL = "https://www.ta3.com/live"
    }
}
