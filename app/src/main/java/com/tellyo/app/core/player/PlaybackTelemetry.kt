package com.tellyo.app.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.tellyo.app.core.model.StreamSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaybackTelemetry(
    val channelId: String? = null,
    val protocol: String? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val selectedVideoBitrate: Long? = null,
    val bandwidthEstimate: Long? = null,
    val droppedFrames: Long = 0,
    val bufferDurationMillis: Long? = null,
    val liveOffsetMillis: Long? = null,
    val positionMillis: Long? = null,
    val durationMillis: Long? = null,
    val videoMimeType: String? = null,
    val audioMimeType: String? = null,
    val audioLanguage: String? = null,
) {
    val effectiveBitrate: Long?
        get() = selectedVideoBitrate ?: bandwidthEstimate
}

/** Process-wide telemetry sink shared by the MediaSession service and diagnostics UI. */
@Singleton
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackTelemetryStore @Inject constructor() {
    private val _state = MutableStateFlow(PlaybackTelemetry())
    val state: StateFlow<PlaybackTelemetry> = _state.asStateFlow()

    fun reset(source: StreamSource?) {
        if (source == null) {
            _state.value = PlaybackTelemetry()
        } else {
            begin(source.channelId, source.type.name)
        }
    }

    fun begin(channelId: String, protocol: String) {
        _state.value = PlaybackTelemetry(channelId = channelId, protocol = protocol)
    }

    fun recordBandwidthEstimate(bitsPerSecond: Long) {
        if (bitsPerSecond <= 0) return
        _state.update { current ->
            if (current.channelId == null) current else current.copy(bandwidthEstimate = bitsPerSecond)
        }
    }

    fun recordDroppedFrames(count: Int) {
        if (count <= 0) return
        _state.update { if (it.channelId == null) it else it.copy(droppedFrames = it.droppedFrames + count) }
    }

    fun recordFormat(trackType: Int, format: Format?) {
        if (format == null || _state.value.channelId == null) return
        when (trackType) {
            C.TRACK_TYPE_VIDEO -> _state.update { current ->
                if (current.channelId == null) current else current.copy(
                    videoWidth = format.width.takeIf { it > 0 } ?: current.videoWidth,
                    videoHeight = format.height.takeIf { it > 0 } ?: current.videoHeight,
                    selectedVideoBitrate = format.bitrate.takeIf { it > 0 }?.toLong()
                        ?: current.selectedVideoBitrate,
                    videoMimeType = format.sampleMimeType ?: current.videoMimeType,
                )
            }
            C.TRACK_TYPE_AUDIO -> _state.update { current ->
                if (current.channelId == null) current else current.copy(
                    audioMimeType = format.sampleMimeType ?: current.audioMimeType,
                    audioLanguage = format.language ?: current.audioLanguage,
                )
            }
        }
    }

    fun sample(player: Player) {
        val channelId = _state.value.channelId ?: return
        if (player.currentMediaItem?.mediaId != channelId || player.playbackState == Player.STATE_IDLE) return
        player.currentTracks.groups.forEach { group ->
            for (index in 0 until group.length) {
                if (group.isTrackSelected(index)) {
                    recordFormat(group.type, group.getTrackFormat(index))
                }
            }
        }
        val videoSize = player.videoSize
        val liveOffset = player.currentLiveOffset.takeUnless { it == C.TIME_UNSET || it < 0 }
        val duration = player.duration.takeUnless { it == C.TIME_UNSET || it < 0 }
        _state.update { current ->
            if (current.channelId != channelId) current else current.copy(
                videoWidth = videoSize.width.takeIf { it > 0 } ?: current.videoWidth,
                videoHeight = videoSize.height.takeIf { it > 0 } ?: current.videoHeight,
                bufferDurationMillis = player.totalBufferedDuration.coerceAtLeast(0),
                liveOffsetMillis = liveOffset,
                positionMillis = player.currentPosition.coerceAtLeast(0),
                durationMillis = duration,
            )
        }
    }
}
