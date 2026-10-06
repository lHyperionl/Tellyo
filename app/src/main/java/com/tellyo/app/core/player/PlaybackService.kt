package com.tellyo.app.core.player

import android.net.Uri
import com.tellyo.app.core.model.StreamSource
import kotlinx.coroutines.flow.StateFlow

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Resolving : PlaybackState
    data object Buffering : PlaybackState
    data class Playing(val channelId: String) : PlaybackState
    data class Paused(val channelId: String) : PlaybackState
    data object Retrying : PlaybackState
    data class OfficialEmbed(val url: String) : PlaybackState
    data class External(
        val packageName: String?,
        val deepLink: Uri? = null,
    ) : PlaybackState
    data class Unavailable(val reason: String) : PlaybackState
    data class Error(val message: String) : PlaybackState
}

/** Central player boundary. UI never owns an ExoPlayer instance directly. */
interface PlaybackService {
    val state: StateFlow<PlaybackState>

    suspend fun play(source: StreamSource)

    fun pause()

    fun stop()
}
