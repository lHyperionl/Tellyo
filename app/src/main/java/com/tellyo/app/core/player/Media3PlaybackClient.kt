package com.tellyo.app.core.player

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tellyo.app.core.model.VideoQualityPreference
import com.tellyo.app.core.network.NetworkMonitor
import com.tellyo.app.core.network.NetworkStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred

/** Activity-side client for the central MediaSession service. */
@Singleton
class Media3PlaybackClient internal constructor(
    private val controllerConnection: Deferred<Player>,
    private val playbackLogger: PlaybackLogger,
    private val telemetryStore: PlaybackTelemetryStore,
    private val networkMonitor: NetworkMonitor,
) : PlaybackService {
    @Inject constructor(
        @ApplicationContext context: Context,
        playbackLogger: PlaybackLogger,
        telemetryStore: PlaybackTelemetryStore,
        networkMonitor: NetworkMonitor,
    ) : this(connect(context), playbackLogger, telemetryStore, networkMonitor)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    private val _player = MutableStateFlow<Player?>(null)
    private var lastPlaybackError: String? = null
    private var lastSource: com.tellyo.app.core.model.StreamSource? = null
    private var automaticRetryCount = 0
    private var retryJob: Job? = null
    private var bufferingWatchdogJob: Job? = null
    private var resumeAfterHostStop = false
    private var videoQualityPreference = VideoQualityPreference.AUTO
    private var telemetryJob: Job? = null
    private var hostActive = true
    private var resumeJob: Job? = null
    private var playbackRequested = false

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    val player: StateFlow<Player?> = _player.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (hostActive && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                playbackRequested = playWhenReady
                if (!playWhenReady) {
                    retryJob?.cancel()
                    bufferingWatchdogJob?.cancel()
                    resumeJob?.cancel()
                    telemetryJob?.cancel()
                    resumeAfterHostStop = false
                }
            }
            updateState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                telemetryJob?.cancel()
            }
            if (playbackState == Player.STATE_BUFFERING) {
                scheduleBufferingWatchdog()
            } else {
                bufferingWatchdogJob?.cancel()
                bufferingWatchdogJob = null
            }
            updateState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && hostActive && lastSource != null) {
                _player.value?.let(::startTelemetrySampling)
            } else {
                telemetryJob?.cancel()
            }
            updateState()
        }

        override fun onPlayerError(error: PlaybackException) {
            val message = error.message ?: error.errorCodeName
            playbackLogger.record(
                PlaybackLogEvent(
                    name = "player_error",
                    channelId = lastSource?.channelId,
                    streamType = lastSource?.type?.name,
                    detail = message,
                ),
            )
            lastPlaybackError = message
            val source = lastSource
            if (source != null) {
                scheduleRetry(source, message)
            } else {
                _state.value = PlaybackState.Error(message)
            }
        }
    }

    // Browsing and official web players do not need an ExoPlayer/MediaSession.
    // Share a lazy connection so cancelling a playback request cannot cancel it.
    private val connectedController = scope.async(start = CoroutineStart.LAZY) {
        try {
            controllerConnection.await().also { controller ->
                controller.addListener(listener)
                _player.value = controller
                playbackLogger.record(PlaybackLogEvent(name = "controller_ready"))
                updateState()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            playbackLogger.record(
                PlaybackLogEvent(name = "controller_error", detail = error.message),
            )
            throw error
        }
    }

    override suspend fun play(source: com.tellyo.app.core.model.StreamSource) {
        playbackRequested = true
        lastPlaybackError = null
        lastSource = source
        automaticRetryCount = 0
        resumeAfterHostStop = !hostActive
        resumeJob?.cancel()
        retryJob?.cancel()
        bufferingWatchdogJob?.cancel()
        telemetryStore.reset(source)
        playbackLogger.record(
            PlaybackLogEvent(
                name = "play_requested",
                channelId = source.channelId,
                streamType = source.type.name,
            ),
        )
        _state.value = PlaybackState.Resolving
        try {
            prepare(source)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = PlaybackState.Error(error.message ?: "Unable to start playback")
        }
    }

    /** Applies the user's quality policy immediately and to future media items. */
    fun setVideoQuality(preference: VideoQualityPreference) {
        videoQualityPreference = preference
        _player.value?.let(::applyVideoQuality)
    }

    private suspend fun prepare(source: com.tellyo.app.core.model.StreamSource) {
        val controller = connectedController.await()
        if (!hostActive || !playbackRequested || lastSource !== source || networkMonitor.status.value != NetworkStatus.Available) return
        lastPlaybackError = null
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
            .setRequestMetadata(StreamMediaSourceFactory.requestMetadata(source.headers))
            .setMimeType(
                when (source.type) {
                    com.tellyo.app.core.model.StreamType.HLS -> MimeTypes.APPLICATION_M3U8
                    com.tellyo.app.core.model.StreamType.DASH -> MimeTypes.APPLICATION_MPD
                },
            )
            .apply {
                source.targetLiveOffsetMs?.let { offset ->
                    setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(offset)
                            .build(),
                    )
                }
            }
            .build()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
        startTelemetrySampling(controller)
        playbackLogger.record(
            PlaybackLogEvent(
                name = "source_prepared",
                channelId = source.channelId,
                streamType = source.type.name,
            ),
        )
    }

    private fun applyVideoQuality(controller: Player) {
        val builder = controller.trackSelectionParameters
            .buildUpon()
            .clearVideoSizeConstraints()
            .setMaxVideoBitrate(Int.MAX_VALUE)
            // AVC is less demanding on TV decoders; retain HEVC-only streams
            // while favouring AVC when an adaptive feed offers both codecs.
            .setPreferredVideoMimeTypes(*if (videoQualityPreference == VideoQualityPreference.HIGH) {
                emptyArray()
            } else {
                arrayOf(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265)
            })
            .setForceHighestSupportedBitrate(videoQualityPreference == VideoQualityPreference.HIGH)
        if (videoQualityPreference == VideoQualityPreference.STABLE) {
            builder
                .setMaxVideoSize(1280, 720)
                .setMaxVideoBitrate(STABLE_MAX_VIDEO_BITRATE)
        }
        controller.trackSelectionParameters = builder.build()
    }

    override fun pause() {
        playbackRequested = false
        resumeAfterHostStop = false
        retryJob?.cancel()
        bufferingWatchdogJob?.cancel()
        resumeJob?.cancel()
        _player.value?.pause()
        lastSource?.let { _state.value = PlaybackState.Paused(it.channelId) }
    }

    /** Pauses playback when the Activity is backgrounded or the device sleeps. */
    fun onHostStopped() {
        hostActive = false
        val player = _player.value
        resumeAfterHostStop = playbackRequested && lastSource != null && (player?.playWhenReady == true ||
            _state.value == PlaybackState.Resolving || _state.value == PlaybackState.Retrying ||
            (_state.value == PlaybackState.Buffering && player?.playWhenReady != false))
        retryJob?.cancel()
        bufferingWatchdogJob?.cancel()
        resumeJob?.cancel()
        telemetryJob?.cancel()
        playbackLogger.record(PlaybackLogEvent(name = "host_stopped", channelId = lastSource?.channelId))
        player?.pause()
    }

    /** Restores the live source after the Activity/device becomes active again. */
    fun onHostStarted() {
        hostActive = true
        if (networkMonitor.status.value != NetworkStatus.Available) return
        if (!resumeAfterHostStop) return
        resumeAfterHostStop = false
        playbackLogger.record(
            PlaybackLogEvent(name = "host_started", channelId = lastSource?.channelId),
        )
        val source = lastSource ?: return
        resumeJob = scope.launch {
            runCatching {
                val controller = connectedController.await()
                if (!hostActive || lastSource !== source) return@runCatching
                if (controller.currentMediaItem == null || controller.playbackState == Player.STATE_IDLE) {
                    prepare(source)
                } else {
                    controller.play()
                    startTelemetrySampling(controller)
                    if (controller.playbackState == Player.STATE_BUFFERING) scheduleBufferingWatchdog()
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _state.value = PlaybackState.Error(error.message ?: "Prehrávanie sa nepodarilo obnoviť")
            }
        }
    }

    override fun stop() {
        playbackRequested = false
        playbackLogger.record(PlaybackLogEvent(name = "stopped", channelId = lastSource?.channelId))
        lastPlaybackError = null
        lastSource = null
        automaticRetryCount = MAX_AUTOMATIC_RETRIES
        retryJob?.cancel()
        bufferingWatchdogJob?.cancel()
        resumeJob?.cancel()
        telemetryJob?.cancel()
        resumeAfterHostStop = false
        _player.value?.stop()
        _player.value?.clearMediaItems()
        telemetryStore.reset(null)
        _state.value = PlaybackState.Idle
    }

    private fun startTelemetrySampling(player: Player) {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (hostActive && lastSource != null) {
                telemetryStore.sample(player)
                delay(TELEMETRY_SAMPLE_MILLIS)
            }
        }
    }

    private fun scheduleRetry(source: com.tellyo.app.core.model.StreamSource, message: String) {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
        if (!hostActive || !playbackRequested || lastSource !== source || networkMonitor.status.value != NetworkStatus.Available) return
        if (automaticRetryCount >= MAX_AUTOMATIC_RETRIES) {
            _state.value = PlaybackState.Error(message)
            return
        }

        automaticRetryCount += 1
        val attempt = automaticRetryCount
        playbackLogger.record(
            PlaybackLogEvent(
                name = "retry_scheduled",
                channelId = source.channelId,
                streamType = source.type.name,
                attempt = attempt,
                detail = message,
            ),
        )
        _state.value = PlaybackState.Retrying
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(RETRY_DELAY_MILLIS * attempt)
            try {
                prepare(source)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (retryError: Exception) {
                lastPlaybackError = retryError.message ?: message
                _state.value = PlaybackState.Error(lastPlaybackError ?: message)
            }
        }
    }

    private fun scheduleBufferingWatchdog() {
        if (!hostActive || lastSource == null) return
        if (bufferingWatchdogJob?.isActive == true) return
        bufferingWatchdogJob = scope.launch {
            val watchdog = BufferingWatchdog(SystemClock.elapsedRealtime())
            while (true) {
                delay(1_000L)
                val player = _player.value ?: return@launch
                if (!hostActive || !playbackRequested || !player.playWhenReady ||
                    player.playbackState != Player.STATE_BUFFERING) return@launch
                if (watchdog.shouldRetry(SystemClock.elapsedRealtime(), player.totalBufferedDuration)) {
                    val source = lastSource ?: return@launch
                    scheduleRetry(source, "Stream sa nepodarilo načítať")
                    return@launch
                }
            }
        }
    }

    private fun updateState() {
        val source = lastSource ?: return
        if (!playbackRequested) {
            _state.value = PlaybackState.Paused(source.channelId)
            return
        }
        val player = _player.value ?: return
        val channelId = player.currentMediaItem?.mediaId ?: return
        if (channelId != source.channelId) return
        if (player.playbackState == Player.STATE_IDLE && lastPlaybackError != null) {
            return
        }
        val nextState = when (player.playbackState) {
            Player.STATE_BUFFERING -> if (player.playWhenReady) PlaybackState.Buffering else PlaybackState.Paused(channelId)
            Player.STATE_READY -> if (player.isPlaying) {
                PlaybackState.Playing(channelId)
            } else {
                PlaybackState.Paused(channelId)
            }
            Player.STATE_ENDED, Player.STATE_IDLE -> PlaybackState.Idle
            else -> PlaybackState.Idle
        }
        if (_state.value != nextState) {
            _state.value = nextState
            playbackLogger.record(
                PlaybackLogEvent(
                    name = "state_changed",
                    channelId = channelId,
                    detail = nextState.logName(),
                ),
            )
        }
    }

    private fun PlaybackState.logName(): String = when (this) {
        PlaybackState.Idle -> "idle"
        PlaybackState.Resolving -> "resolving"
        PlaybackState.Buffering -> "buffering"
        is PlaybackState.Playing -> "playing"
        is PlaybackState.Paused -> "paused"
        PlaybackState.Retrying -> "retrying"
        is PlaybackState.OfficialEmbed -> "official_embed"
        is PlaybackState.External -> "external"
        is PlaybackState.Unavailable -> "unavailable"
        is PlaybackState.Error -> "error"
    }

    private companion object {
        // A caller cancelling its await must not cancel the process-wide session connection.
        fun connect(context: Context): Deferred<Player> =
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).async(start = CoroutineStart.LAZY) {
                MediaController.Builder(
                    context,
                    SessionToken(context, ComponentName(context, TellyoMediaSessionService::class.java)),
                ).buildAsync().await()
            }

        const val MAX_AUTOMATIC_RETRIES = 3
        const val RETRY_DELAY_MILLIS = 1_000L
        const val STABLE_MAX_VIDEO_BITRATE = 3_000_000
        const val TELEMETRY_SAMPLE_MILLIS = 1_000L
    }
}
