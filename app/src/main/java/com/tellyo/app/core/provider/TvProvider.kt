package com.tellyo.app.core.provider

import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.StreamResolution

/**
 * Provider boundary for future legal channel integrations.
 * UI code only receives domain data and never resolves stream URLs itself.
 */
interface TvProvider {
    val id: String

    suspend fun getChannels(): List<Channel>

    suspend fun resolveStream(channel: Channel): StreamResolution
}
