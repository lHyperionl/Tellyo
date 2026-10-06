package com.tellyo.app.core.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Format
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.LoadEventInfo
import com.tellyo.app.BuildConfig
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Owns the single ExoPlayer/MediaSession pair for the application process.
 * Stream resolution remains outside this service; it only plays resolved MediaItems.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@AndroidEntryPoint
class TellyoMediaSessionService : MediaSessionService() {
    @Inject lateinit var telemetryStore: PlaybackTelemetryStore
    @Inject lateinit var playbackLogger: PlaybackLogger

    private var mediaSession: MediaSession? = null
    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            playbackLogger.record(PlaybackLogEvent(name = "video_decoder_initialized", detail = decoderName))
        }

        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            if (BuildConfig.DEBUG && mediaLoadData.dataType == C.DATA_TYPE_MEDIA) {
                playbackLogger.record(PlaybackLogEvent(
                    name = "media_segment_loaded",
                    detail = "bytes=${loadEventInfo.bytesLoaded} loadMs=${loadEventInfo.loadDurationMs} " +
                        "startMs=${mediaLoadData.mediaStartTimeMs} endMs=${mediaLoadData.mediaEndTimeMs}",
                ))
            }
        }

        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long,
        ) {
            telemetryStore.recordBandwidthEstimate(bitrateEstimate)
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            telemetryStore.recordDroppedFrames(droppedFrames)
            playbackLogger.record(
                PlaybackLogEvent(
                    name = "video_frames_dropped",
                    channelId = eventTime.currentMediaPeriodId?.periodUid?.toString(),
                    detail = "count=$droppedFrames elapsedMs=$elapsedMs",
                ),
            )
        }

        override fun onDownstreamFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            mediaLoadData: MediaLoadData,
        ) {
            telemetryStore.recordFormat(mediaLoadData.trackType, mediaLoadData.trackFormat)
            val format = mediaLoadData.trackFormat ?: return
            playbackLogger.record(
                PlaybackLogEvent(
                    name = "track_format_changed",
                    detail = format.telemetrySummary(mediaLoadData.trackType),
                ),
            )
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            telemetryStore.recordFormat(C.TRACK_TYPE_VIDEO, format)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            telemetryStore.recordFormat(C.TRACK_TYPE_AUDIO, format)
        }
    }

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
            // High-bitrate single-rendition IPTV can hit Media3's byte target
            // after only a few seconds. Retain the requested duration of headroom.
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector { mimeType, secure, tunneling ->
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
                    .sortedByDescending { it.hardwareAccelerated }
            }
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(StreamMediaSourceFactory(this))
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
                addAnalyticsListener(analyticsListener)
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

    private fun Format.telemetrySummary(trackType: Int): String = buildString {
        append("trackType=")
        append(trackType)
        sampleMimeType?.let { append(" mime=").append(it) }
        if (bitrate > 0) append(" bitrate=").append(bitrate)
        if (width > 0 && height > 0) append(" resolution=").append(width).append('x').append(height)
        language?.let { append(" language=").append(it) }
    }

    private companion object {
        const val SESSION_ID = "tellyo-playback"
        const val MIN_DURATION_FOR_QUALITY_INCREASE_MS = 15_000
        const val MAX_DURATION_FOR_QUALITY_DECREASE_MS = 12_000
        const val MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 15_000
        const val BANDWIDTH_FRACTION = 0.7f
        const val MIN_BUFFER_MS = 30_000
        const val MAX_BUFFER_MS = 90_000
        const val BUFFER_FOR_PLAYBACK_MS = 3_000
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 6_000
    }
}
