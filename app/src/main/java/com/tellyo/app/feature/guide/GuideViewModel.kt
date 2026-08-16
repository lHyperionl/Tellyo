package com.tellyo.app.feature.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tellyo.app.core.epg.EpgRepository
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.provider.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GuideRowState(
    val channel: Channel,
    val programs: List<Program> = emptyList(),
)

data class GuideUiState(
    val windowStart: Instant = initialWindowStart(),
    val windowHours: Int = 6,
    val now: Instant = Instant.now(),
    val rows: List<GuideRowState> = emptyList(),
    val focusedProgramId: String? = null,
    val selectedProgram: Program? = null,
    val isRefreshing: Boolean = true,
    val error: String? = null,
)

sealed interface GuideAction {
    data class FocusProgram(val programId: String) : GuideAction
    data class SelectProgram(val programId: String) : GuideAction
    data class OpenChannel(val channelId: String) : GuideAction
    data class ShiftWindow(val hours: Long) : GuideAction
    data object DismissProgram : GuideAction
    data object Refresh : GuideAction
}

@HiltViewModel
class GuideViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GuideUiState())
    val uiState: StateFlow<GuideUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null
    private var refreshJob: Job? = null
    private var guideChannels: List<Channel> = emptyList()

    init {
        refreshAndObserve()
    }

    fun onAction(action: GuideAction) {
        when (action) {
            is GuideAction.FocusProgram -> _uiState.update { it.copy(focusedProgramId = action.programId) }
            is GuideAction.SelectProgram -> _uiState.update { state ->
                state.copy(
                    focusedProgramId = action.programId,
                    selectedProgram = state.rows.flatMap { it.programs }
                        .firstOrNull { program -> program.id == action.programId },
                )
            }
            is GuideAction.OpenChannel -> _uiState.update { it.copy(selectedProgram = null) }
            is GuideAction.ShiftWindow -> _uiState.update {
                it.copy(
                    windowStart = it.windowStart.plus(Duration.ofHours(action.hours)),
                    now = Instant.now(),
                    selectedProgram = null,
                )
            }.also { observeWindow() }
            GuideAction.DismissProgram -> _uiState.update { it.copy(selectedProgram = null) }
            GuideAction.Refresh -> refreshAndObserve()
        }
    }

    private fun refreshAndObserve() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            refreshEpg()
            observeWindow()
            while (true) {
                delay(EPG_REFRESH_INTERVAL_MILLIS)
                refreshEpg()
            }
        }
    }

    private suspend fun refreshEpg() {
        try {
            epgRepository.refresh()
            _uiState.update { it.copy(error = null) }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.update { it.copy(error = error.message ?: "EPG refresh failed") }
        }
    }

    private fun observeWindow() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            if (guideChannels.isEmpty()) {
                guideChannels = channelRepository.getChannels()
                    .filter { it.enabled }
                    .sortedBy { it.number }
            }

            val windowStart = _uiState.value.windowStart
            val windowEnd = windowStart.plus(Duration.ofHours(_uiState.value.windowHours.toLong()))
            _uiState.update {
                it.copy(
                    rows = guideChannels.map { channel -> GuideRowState(channel) },
                    isRefreshing = true,
                )
            }

            guideChannels.forEach { channel ->
                launch {
                    epgRepository.observePrograms(channel.id, windowStart, windowEnd).collect { programs ->
                        _uiState.update { state ->
                            state.copy(
                                rows = state.rows.map { row ->
                                    if (row.channel.id == channel.id) row.copy(programs = programs) else row
                                },
                                isRefreshing = false,
                            )
                        }
                    }
                }
            }
        }
    }

}

private fun initialWindowStart(): Instant =
    Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(1))

private const val EPG_REFRESH_INTERVAL_MILLIS = 60_000L
