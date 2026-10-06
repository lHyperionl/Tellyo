package com.tellyo.app.feature.diagnostics

import androidx.lifecycle.ViewModelStore
import com.tellyo.app.core.epg.EpgRepository
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.model.StreamResolution
import com.tellyo.app.core.model.StreamSource
import com.tellyo.app.core.network.*
import com.tellyo.app.core.player.*
import com.tellyo.app.core.provider.ChannelRepository
import com.tellyo.app.core.provider.PreviewTvProvider
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {
    private val store = ViewModelStore()
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test
    fun rapidActionsRunOneScanAndOneEpgRefreshUntilCompletion() = runTest {
        var probes = 0
        var refreshes = 0
        val probeGate = CompletableDeferred<Unit>()
        val refreshGate = CompletableDeferred<Unit>()
        val model = DiagnosticsViewModel(
            object : ChannelRepository {
                override suspend fun getChannels() = PreviewTvProvider.previewChannels.take(1)
                override suspend fun resolveStream(channel: Channel): StreamResolution {
                    probes++
                    probeGate.await()
                    return StreamResolution.Unavailable("test")
                }
            },
            object : EpgRepository {
                override fun observePrograms(channelId: String, from: Instant, to: Instant) = flowOf(emptyList<Program>())
                override suspend fun refresh() { refreshes++; refreshGate.await() }
            },
            object : PlaybackService {
                override val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
                override suspend fun play(source: StreamSource) = Unit
                override fun pause() = Unit
                override fun stop() = Unit
            },
            PlaybackTelemetryStore(),
            object : NetworkMonitor { override val status = MutableStateFlow(NetworkStatus.Available) },
            object : StreamHealthChecker {
                override suspend fun check(source: StreamSource): StreamHealthResult = error("No playable route in this test")
            },
        ).also { store.put("diagnostics", it) }
        repeat(2) { model.onAction(DiagnosticsAction.CheckChannels); model.onAction(DiagnosticsAction.RefreshEpg) }
        assertEquals(1, probes)
        assertEquals(1, refreshes)
        assertTrue(model.uiState.value.isCheckingChannels)
        assertEquals(EpgRefreshStatus.Refreshing, model.uiState.value.epgStatus)
        probeGate.complete(Unit)
        refreshGate.complete(Unit)
        assertFalse(model.uiState.value.isCheckingChannels)
        assertEquals(EpgRefreshStatus.Updated, model.uiState.value.epgStatus)
        model.onAction(DiagnosticsAction.CheckChannels)
        assertEquals(2, probes)
    }
}
