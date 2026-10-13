package com.tellyo.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tellyo.app.core.model.VideoQualityPreference
import com.tellyo.app.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SettingsUiState(
    val reduceMotion: Boolean = false,
    val videoQuality: VideoQualityPreference = VideoQualityPreference.AUTO,
    val blockEmbedAds: Boolean = true,
    val isLoading: Boolean = true,
)

sealed interface SettingsAction {
    data object ToggleReduceMotion : SettingsAction
    data object CycleVideoQuality : SettingsAction
    data object ToggleBlockEmbedAds : SettingsAction
    data object Reset : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val actionMutex = Mutex()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        reduceMotion = settings.reduceMotion,
                        videoQuality = settings.videoQuality,
                        blockEmbedAds = settings.blockEmbedAds,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onAction(action: SettingsAction) {
        viewModelScope.launch {
            actionMutex.withLock {
                // Read the committed preferences after the preceding action;
                // the UI flow may not yet have delivered that write.
                val current = settingsRepository.settings.first()
                when (action) {
                    SettingsAction.ToggleReduceMotion -> settingsRepository.setReduceMotion(!current.reduceMotion)
                    SettingsAction.CycleVideoQuality -> settingsRepository.setVideoQuality(nextVideoQuality(current.videoQuality))
                    SettingsAction.ToggleBlockEmbedAds -> settingsRepository.setBlockEmbedAds(!current.blockEmbedAds)
                    SettingsAction.Reset -> {
                        settingsRepository.setReduceMotion(false)
                        settingsRepository.setVideoQuality(VideoQualityPreference.AUTO)
                        settingsRepository.setBlockEmbedAds(true)
                    }
                }
            }
        }
    }

    private fun nextVideoQuality(current: VideoQualityPreference): VideoQualityPreference = when (current) {
        VideoQualityPreference.AUTO -> VideoQualityPreference.HIGH
        VideoQualityPreference.HIGH -> VideoQualityPreference.STABLE
        VideoQualityPreference.STABLE -> VideoQualityPreference.AUTO
    }
}
