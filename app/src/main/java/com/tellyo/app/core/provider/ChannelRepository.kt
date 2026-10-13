package com.tellyo.app.core.provider

import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.StreamResolution
import javax.inject.Inject
import javax.inject.Singleton
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val providerRegistry: Lazy<ProviderRegistry>,
    private val channelCatalog: ChannelCatalogRepository,
) : ChannelRepository {
    override suspend fun getChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val configuredByProvider = channelCatalog.getChannels()
            .groupBy(Channel::providerId)
        providerRegistry.get().all()
            .flatMap { provider ->
                configuredByProvider[provider.id]?.filter(Channel::enabled) ?: run {
                    // Keeps development-only providers usable while a config
                    // document is being introduced or upgraded.
                    provider.getChannels()
                }
            }
            .sortedBy(Channel::number)
    }

    override suspend fun resolveStream(channel: Channel): StreamResolution = withContext(Dispatchers.IO) {
        providerRegistry.get().providerFor(channel.providerId)?.resolveStream(channel)
            ?: StreamResolution.Unavailable("No provider is registered for ${channel.providerId}")
    }
}
