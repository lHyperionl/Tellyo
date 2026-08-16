package com.tellyo.app.core.provider

import android.net.Uri
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.ChannelCategory
import com.tellyo.app.core.model.StreamResolution
import com.tellyo.app.core.model.StreamSource
import com.tellyo.app.core.model.StreamType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Development-only provider. It uses a public Mux HLS test asset and is not a broadcaster integration.
 */
@Singleton
class PreviewTvProvider @Inject constructor() : TvProvider {
    override val id: String = PROVIDER_ID

    override suspend fun getChannels(): List<Channel> = previewChannels

    override suspend fun resolveStream(channel: Channel): StreamResolution {
        if (channel.providerId != id || !channel.enabled) {
            return StreamResolution.Unavailable("Channel is not available in the preview provider")
        }
        return StreamResolution.Playable(
            StreamSource(
                channelId = channel.id,
                uri = Uri.parse(TEST_HLS_URI),
                type = StreamType.HLS,
                isTimeshiftable = false,
            ),
        )
    }

    companion object {
        const val PROVIDER_ID = "preview"
        const val TEST_HLS_URI = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

        val previewChannels: List<Channel> = listOf(
            Channel("stvr1", 1, "STVR 1", "STVR 1", null, PROVIDER_ID, ChannelCategory.GENERAL, true),
            Channel("joj", 2, "JOJ", "JOJ", null, PROVIDER_ID, ChannelCategory.GENERAL, true),
            Channel("ta3", 3, "TA3", "TA3", null, PROVIDER_ID, ChannelCategory.NEWS, true),
            Channel("24", 4, ":24", ":24", null, PROVIDER_ID, ChannelCategory.NEWS, true),
            Channel("dvojka", 5, "Dvojka", "Dvojka", null, PROVIDER_ID, ChannelCategory.CULTURE, true),
            Channel("rtvs", 6, "RTVS", "RTVS", null, PROVIDER_ID, ChannelCategory.CULTURE, true),
        )
    }
}
