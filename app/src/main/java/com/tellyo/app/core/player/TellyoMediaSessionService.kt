package com.tellyo.app.core.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns the single ExoPlayer/MediaSession pair for the application process.
 * Stream resolution remains outside this service; it only plays resolved MediaItems.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class TellyoMediaSessionService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val trackSelector = DefaultTrackSelector(
            this,
            AdaptiveTrackSelection.Factory(
                MIN_DURATION_FOR_QUALITY_INCREASE_MS,
                MAX_DURATION_FOR_QUALITY_DECREASE_MS,
                MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
                BANDWIDTH_FRACTION,
            ),
        )
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .build()
        val player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                // Do not inherit a muted player state from a previous session.
                volume = 1f
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .build()
            }
        mediaSession = MediaSession.Builder(this, player).setId(SESSION_ID).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val SESSION_ID = "tellyo-playback"
        const val MIN_DURATION_FOR_QUALITY_INCREASE_MS = 15_000
        const val MAX_DURATION_FOR_QUALITY_DECREASE_MS = 12_000
        const val MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 15_000
        const val BANDWIDTH_FRACTION = 0.7f
        const val MIN_BUFFER_MS = 20_000
        const val MAX_BUFFER_MS = 90_000
        const val BUFFER_FOR_PLAYBACK_MS = 3_000
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 6_000
    }
}
