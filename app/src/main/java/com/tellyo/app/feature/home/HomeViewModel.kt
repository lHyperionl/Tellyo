package com.tellyo.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tellyo.app.core.epg.EpgRepository
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.model.StreamResolution
import com.tellyo.app.core.player.PlaybackService
import com.tellyo.app.core.player.PlaybackState
import com.tellyo.app.core.provider.ChannelRepository
import com.tellyo.app.core.settings.FavoriteSettings
import com.tellyo.app.core.settings.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NavigationDestination(val label: String) {
    HOME("Domov"),
    GUIDE("TV program"),
    FAVORITES("Obľúbené"),
    SETTINGS("Nastavenia"),
    DIAGNOSTICS("Diagnostika"),
}

data class HomeUiState(
    val selectedDestination: NavigationDestination = NavigationDestination.HOME,
    val focusContentOnDestination: Boolean = true,
    val selectedChannelId: String = "",
    val focusedFavoriteId: String = "",
    val playerVisible: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val channels: List<Channel> = emptyList(),
    val programsByChannel: Map<String, List<Program>> = emptyMap(),
    val favoriteSettings: FavoriteSettings = FavoriteSettings(),
    val isLoading: Boolean = true,
    val dataError: String? = null,
)

sealed interface HomeAction {
    data class SelectDestination(
        val destination: NavigationDestination,
        val requestContentFocus: Boolean = true,
    ) : HomeAction
    data class SelectChannel(val channelId: String) : HomeAction
    data class FocusFavorite(val channelId: String) : HomeAction
    data class PlayChannel(val channelId: String) : HomeAction
    data object OpenPlayer : HomeAction
    data object ClosePlayer : HomeAction
    data object RetryPlayback : HomeAction
    data class ToggleFavorite(val channelId: String) : HomeAction
    data class MoveFavorite(val channelId: String, val direction: Int) : HomeAction
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val playbackService: PlaybackService,
    private val epgRepository: EpgRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var epgObservationJobs: List<Job> = emptyList()
    private var epgRefreshJob: Job? = null
    private var streamRefreshJob: Job? = null
    private var automaticStreamRefreshAttempts = 0

    init {
        viewModelScope.launch {
            playbackService.state.collect { playbackState ->
                _uiState.update { it.copy(playbackState = playbackState) }
                if (playbackState is PlaybackState.Error) {
                    scheduleStreamRefresh()
                }
            }
        }
        viewModelScope.launch {
            favoritesRepository.settings.collect { favorites ->
                _uiState.update { it.copy(favoriteSettings = favorites) }
            }
        }
        loadChannels()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.SelectDestination -> _uiState.update {
                it.copy(
                    selectedDestination = action.destination,
                    focusContentOnDestination = action.requestContentFocus,
                )
            }

            is HomeAction.SelectChannel -> _uiState.update {
                it.copy(selectedChannelId = action.channelId)
            }

            is HomeAction.FocusFavorite -> _uiState.update {
                it.copy(focusedFavoriteId = action.channelId, selectedChannelId = action.channelId)
            }

            is HomeAction.PlayChannel -> playChannel(action.channelId)

            HomeAction.OpenPlayer -> {
                if (_uiState.value.selectedChannelId.isBlank()) {
                    _uiState.update { it.copy(dataError = "Kanály ešte nie sú pripravené.") }
                } else {
                    playChannel(_uiState.value.selectedChannelId)
                }
            }

            HomeAction.ClosePlayer -> {
                streamRefreshJob?.cancel()
                automaticStreamRefreshAttempts = 0
                playbackService.stop()
                _uiState.update { it.copy(playerVisible = false) }
            }

            HomeAction.RetryPlayback -> {
                streamRefreshJob?.cancel()
                automaticStreamRefreshAttempts = 0
                resolveAndPlay(_uiState.value.selectedChannelId)
            }

            is HomeAction.ToggleFavorite -> viewModelScope.launch {
                favoritesRepository.toggle(action.channelId)
            }

            is HomeAction.MoveFavorite -> viewModelScope.launch {
                favoritesRepository.move(action.channelId, action.direction)
            }

        }
    }

    private fun loadChannels() {
        viewModelScope.launch {
            val channels = runCatching {
                channelRepository.getChannels()
                    .filter { it.enabled }
                    .sortedBy { it.number }
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        dataError = error.message ?: "Kanály sa nepodarilo načítať.",
                    )
                }
                return@launch
            }

            if (channels.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        dataError = "Nie sú dostupné žiadne kanály.",
                    )
                }
            } else {
                _uiState.update { state ->
                    val selectedId = state.selectedChannelId
                        .takeIf { id -> channels.any { channel -> channel.id == id } }
                        ?: channels.first().id
                    state.copy(
                        channels = channels,
                        selectedChannelId = selectedId,
                        focusedFavoriteId = state.focusedFavoriteId
                            .takeIf { id -> channels.any { channel -> channel.id == id } }
                            ?: selectedId,
                        isLoading = false,
                        dataError = null,
                    )
                }
                favoritesRepository.initialize(channels.map(Channel::id))
                refreshAndObserveEpg(channels)
            }
        }
    }

    private fun refreshAndObserveEpg(channels: List<Channel>) {
        epgObservationJobs.forEach(Job::cancel)
        epgRefreshJob?.cancel()

        epgRefreshJob = viewModelScope.launch {
            refreshEpg()
            while (true) {
                delay(EPG_REFRESH_INTERVAL_MILLIS)
                refreshEpg()
            }
        }

        viewModelScope.launch {

            val from = Instant.now().minus(Duration.ofHours(2))
            val to = Instant.now().plus(Duration.ofHours(24))
            epgObservationJobs = channels.map { channel ->
                launch {
                    epgRepository.observePrograms(channel.id, from, to).collect { programs ->
                        _uiState.update { state ->
                            state.copy(
                                programsByChannel = state.programsByChannel + (channel.id to programs),
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun refreshEpg() {
        try {
            epgRepository.refresh()
            _uiState.update { it.copy(dataError = null) }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.update {
                it.copy(dataError = error.message ?: "TV program sa nepodarilo načítať.")
            }
        }
    }

    private fun resolveAndPlay(channelId: String) {
        _uiState.update {
            if (it.playerVisible && it.selectedChannelId == channelId) {
                it.copy(playbackState = PlaybackState.Resolving)
            } else {
                it
            }
        }
        viewModelScope.launch {
            runCatching {
                val channel = _uiState.value.channels.firstOrNull { it.id == channelId }
                    ?: channelRepository.getChannels().firstOrNull { it.id == channelId }
                    ?: error("Channel $channelId was not found")
                channelRepository.resolveStream(channel)
            }.onSuccess { resolution ->
                when (resolution) {
                    is StreamResolution.Playable -> playbackService.play(resolution.source)
                    is StreamResolution.ExternalApp -> _uiState.update {
                        it.copy(
                            playbackState = PlaybackState.External(
                                packageName = resolution.packageName,
                                deepLink = resolution.deepLink,
                            ),
                        )
                    }
                    is StreamResolution.Unavailable -> _uiState.update {
                        it.copy(playbackState = PlaybackState.Unavailable(resolution.reason))
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(playbackState = PlaybackState.Error(error.message ?: "Kanál sa nepodarilo spustiť."))
                }
            }
        }
    }

    private fun playChannel(channelId: String) {
        if (_uiState.value.channels.none { it.id == channelId }) {
            _uiState.update {
                it.copy(
                    playbackState = PlaybackState.Error("Kanál sa nepodarilo nájsť."),
                    dataError = "Kanál sa nepodarilo nájsť.",
                )
            }
            return
        }

        // Stop any previous channel before resolving the next one so a failed
        // resolution cannot leave stale playback underneath the new state.
        streamRefreshJob?.cancel()
        automaticStreamRefreshAttempts = 0
        playbackService.stop()
        _uiState.update {
            it.copy(
                selectedChannelId = channelId,
                focusedFavoriteId = channelId,
                playerVisible = true,
                playbackState = PlaybackState.Resolving,
            )
        }
        resolveAndPlay(channelId)
    }

    /** Re-resolves the provider source after repeated player failures.
     *
     * This matters for short-lived or rotated manifest URLs: retrying the same
     * MediaItem can never recover an expired source, while a fresh provider
     * resolution can.
     */
    private fun scheduleStreamRefresh() {
        val state = _uiState.value
        val channelId = state.selectedChannelId
        if (!state.playerVisible || channelId.isBlank() || automaticStreamRefreshAttempts >= MAX_STREAM_REFRESHES) {
            return
        }

        automaticStreamRefreshAttempts += 1
        streamRefreshJob?.cancel()
        streamRefreshJob = viewModelScope.launch {
            delay(STREAM_REFRESH_DELAY_MILLIS)
            val current = _uiState.value
            if (current.playerVisible && current.selectedChannelId == channelId) {
                resolveAndPlay(channelId)
            }
        }
    }

    private companion object {
        const val EPG_REFRESH_INTERVAL_MILLIS = 60_000L
        const val STREAM_REFRESH_DELAY_MILLIS = 1_500L
        const val MAX_STREAM_REFRESHES = 2
    }
}
