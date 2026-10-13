package com.tellyo.app.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tellyo.app.core.update.AppUpdate
import com.tellyo.app.core.update.UpdateDownloader
import com.tellyo.app.core.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UpdatePhase { AVAILABLE, DOWNLOADING, READY }
enum class UpdateError { DOWNLOAD, INSTALL }

data class UpdateUiState(
    val update: AppUpdate? = null,
    val phase: UpdatePhase = UpdatePhase.AVAILABLE,
    val progress: Float = 0f,
    val apk: File? = null,
    val error: UpdateError? = null,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
    private val downloader: UpdateDownloader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState = _uiState.asStateFlow()
    private var checkedOnStartup = false
    private var downloadJob: Job? = null

    /** Invoked after the first Activity frame, once across configuration changes. */
    fun checkOnStartup() {
        if (checkedOnStartup) return
        checkedOnStartup = true
        viewModelScope.launch {
            try {
                repository.checkForUpdate()?.let { _uiState.value = UpdateUiState(update = it) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Offline, rate limited, missing release, or invalid metadata: keep watching TV.
            }
        }
    }

    fun downloadUpdate() {
        val update = _uiState.value.update ?: return
        if (_uiState.value.phase != UpdatePhase.AVAILABLE) return
        _uiState.update { it.copy(phase = UpdatePhase.DOWNLOADING, progress = 0f, error = null) }
        downloadJob = viewModelScope.launch {
            try {
                val apk = downloader.download(update) { progress ->
                    _uiState.update { state ->
                        if (state.update == update && state.phase == UpdatePhase.DOWNLOADING)
                            state.copy(progress = progress) else state
                    }
                }
                _uiState.update { it.copy(phase = UpdatePhase.READY, apk = apk, progress = 1f) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(phase = UpdatePhase.AVAILABLE, error = UpdateError.DOWNLOAD) }
            }
        }
    }

    fun dismiss() {
        downloadJob?.cancel()
        _uiState.value = UpdateUiState()
    }

    fun installFailed() {
        _uiState.update { it.copy(error = UpdateError.INSTALL) }
    }
}
