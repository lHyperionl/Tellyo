package com.tellyo.app.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tellyo.app.core.epg.EpgRepository
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.StreamResolution
import com.tellyo.app.core.network.NetworkMonitor
import com.tellyo.app.core.network.NetworkStatus
import com.tellyo.app.core.network.StreamHealthChecker
import com.tellyo.app.core.player.PlaybackService
import com.tellyo.app.core.player.PlaybackState
import com.tellyo.app.core.player.PlaybackTelemetry
import com.tellyo.app.core.player.PlaybackTelemetryStore
import com.tellyo.app.core.provider.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChannelCheckStatus {
    Idle,
    Checking,
    Ok,
    External,
    Failed,
}

data class ChannelHealth(
    val channel: Channel,
    val status: ChannelCheckStatus = ChannelCheckStatus.Idle,
    val durationMillis: Long? = null,
    val detail: String? = null,
)

enum class EpgRefreshStatus {
    Idle,
    Refreshing,
    Updated,
    Failed,
}

data class DiagnosticsUiState(
    val networkStatus: NetworkStatus = NetworkStatus.Unavailable,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val channelHealth: List<ChannelHealth> = emptyList(),
    val isCheckingChannels: Boolean = false,
    val epgStatus: EpgRefreshStatus = EpgRefreshStatus.Idle,
    val epgDetail: String? = null,
    val lastEpgRefresh: Instant? = null,
    val playbackTelemetry: PlaybackTelemetry = PlaybackTelemetry(),
)

sealed interface DiagnosticsAction {
    data object CheckChannels : DiagnosticsAction
    data object RefreshEpg : DiagnosticsAction
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository,
    private val playbackService: PlaybackService,
    private val playbackTelemetryStore: PlaybackTelemetryStore,
    private val networkMonitor: NetworkMonitor,
    private val streamHealthChecker: StreamHealthChecker,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()
    private var channelCheckJob: Job? = null
    private var epgRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            networkMonitor.status.collect { status ->
                _uiState.update { it.copy(networkStatus = status) }
            }
        }
        viewModelScope.launch {
            playbackService.state.collect { playbackState ->
                _uiState.update { it.copy(playbackState = playbackState) }
            }
        }
        viewModelScope.launch {
            playbackTelemetryStore.state.collect { telemetry ->
                _uiState.update { it.copy(playbackTelemetry = telemetry) }
            }
        }
        loadChannels()
    }

    fun onAction(action: DiagnosticsAction) {
        when (action) {
            DiagnosticsAction.CheckChannels -> checkChannels()
            DiagnosticsAction.RefreshEpg -> refreshEpg()
        }
    }

    private fun loadChannels() {
        viewModelScope.launch {
            runCatching { channelRepository.getChannels().sortedBy { it.number } }
                .onSuccess { channels ->
                    _uiState.update { state ->
                        state.copy(channelHealth = channels.map { ChannelHealth(it) })
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            channelHealth = emptyList(),
                            epgDetail = error.message ?: "Kanály sa nepodarilo načítať.",
                        )
                    }
                }
        }
    }

    private fun checkChannels() {
        if (channelCheckJob?.isActive == true) return
        channelCheckJob = viewModelScope.launch {
            val channels = _uiState.value.channelHealth.map { it.channel }
            if (channels.isEmpty()) {
                loadChannels()
                return@launch
            }
            if (_uiState.value.networkStatus != NetworkStatus.Available) {
                _uiState.update { state ->
                    state.copy(
                        channelHealth = state.channelHealth.map {
                            it.copy(status = ChannelCheckStatus.Failed, detail = "Sieť nie je dostupná")
                        },
                        isCheckingChannels = false,
                    )
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    isCheckingChannels = true,
                    channelHealth = state.channelHealth.map {
                        it.copy(status = ChannelCheckStatus.Checking, durationMillis = null, detail = null)
                    },
                )
            }

            try {
                channels.forEach { channel ->
                    val startedAt = System.nanoTime()
                    try {
                        val resolution = channelRepository.resolveStream(channel)
                        val result = when (resolution) {
                            is StreamResolution.Playable -> {
                                val health = streamHealthChecker.check(resolution.source)
                                val elapsed = (System.nanoTime() - startedAt) / 1_000_000L
                                Triple(
                                    if (health.isReachable) ChannelCheckStatus.Ok else ChannelCheckStatus.Failed,
                                    elapsed,
                                    health.detail,
                                )
                            }
                            is StreamResolution.OfficialEmbed ->
                                Triple(
                                    ChannelCheckStatus.External,
                                    (System.nanoTime() - startedAt) / 1_000_000L,
                                    "Oficiálny vložený prehrávač",
                                )
                            is StreamResolution.ExternalApp ->
                                Triple(ChannelCheckStatus.External, (System.nanoTime() - startedAt) / 1_000_000L, "Oficiálny web")
                            is StreamResolution.Unavailable ->
                                Triple(ChannelCheckStatus.Failed, (System.nanoTime() - startedAt) / 1_000_000L, resolution.reason)
                        }
                        updateChannel(channel.id, result.first, result.second, result.third)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        val elapsed = (System.nanoTime() - startedAt) / 1_000_000L
                        updateChannel(
                            channel.id,
                            ChannelCheckStatus.Failed,
                            elapsed,
                            error.message ?: "Kontrola zlyhala",
                        )
                    }
                }
            } finally {
                _uiState.update { it.copy(isCheckingChannels = false) }
            }
        }
    }

    private fun updateChannel(
        channelId: String,
        status: ChannelCheckStatus,
        durationMillis: Long,
        detail: String,
    ) {
        _uiState.update { state ->
            state.copy(
                channelHealth = state.channelHealth.map {
                    if (it.channel.id == channelId) {
                        it.copy(status = status, durationMillis = durationMillis, detail = detail)
                    } else {
                        it
                    }
                },
            )
        }
    }

    private fun refreshEpg() {
        if (epgRefreshJob?.isActive == true) return
        epgRefreshJob = viewModelScope.launch {
            if (_uiState.value.networkStatus != NetworkStatus.Available) {
                _uiState.update {
                    it.copy(epgStatus = EpgRefreshStatus.Failed, epgDetail = "Sieť nie je dostupná")
                }
                return@launch
            }
            _uiState.update { it.copy(epgStatus = EpgRefreshStatus.Refreshing, epgDetail = null) }
            try {
                epgRepository.refresh()
                _uiState.update {
                    it.copy(
                        epgStatus = EpgRefreshStatus.Updated,
                        epgDetail = null,
                        lastEpgRefresh = Instant.now(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        epgStatus = EpgRefreshStatus.Failed,
                        epgDetail = error.message ?: "EPG sa nepodarilo obnoviť",
                    )
                }
            }
        }
    }
}
