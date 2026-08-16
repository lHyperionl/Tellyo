package com.tellyo.app.core.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tellyo.app.core.model.VideoQualityPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/** Activity-side client for the central MediaSession service. */
@Singleton
class Media3PlaybackClient @Inject constructor(
    @ApplicationContext context: Context,
) : PlaybackService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerFuture = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, TellyoMediaSessionService::class.java)),
    ).buildAsync()
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    private val _player = MutableStateFlow<Player?>(null)
    private var lastPlaybackError: String? = null
    private var lastSource: com.tellyo.app.core.model.StreamSource? = null
    private var automaticRetryCount = 0
    private var retryJob: Job? = null
    private var bufferingWatchdogJob: Job? = null
    private var resumeAfterHostStop = false
    private var videoQualityPreference = VideoQualityPreference.AUTO

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    val player: StateFlow<Player?> = _player.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                scheduleBufferingWatchdog()
            } else {
                bufferingWatchdogJob?.cancel()
                bufferingWatchdogJob = null
            }
            updateState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateState()
        }

        override fun onPlayerError(error: PlaybackException) {
            val message = error.message ?: error.errorCodeName
            lastPlaybackError = message
            val source = lastSource
            if (source != null) {
                scheduleRetry(source, message)
            } else {
                _state.value = PlaybackState.Error(message)
            }
        }
    }

    init {
        scope.launch {
            runCatching {
                controllerFuture.await()
            }.onSuccess { controller ->
                controller.addListener(listener)
                _player.value = controller
                updateState()
            }.onFailure { error ->
                _state.value = PlaybackState.Error(error.message ?: "Unable to connect to playback service")
            }
        }
    }

    override suspend fun play(source: com.tellyo.app.core.model.StreamSource) {
        lastPlaybackError = null
        lastSource = source
        automaticRetryCount = 0
        resumeAfterHostStop = false
        retryJob?.cancel()
        bufferingWatchdogJob?.cancel()
        _state.value = PlaybackState.Resolving
        try {
            prepare(source)
        } catch (error: Exception) {
            _state.value = PlaybackState.Error(error.message ?: "Unable to start playback")
        }
    }

    /** Applies the user's quality policy immediately and to future media items. */
    fun setVideoQuality(preference: VideoQualityPreference) {
        videoQualityPreference = preference
        scope.launch {
            runCatching {
                applyVideoQuality(controllerFuture.await())
            }
        }
    }

    private suspend fun prepare(source: com.tellyo.app.core.model.StreamSource) {
        lastPlaybackError = null
        val controller = controllerFuture.await()
        // The service owns the player, but the controller can retain playback
        // settings across media items. Always re-enable the audio renderer
        // and restore full player volume when a channel is opened.
        controller.volume = 1f
        applyVideoQuality(controller)
        controller.trackSelectionParameters = controller.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(source.channelId)
            .setUri(source.uri)
            .setMimeType(
                when (source.type) {
                    com.tellyo.app.core.model.StreamType.HLS -> MimeTypes.APPLICATION_M3U8
                    com.tellyo.app.core.model.StreamType.DASH -> MimeTypes.APPLICATION_MPD
                },
            )
            .build()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    private fun applyVideoQuality(controller: MediaController) {
        val builder = controller.trackSelectionParameters
            .buildUpon()
            .clearVideoSizeConstraints()
            .setForceHighestSupportedBitrate(videoQualityPreference == VideoQualityPreference.HIGH)
        if (videoQualityPreference == VideoQualityPreference.STABLE) {
            builder
                .setMaxVideoSize(1280, 720)
                .setMaxVideoBitrate(STABLE_MAX_VIDEO_BITRATE)
        }
        controller.trackSelectionParameters = builder.build()
    }

    override fun pause() {
        resumeAfterHostStop = false
        _player.value?.pause()
    }

    /** Pauses playback when the Activity is backgrounded or the device sleeps. */
    fun onHostStopped() {
        val player = _player.value ?: return
        if (lastSource != null && (player.isPlaying || player.playbackState == Player.STATE_BUFFERING)) {
            resumeAfterHostStop = true
            player.pause()
        }
    }

    /** Restores the live source after the Activity/device becomes active again. */
    fun onHostStarted() {
        if (!resumeAfterHostStop) return
        resumeAfterHostStop = false
        scope.launch {
            runCatching {
                val controller = controllerFuture.await()
                val source = lastSource ?: return@runCatching
                if (controller.currentMediaItem == null || controller.playbackState == Player.STATE_IDLE) {
                    prepare(source)
                } else {
                    controller.play()
                }
            }.onFailure { error ->
                _state.value = PlaybackState.Error(error.message ?: "Prehrávanie sa nepodarilo obnoviť")
            }
        }
    }

    override fun stop() {
        lastPlaybackError = null
        lastSource = null
        automaticRetryCount = MAX_AUTOMATIC_RETRIES
        retryJob?.cancel()
        bufferingWatchdogJob?.cancel()
        resumeAfterHostStop = false
        _player.value?.stop()
        _state.value = PlaybackState.Idle
    }

    private fun scheduleRetry(source: com.tellyo.app.core.model.StreamSource, message: String) {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
        if (automaticRetryCount >= MAX_AUTOMATIC_RETRIES) {
            _state.value = PlaybackState.Error(message)
            return
        }

        automaticRetryCount += 1
        val attempt = automaticRetryCount
        _state.value = PlaybackState.Retrying
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(RETRY_DELAY_MILLIS * attempt)
            try {
                prepare(source)
            } catch (retryError: Exception) {
                lastPlaybackError = retryError.message ?: message
                _state.value = PlaybackState.Error(lastPlaybackError ?: message)
            }
        }
    }

    private fun scheduleBufferingWatchdog() {
        if (bufferingWatchdogJob?.isActive == true) return
        bufferingWatchdogJob = scope.launch {
            delay(BUFFERING_TIMEOUT_MILLIS)
            val player = _player.value
            if (player?.playbackState == Player.STATE_BUFFERING) {
                val source = lastSource
                if (source != null) {
                    scheduleRetry(source, "Stream sa nepodarilo načítať")
                } else {
                    _state.value = PlaybackState.Error("Stream sa nepodarilo načítať")
                }
            }
        }
    }

    private fun updateState() {
        val player = _player.value ?: return
        val channelId = player.currentMediaItem?.mediaId ?: return
        if (player.playbackState == Player.STATE_IDLE && lastPlaybackError != null) {
            return
        }
        _state.value = when (player.playbackState) {
            Player.STATE_BUFFERING -> PlaybackState.Buffering
            Player.STATE_READY -> if (player.isPlaying) {
                PlaybackState.Playing(channelId)
            } else {
                PlaybackState.Paused(channelId)
            }
            Player.STATE_ENDED, Player.STATE_IDLE -> PlaybackState.Idle
            else -> PlaybackState.Idle
        }
    }

    private companion object {
        const val MAX_AUTOMATIC_RETRIES = 3
        const val RETRY_DELAY_MILLIS = 1_000L
        const val BUFFERING_TIMEOUT_MILLIS = 20_000L
        const val STABLE_MAX_VIDEO_BITRATE = 3_000_000
    }
}
