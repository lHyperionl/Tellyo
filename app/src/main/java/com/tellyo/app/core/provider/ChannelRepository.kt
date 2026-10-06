package com.tellyo.app.core.provider

import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.StreamResolution
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-facing channel boundary. Features can work with channels and
 * resolutions without knowing which provider owns them.
 */
interface ChannelRepository {
    suspend fun getChannels(): List<Channel>

    suspend fun resolveStream(channel: Channel): StreamResolution
}

@Singleton
class DefaultChannelRepository @Inject constructor(
    private val providerRegistry: ProviderRegistry,
    private val channelCatalog: ChannelCatalogRepository,
) : ChannelRepository {
    override suspend fun getChannels(): List<Channel> {
        val configuredByProvider = channelCatalog.getChannels()
            .groupBy(Channel::providerId)
        return providerRegistry.all()
            .flatMap { provider ->
                configuredByProvider[provider.id]?.filter(Channel::enabled) ?: run {
                    // Keeps development-only providers usable while a config
                    // document is being introduced or upgraded.
                    provider.getChannels()
                }
            }
            .sortedBy(Channel::number)
    }

    override suspend fun resolveStream(channel: Channel): StreamResolution =
        providerRegistry.providerFor(channel.providerId)?.resolveStream(channel)
            ?: StreamResolution.Unavailable("No provider is registered for ${channel.providerId}")
}
