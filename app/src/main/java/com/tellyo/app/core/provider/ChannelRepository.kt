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
) : ChannelRepository {
    override suspend fun getChannels(): List<Channel> =
        providerRegistry.all().flatMap { it.getChannels() }

    override suspend fun resolveStream(channel: Channel): StreamResolution =
        providerRegistry.providerFor(channel.providerId)?.resolveStream(channel)
            ?: StreamResolution.Unavailable("No provider is registered for ${channel.providerId}")
}
