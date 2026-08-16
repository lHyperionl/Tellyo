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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val reduceMotion: Boolean = false,
    val videoQuality: VideoQualityPreference = VideoQualityPreference.AUTO,
    val isLoading: Boolean = true,
)

sealed interface SettingsAction {
    data object ToggleReduceMotion : SettingsAction
    data object CycleVideoQuality : SettingsAction
    data object Reset : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        reduceMotion = settings.reduceMotion,
                        videoQuality = settings.videoQuality,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.ToggleReduceMotion -> viewModelScope.launch {
                settingsRepository.setReduceMotion(!_uiState.value.reduceMotion)
            }
            SettingsAction.CycleVideoQuality -> viewModelScope.launch {
                settingsRepository.setVideoQuality(nextVideoQuality(_uiState.value.videoQuality))
            }
            SettingsAction.Reset -> viewModelScope.launch {
                settingsRepository.setReduceMotion(false)
                settingsRepository.setVideoQuality(VideoQualityPreference.AUTO)
            }
        }
    }

    private fun nextVideoQuality(current: VideoQualityPreference): VideoQualityPreference = when (current) {
        VideoQualityPreference.AUTO -> VideoQualityPreference.HIGH
        VideoQualityPreference.HIGH -> VideoQualityPreference.STABLE
        VideoQualityPreference.STABLE -> VideoQualityPreference.AUTO
    }
}
