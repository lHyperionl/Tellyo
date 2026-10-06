package com.tellyo.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tellyo.app.core.epg.EpgRepository
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.model.StreamResolution
import com.tellyo.app.core.network.NetworkMonitor
import com.tellyo.app.core.network.NetworkStatus
import com.tellyo.app.core.player.PlaybackService
import com.tellyo.app.core.player.PlaybackState
import com.tellyo.app.core.provider.ChannelRepository
import com.tellyo.app.core.settings.FavoriteSettings
import com.tellyo.app.core.settings.FavoritesRepository
import com.tellyo.app.core.settings.SettingsRepository
import com.tellyo.app.core.time.AppClock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
    val now: Instant = Instant.now(),
    val selectedDestination: NavigationDestination = NavigationDestination.HOME,
    val focusContentOnDestination: Boolean = true,
    val selectedChannelId: String = "",
    val focusedFavoriteId: String = "",
    val playerVisible: Boolean = false,
    val restoreChannelFocus: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val channels: List<Channel> = emptyList(),
    val programsByChannel: Map<String, List<Program>> = emptyMap(),
    val favoriteSettings: FavoriteSettings = FavoriteSettings(),
    val isLoading: Boolean = true,
    val dataError: String? = null,
    val isInternetAvailable: Boolean = true,
    val embedReloadGeneration: Int = 0,
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
    data object ChannelFocusRestored : HomeAction
    data object RetryPlayback : HomeAction
    data class ToggleFavorite(val channelId: String) : HomeAction
    data class MoveFavorite(val channelId: String, val direction: Int) : HomeAction
}

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val playbackService: PlaybackService,
    private val epgRepository: EpgRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val clock: AppClock = AppClock(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(
        isInternetAvailable = networkMonitor.status.value == NetworkStatus.Available,
    ))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var epgObservationJob: Job? = null
    private var epgRefreshJob: Job? = null
    private var streamRefreshJob: Job? = null
    private var playbackResolutionJob: Job? = null
    private var activeChannelId: String? = null
    private var lastRememberedChannelId: String? = null
    private var automaticStreamRefreshAttempts = 0
    private var hostActive = true
    private var resumeResolution = false
    private var networkRecoveryChannelId: String? = null
    private var playbackActiveBeforeHostStop = false

    init {
        viewModelScope.launch {
            clock.now.collect { now -> _uiState.update { it.copy(now = now) } }
        }
        viewModelScope.launch {
            playbackService.state.collect { playbackState ->
                _uiState.update { it.copy(playbackState = playbackState) }
                if (playbackState is PlaybackState.Error) {
                    scheduleStreamRefresh()
                }
                if (playbackState is PlaybackState.Playing && playbackState.channelId == activeChannelId) {
                    rememberPlayedChannel(playbackState.channelId)
                }
            }
        }
        viewModelScope.launch {
            favoritesRepository.settings.collect { favorites ->
                _uiState.update { it.copy(favoriteSettings = favorites) }
            }
        }
        viewModelScope.launch {
            networkMonitor.status.collect { status ->
                val online = status == NetworkStatus.Available
                _uiState.update { it.copy(isInternetAvailable = online) }
                if (online) {
                    recoverAfterNetworkOutage()
                } else {
                    val state = _uiState.value
                    val wasActive = if (hostActive) shouldRecoverPlayback(state) else playbackActiveBeforeHostStop
                    if (state.playerVisible && wasActive) {
                        networkRecoveryChannelId = activeChannelId ?: state.selectedChannelId
                    }
                    playbackResolutionJob?.cancel()
                    streamRefreshJob?.cancel()
                    // Keep the source and manual pause choice, but cancel native retries.
                    if (state.playerVisible) playbackService.pause()
                }
            }
        }
        loadChannels()
    }

    fun onHostStarted() {
        hostActive = true
        val channels = _uiState.value.channels
        if (channels.isNotEmpty() && epgRefreshJob?.isActive != true) refreshAndObserveEpg(channels)
        if (recoverAfterNetworkOutage()) return
        if (resumeResolution && _uiState.value.playerVisible) {
            resumeResolution = false
            resolveAndPlay(_uiState.value.selectedChannelId)
        }
    }

    fun onHostStopped() {
        playbackActiveBeforeHostStop = shouldRecoverPlayback(_uiState.value)
        hostActive = false
        resumeResolution = playbackResolutionJob?.isActive == true || streamRefreshJob?.isActive == true
        playbackResolutionJob?.cancel()
        streamRefreshJob?.cancel()
        epgRefreshJob?.cancel()
        epgObservationJob?.cancel()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.SelectDestination -> _uiState.update {
                it.copy(
                    selectedDestination = action.destination,
                    focusContentOnDestination = action.requestContentFocus,
                    restoreChannelFocus = false,
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
                networkRecoveryChannelId = null
                playbackActiveBeforeHostStop = false
                resumeResolution = false
                playbackResolutionJob?.cancel()
                activeChannelId = null
                streamRefreshJob?.cancel()
                automaticStreamRefreshAttempts = 0
                playbackService.stop()
                _uiState.update {
                    it.copy(
                        playerVisible = false,
                        playbackState = PlaybackState.Idle,
                        selectedDestination = NavigationDestination.HOME,
                        focusedFavoriteId = it.selectedChannelId,
                        restoreChannelFocus = true,
                    )
                }
            }

            HomeAction.ChannelFocusRestored -> _uiState.update { it.copy(restoreChannelFocus = false) }

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
                if (error is CancellationException) throw error
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
                // Read once before exposing channels so a slow preferences read
                // cannot replace a channel the viewer has already chosen.
                val savedChannelId = try {
                    settingsRepository.settings.first().lastChannelId
                } catch (error: IOException) {
                    null
                }
                lastRememberedChannelId = savedChannelId
                val restoredChannelId = savedChannelId
                    ?.takeIf { id -> channels.any { it.id == id } }
                val restorePlayback = _uiState.value.selectedChannelId.isBlank() && restoredChannelId != null
                _uiState.update { state ->
                    val selectedId = state.selectedChannelId
                        .takeIf { id -> channels.any { channel -> channel.id == id } }
                        ?: restoredChannelId
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
                if (restorePlayback) {
                    playChannel(requireNotNull(restoredChannelId))
                }
                favoritesRepository.initialize(channels.map(Channel::id))
                refreshAndObserveEpg(channels)
            }
        }
    }

    private fun refreshAndObserveEpg(channels: List<Channel>) {
        epgObservationJob?.cancel()
        epgRefreshJob?.cancel()
        if (!hostActive) return

        epgRefreshJob = viewModelScope.launch {
            refreshEpg()
            while (true) {
                delay(EPG_REFRESH_INTERVAL_MILLIS)
                refreshEpg()
            }
        }

        epgObservationJob = viewModelScope.launch {
            clock.now.map { it.truncatedTo(java.time.temporal.ChronoUnit.HOURS) }
                .distinctUntilChanged()
                .flatMapLatest { hour ->
                    epgRepository.observePrograms(
                        channels.map(Channel::id),
                        hour.minus(Duration.ofHours(2)),
                        hour.plus(Duration.ofHours(25)),
                    )
                }.collect { programs ->
                _uiState.update { state ->
                    state.copy(programsByChannel = programs)
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
        playbackResolutionJob?.cancel()
        if (networkMonitor.status.value != NetworkStatus.Available) {
            networkRecoveryChannelId = channelId
            return
        }
        if (!hostActive) {
            resumeResolution = true
            return
        }
        resumeResolution = false
        _uiState.update {
            if (it.playerVisible && it.selectedChannelId == channelId) {
                it.copy(playbackState = PlaybackState.Resolving)
            } else {
                it
            }
        }
        playbackResolutionJob = viewModelScope.launch {
            runCatching {
                val channel = _uiState.value.channels.firstOrNull { it.id == channelId }
                    ?: channelRepository.getChannels().firstOrNull { it.id == channelId }
                    ?: error("Channel $channelId was not found")
                channelRepository.resolveStream(channel)
            }.onSuccess { resolution ->
                ensureActive()
                if (!hostActive) return@onSuccess
                when (resolution) {
                    is StreamResolution.Playable -> playbackService.play(resolution.source)
                    is StreamResolution.OfficialEmbed -> {
                        // Web players own their playback state. Remember the
                        // channel when opening their player instead.
                        rememberPlayedChannel(channelId)
                        ensureActive()
                        _uiState.update {
                            it.copy(playbackState = PlaybackState.OfficialEmbed(resolution.url))
                        }
                    }
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
                if (error is CancellationException) throw error
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
        playbackResolutionJob?.cancel()
        activeChannelId = channelId
        networkRecoveryChannelId = null
        automaticStreamRefreshAttempts = 0
        playbackService.stop()
        _uiState.update {
            it.copy(
                selectedChannelId = channelId,
                focusedFavoriteId = channelId,
                playerVisible = true,
                restoreChannelFocus = false,
                playbackState = PlaybackState.Resolving,
            )
        }
        resolveAndPlay(channelId)
    }

    private suspend fun rememberPlayedChannel(channelId: String) {
        if (channelId == lastRememberedChannelId) return
        try {
            // Persist during playback, rather than relying on a shutdown callback
            // that Android may never deliver when the TV or process turns off.
            settingsRepository.setLastChannel(channelId)
            lastRememberedChannelId = channelId
        } catch (error: IOException) {
            _uiState.update { it.copy(dataError = "Posledný kanál sa nepodarilo uložiť.") }
        }
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
        if (!hostActive || networkMonitor.status.value != NetworkStatus.Available ||
            !state.playerVisible || channelId.isBlank() || automaticStreamRefreshAttempts >= MAX_STREAM_REFRESHES) {
            return
        }

        automaticStreamRefreshAttempts += 1
        streamRefreshJob?.cancel()
        streamRefreshJob = viewModelScope.launch {
            delay(STREAM_REFRESH_DELAY_MILLIS)
            val current = _uiState.value
            if (hostActive && current.playerVisible && current.selectedChannelId == channelId) {
                resolveAndPlay(channelId)
            }
        }
    }

    private fun shouldRecoverPlayback(state: HomeUiState): Boolean = state.playerVisible &&
        state.playbackState !is PlaybackState.Paused &&
        state.playbackState !is PlaybackState.External &&
        state.playbackState !is PlaybackState.Unavailable

    /** A new connection gets a fresh provider URL and a fresh retry budget. */
    private fun recoverAfterNetworkOutage(): Boolean {
        val channelId = networkRecoveryChannelId ?: return false
        if (!hostActive || networkMonitor.status.value != NetworkStatus.Available) return false
        networkRecoveryChannelId = null
        val state = _uiState.value
        if (!state.playerVisible || activeChannelId != channelId) return false
        streamRefreshJob?.cancel()
        automaticStreamRefreshAttempts = 0
        resumeResolution = false
        // A broadcaster may return the same embed URL. Still reload its failed page.
        _uiState.update { it.copy(embedReloadGeneration = it.embedReloadGeneration + 1) }
        resolveAndPlay(channelId)
        return true
    }

    private companion object {
        const val EPG_REFRESH_INTERVAL_MILLIS = 5 * 60_000L
        const val STREAM_REFRESH_DELAY_MILLIS = 1_500L
        const val MAX_STREAM_REFRESHES = 2
    }
}
