package com.tellyo.app.feature.home

import androidx.lifecycle.ViewModelStore
import com.tellyo.app.core.epg.EpgRepository
import com.tellyo.app.core.model.Channel
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.model.StreamResolution
import com.tellyo.app.core.model.StreamSource
import com.tellyo.app.core.model.VideoQualityPreference
import com.tellyo.app.core.network.NetworkMonitor
import com.tellyo.app.core.network.NetworkStatus
import com.tellyo.app.core.player.PlaybackService
import com.tellyo.app.core.player.PlaybackState
import com.tellyo.app.core.provider.ChannelRepository
import com.tellyo.app.core.provider.PreviewTvProvider
import com.tellyo.app.core.settings.FavoriteSettings
import com.tellyo.app.core.settings.FavoritesRepository
import com.tellyo.app.core.settings.SettingsRepository
import com.tellyo.app.core.settings.TellyoSettings
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun reconnectRecoversEvenAfterPlaybackRetriesWereExhausted() = kotlinx.coroutines.test.runTest {
        val network = FakeNetworkMonitor()
        val channels = FakeChannelRepository()
        val playback = FakePlaybackService()
        val model = createViewModel(channels = channels, playback = playback, network = network)
        model.onAction(HomeAction.PlayChannel("joj"))
        repeat(3) {
            playback.emit(PlaybackState.Error("Retries exhausted $it"))
            testScheduler.advanceTimeBy(1_501)
        }
        assertEquals(3, channels.resolvedChannelIds.size)

        network.status.value = NetworkStatus.Unavailable
        assertFalse(model.uiState.value.isInternetAvailable)
        testScheduler.advanceTimeBy(120_000)
        assertEquals(3, channels.resolvedChannelIds.size)
        assertEquals(1, playback.pauseCalls)

        network.status.value = NetworkStatus.Available
        assertTrue(model.uiState.value.isInternetAvailable)
        assertEquals(4, channels.resolvedChannelIds.size)
        assertEquals(PlaybackState.OfficialEmbed("https://example.com/joj"), model.uiState.value.playbackState)
        assertEquals(1, model.uiState.value.embedReloadGeneration)
        playback.emit(PlaybackState.Error("New failure"))
        testScheduler.advanceTimeBy(1_501)
        assertEquals("Fresh connection resets provider refresh budget", 5, channels.resolvedChannelIds.size)
        viewModels.clear()
    }

    @Test
    fun offlineLaunchWaitsForInternetThenRestoresSavedChannel() {
        val network = FakeNetworkMonitor(NetworkStatus.Unavailable)
        val channels = FakeChannelRepository()
        val model = createViewModel(settings = FakeSettingsRepository("joj"), channels = channels, network = network)
        assertTrue(model.uiState.value.playerVisible)
        assertFalse(model.uiState.value.isInternetAvailable)
        model.onAction(HomeAction.RetryPlayback)
        assertTrue(channels.resolvedChannelIds.isEmpty())

        network.status.value = NetworkStatus.Available
        assertEquals(listOf("joj"), channels.resolvedChannelIds)
        network.status.value = NetworkStatus.Available
        assertEquals(1, channels.resolvedChannelIds.size)
    }

    @Test
    fun offlineHomeShowsErrorWithoutAutoplayOnReconnect() {
        val network = FakeNetworkMonitor(NetworkStatus.Unavailable)
        val channels = FakeChannelRepository()
        val model = createViewModel(channels = channels, network = network)
        assertFalse(model.uiState.value.isInternetAvailable)
        network.status.value = NetworkStatus.Available
        assertTrue(model.uiState.value.isInternetAvailable)
        assertFalse(model.uiState.value.playerVisible)
        assertTrue(channels.resolvedChannelIds.isEmpty())
    }

    @Test
    fun closingOfflinePlayerPreventsReopeningWhenInternetReturns() {
        val network = FakeNetworkMonitor()
        val channels = FakeChannelRepository()
        val model = createViewModel(channels = channels, network = network)
        model.onAction(HomeAction.PlayChannel("joj"))
        network.status.value = NetworkStatus.Unavailable
        model.onAction(HomeAction.ClosePlayer)
        network.status.value = NetworkStatus.Available
        assertEquals(listOf("joj"), channels.resolvedChannelIds)
        assertFalse(model.uiState.value.playerVisible)
    }

    @Test
    fun switchingChannelsOfflineRecoversOnlyLatestChoice() {
        val network = FakeNetworkMonitor()
        val channels = FakeChannelRepository()
        val model = createViewModel(channels = channels, network = network)
        model.onAction(HomeAction.PlayChannel("joj"))
        network.status.value = NetworkStatus.Unavailable
        model.onAction(HomeAction.PlayChannel("ta3"))
        assertEquals(listOf("joj"), channels.resolvedChannelIds)
        network.status.value = NetworkStatus.Available
        assertEquals(listOf("joj", "ta3"), channels.resolvedChannelIds)
        assertEquals("ta3", model.uiState.value.selectedChannelId)
    }

    @Test
    fun outageCancelsPendingResolutionAndReconnectGetsFreshSource() {
        val gate = CompletableDeferred<StreamResolution>()
        val network = FakeNetworkMonitor()
        var attempts = 0
        val channels = FakeChannelRepository(resolve = {
            if (++attempts == 1) gate.await() else StreamResolution.OfficialEmbed("https://example.com/fresh")
        })
        val model = createViewModel(channels = channels, network = network)
        model.onAction(HomeAction.PlayChannel("joj"))
        network.status.value = NetworkStatus.Unavailable
        gate.complete(StreamResolution.OfficialEmbed("https://example.com/stale"))
        assertFalse(model.uiState.value.playbackState is PlaybackState.OfficialEmbed)
        network.status.value = NetworkStatus.Available
        assertEquals(2, attempts)
        assertEquals(PlaybackState.OfficialEmbed("https://example.com/fresh"), model.uiState.value.playbackState)
    }

    @Test
    fun reconnectWhileBackgroundedWaitsUntilHostReturns() {
        val network = FakeNetworkMonitor()
        val channels = FakeChannelRepository()
        val playback = FakePlaybackService()
        val model = createViewModel(channels = channels, playback = playback, network = network)
        model.onAction(HomeAction.PlayChannel("joj"))
        playback.emit(PlaybackState.Playing("joj"))
        model.onHostStopped()
        playback.emit(PlaybackState.Paused("joj")) // Activity paused a previously playing stream.
        network.status.value = NetworkStatus.Unavailable
        network.status.value = NetworkStatus.Available
        assertEquals(1, channels.resolvedChannelIds.size)
        model.onHostStarted()
        assertEquals(2, channels.resolvedChannelIds.size)
        model.onHostStarted()
        assertEquals(2, channels.resolvedChannelIds.size)
    }

    @Test
    fun manuallyPausedPlaybackDoesNotAutoplayOnReconnect() {
        val network = FakeNetworkMonitor()
        val channels = FakeChannelRepository()
        val playback = FakePlaybackService()
        val model = createViewModel(channels = channels, playback = playback, network = network)
        model.onAction(HomeAction.PlayChannel("joj"))
        playback.emit(PlaybackState.Paused("joj"))
        network.status.value = NetworkStatus.Unavailable
        network.status.value = NetworkStatus.Available
        assertEquals(1, channels.resolvedChannelIds.size)
        assertEquals(PlaybackState.Paused("joj"), model.uiState.value.playbackState)
        model.onHostStopped()
        network.status.value = NetworkStatus.Unavailable
        network.status.value = NetworkStatus.Available
        model.onHostStarted()
        assertEquals(1, channels.resolvedChannelIds.size)
    }

    @Test
    fun backgroundResolutionCannotOpenPlayerAndResolvesAgainOnResume() {
        val gate = CompletableDeferred<StreamResolution>()
        val channels = FakeChannelRepository(resolve = { gate.await() })
        val model = createViewModel(channels = channels)
        model.onAction(HomeAction.PlayChannel("joj"))
        model.onHostStopped()
        gate.complete(StreamResolution.OfficialEmbed("https://example.com/resumed"))
        assertFalse(model.uiState.value.playbackState is PlaybackState.OfficialEmbed)
        assertEquals(1, channels.resolvedChannelIds.size)
        model.onHostStarted()
        assertEquals(2, channels.resolvedChannelIds.size)
        assertEquals(PlaybackState.OfficialEmbed("https://example.com/resumed"), model.uiState.value.playbackState)
    }

    @Test
    fun clockRollsProgrammeWindowAndBackgroundStopsRefreshes() = kotlinx.coroutines.test.runTest {
        val clock = com.tellyo.app.core.time.AppClock()
        val start = java.time.Instant.parse("2026-10-03T10:00:00Z")
        clock.tick(start)
        val epg = FakeEpgRepository()
        val model = createViewModel(epg = epg, clock = clock)
        assertEquals(1, epg.windows.size)
        clock.tick(start.plusSeconds(30))
        assertEquals(1, epg.windows.size)
        clock.tick(start.plusSeconds(26 * 3600))
        assertEquals(2, epg.windows.size)
        assertEquals(26 * 3600L, java.time.Duration.between(epg.windows[0].first, epg.windows[1].first).seconds)
        assertEquals(clock.now.value, model.uiState.value.now)
        model.onHostStopped()
        val refreshes = epg.refreshes
        testScheduler.advanceTimeBy(30 * 60_000L)
        assertEquals(refreshes, epg.refreshes)
        model.onHostStarted()
        assertEquals(refreshes + 1, epg.refreshes)
        viewModels.clear()
    }

    private val viewModels = ViewModelStore()
    private var nextViewModelId = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun selectingChannelUpdatesSelection() {
        val viewModel = createViewModel()

        viewModel.onAction(HomeAction.SelectChannel("joj"))

        assertEquals("joj", viewModel.uiState.value.selectedChannelId)
    }

    @Test
    fun selectingDestinationKeepsNavigationStateExplicit() {
        val viewModel = createViewModel()

        viewModel.onAction(HomeAction.SelectDestination(NavigationDestination.GUIDE))

        assertEquals(NavigationDestination.GUIDE, viewModel.uiState.value.selectedDestination)
    }

    @Test
    fun firstLaunchDoesNotStartPlayback() {
        val channels = FakeChannelRepository()
        val viewModel = createViewModel(channels = channels)

        assertEquals("stvr1", viewModel.uiState.value.selectedChannelId)
        assertFalse(viewModel.uiState.value.playerVisible)
        assertTrue(channels.resolvedChannelIds.isEmpty())
    }

    @Test
    fun reopeningAppRestoresLastPlayedChannelEvenAfterBrowsingAndClosingPlayer() {
        val settings = FakeSettingsRepository()
        val viewModel = createViewModel(settings = settings)
        viewModel.onAction(HomeAction.PlayChannel("joj"))
        viewModel.onAction(HomeAction.ClosePlayer)
        viewModel.onAction(HomeAction.FocusFavorite("ta3"))
        viewModel.onAction(HomeAction.SelectChannel("dvojka"))
        viewModels.clear()

        val channels = FakeChannelRepository()
        val reopened = createViewModel(settings = settings, channels = channels)

        assertEquals("joj", settings.state.value.lastChannelId)
        assertEquals("joj", reopened.uiState.value.selectedChannelId)
        assertEquals("joj", reopened.uiState.value.focusedFavoriteId)
        assertTrue(reopened.uiState.value.playerVisible)
        assertEquals(listOf("joj"), channels.resolvedChannelIds)
        assertEquals(PlaybackState.OfficialEmbed("https://example.com/joj"), reopened.uiState.value.playbackState)
    }

    @Test
    fun directPlaybackIsRememberedWhenPlayingStarts() {
        val resolution = CompletableDeferred<StreamResolution>()
        val settings = FakeSettingsRepository()
        val playback = FakePlaybackService()
        val viewModel = createViewModel(
            settings = settings,
            playback = playback,
            channels = FakeChannelRepository(resolve = { resolution.await() }),
        )
        viewModel.onAction(HomeAction.PlayChannel("ta3"))
        assertEquals(null, settings.state.value.lastChannelId)

        playback.emit(PlaybackState.Playing("ta3"))
        assertEquals("ta3", settings.state.value.lastChannelId)
        playback.emit(PlaybackState.Buffering)
        playback.emit(PlaybackState.Playing("ta3"))
        assertEquals(listOf("ta3"), settings.writtenChannelIds)
        viewModels.clear()

        val reopened = createViewModel(settings = settings)
        assertEquals("ta3", reopened.uiState.value.selectedChannelId)
        assertTrue(reopened.uiState.value.playerVisible)
    }

    @Test
    fun switchingChannelsRemembersLatestPlayback() {
        val settings = FakeSettingsRepository()
        val viewModel = createViewModel(settings = settings)
        viewModel.onAction(HomeAction.PlayChannel("joj"))
        viewModel.onAction(HomeAction.PlayChannel("ta3"))

        assertEquals("ta3", settings.state.value.lastChannelId)
        assertEquals(listOf("joj", "ta3"), settings.writtenChannelIds)
    }

    @Test
    fun unavailableChannelDoesNotReplaceLastPlayedChannel() {
        val settings = FakeSettingsRepository("joj")
        val viewModel = createViewModel(
            settings = settings,
            channels = FakeChannelRepository(resolve = { StreamResolution.Unavailable("Offline") }),
        )
        viewModel.onAction(HomeAction.PlayChannel("ta3"))
        viewModel.onAction(HomeAction.PlayChannel("removed"))

        assertEquals("joj", settings.state.value.lastChannelId)
        assertTrue(settings.writtenChannelIds.isEmpty())
    }

    @Test
    fun removedSavedChannelFallsBackToHomeWithoutAutoplay() {
        val channels = FakeChannelRepository()
        val viewModel = createViewModel(settings = FakeSettingsRepository("removed"), channels = channels)

        assertEquals("stvr1", viewModel.uiState.value.selectedChannelId)
        assertFalse(viewModel.uiState.value.playerVisible)
        assertTrue(channels.resolvedChannelIds.isEmpty())
    }

    @Test
    fun disabledSavedChannelFallsBackToEnabledChannel() {
        val channels = FakeChannelRepository(
            channels = PreviewTvProvider.previewChannels.map { it.copy(enabled = it.id != "joj") },
        )
        val viewModel = createViewModel(settings = FakeSettingsRepository("joj"), channels = channels)

        assertEquals("stvr1", viewModel.uiState.value.selectedChannelId)
        assertFalse(viewModel.uiState.value.playerVisible)
        assertTrue(channels.resolvedChannelIds.isEmpty())
    }

    @Test
    fun emptyCatalogDoesNotRestorePlayback() {
        val viewModel = createViewModel(
            settings = FakeSettingsRepository("joj"),
            channels = FakeChannelRepository(channels = emptyList()),
        )

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.playerVisible)
        assertEquals("", viewModel.uiState.value.selectedChannelId)
    }

    @Test
    fun delayedSettingsLoadRestoresOnlyAfterChannelsAreReady() {
        val settings = FakeSettingsRepository("joj")
        settings.readGate = CompletableDeferred()
        val viewModel = createViewModel(settings = settings)
        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.channels.isEmpty())

        settings.readGate?.complete(Unit)

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("joj", viewModel.uiState.value.selectedChannelId)
        assertTrue(viewModel.uiState.value.playerVisible)
    }

    @Test
    fun settingsReadFailureStillLoadsHome() {
        val settings = FakeSettingsRepository("joj")
        settings.readFailure = IOException("Read failed")
        val viewModel = createViewModel(settings = settings)

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("stvr1", viewModel.uiState.value.selectedChannelId)
        assertFalse(viewModel.uiState.value.playerVisible)
    }

    @Test
    fun settingsWriteFailureDoesNotPreventPlayback() {
        val settings = FakeSettingsRepository()
        settings.writeFailure = IOException("Disk full")
        val viewModel = createViewModel(settings = settings)
        viewModel.onAction(HomeAction.PlayChannel("joj"))

        assertTrue(viewModel.uiState.value.playerVisible)
        assertEquals(PlaybackState.OfficialEmbed("https://example.com/joj"), viewModel.uiState.value.playbackState)
        assertEquals(null, settings.state.value.lastChannelId)
    }

    @Test
    fun closingWhileResolvingDoesNotRememberOrReopenChannel() {
        val resolution = CompletableDeferred<StreamResolution>()
        val settings = FakeSettingsRepository()
        val viewModel = createViewModel(
            settings = settings,
            channels = FakeChannelRepository(resolve = { resolution.await() }),
        )
        viewModel.onAction(HomeAction.PlayChannel("joj"))
        viewModel.onAction(HomeAction.ClosePlayer)
        resolution.complete(StreamResolution.OfficialEmbed("https://example.com/joj"))

        assertFalse(viewModel.uiState.value.playerVisible)
        assertEquals(null, settings.state.value.lastChannelId)
        assertEquals(PlaybackState.Idle, viewModel.uiState.value.playbackState)
        assertEquals(NavigationDestination.HOME, viewModel.uiState.value.selectedDestination)
        assertEquals("joj", viewModel.uiState.value.focusedFavoriteId)
        assertTrue(viewModel.uiState.value.restoreChannelFocus)
    }

    @Test
    fun slowPreviousChannelCannotOverwriteLatestChannel() {
        val slowResolution = CompletableDeferred<StreamResolution>()
        val settings = FakeSettingsRepository()
        val viewModel = createViewModel(
            settings = settings,
            channels = FakeChannelRepository(resolve = { channel ->
                if (channel.id == "joj") slowResolution.await()
                else StreamResolution.OfficialEmbed("https://example.com/${channel.id}")
            }),
        )
        viewModel.onAction(HomeAction.PlayChannel("joj"))
        viewModel.onAction(HomeAction.PlayChannel("ta3"))
        slowResolution.complete(StreamResolution.OfficialEmbed("https://example.com/joj"))

        assertEquals("ta3", settings.state.value.lastChannelId)
        assertEquals(PlaybackState.OfficialEmbed("https://example.com/ta3"), viewModel.uiState.value.playbackState)
    }

    private fun createViewModel(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        channels: ChannelRepository = FakeChannelRepository(),
        playback: FakePlaybackService = FakePlaybackService(),
        epg: FakeEpgRepository = FakeEpgRepository(),
        clock: com.tellyo.app.core.time.AppClock = com.tellyo.app.core.time.AppClock(),
        network: FakeNetworkMonitor = FakeNetworkMonitor(),
    ) = HomeViewModel(
        channels,
        playback,
        epg,
        FakeFavoritesRepository(),
        settings,
        network,
        clock,
    ).also { viewModels.put("home-${nextViewModelId++}", it) }

    private class FakePlaybackService : PlaybackService {
        private val stateFlow = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
        override val state: StateFlow<PlaybackState> = stateFlow

        fun emit(state: PlaybackState) {
            stateFlow.value = state
        }

        override suspend fun play(source: StreamSource) {
            stateFlow.value = PlaybackState.Playing(source.channelId)
        }

        var pauseCalls = 0
        override fun pause() { pauseCalls++ }

        override fun stop() {
            stateFlow.value = PlaybackState.Idle
        }
    }

    private class FakeNetworkMonitor(initial: NetworkStatus = NetworkStatus.Available) : NetworkMonitor {
        override val status = MutableStateFlow(initial)
    }

    private class FakeChannelRepository(
        private val channels: List<Channel> = PreviewTvProvider.previewChannels,
        private val resolve: suspend (Channel) -> StreamResolution = {
            StreamResolution.OfficialEmbed("https://example.com/${it.id}")
        },
    ) : ChannelRepository {
        val resolvedChannelIds = mutableListOf<String>()

        override suspend fun getChannels() = channels

        override suspend fun resolveStream(channel: Channel): StreamResolution {
            resolvedChannelIds += channel.id
            return resolve(channel)
        }
    }

    private class FakeSettingsRepository(lastChannelId: String? = null) : SettingsRepository {
        val state = MutableStateFlow(TellyoSettings(lastChannelId = lastChannelId))
        val writtenChannelIds = mutableListOf<String>()
        var readGate: CompletableDeferred<Unit>? = null
        var readFailure: IOException? = null
        var writeFailure: IOException? = null
        override val settings = flow {
            readGate?.await()
            readFailure?.let { throw it }
            emitAll(state)
        }

        override suspend fun setLastChannel(id: String) {
            writeFailure?.let { throw it }
            writtenChannelIds += id
            state.value = state.value.copy(lastChannelId = id)
        }

        override suspend fun setReduceMotion(enabled: Boolean) = Unit

        override suspend fun setVideoQuality(preference: VideoQualityPreference) = Unit
    }

    private class FakeEpgRepository : EpgRepository {
        var refreshes = 0
        val windows = mutableListOf<Pair<java.time.Instant, java.time.Instant>>()
        override fun observePrograms(channelIds: List<String>, from: java.time.Instant, to: java.time.Instant): kotlinx.coroutines.flow.Flow<Map<String, List<Program>>> {
            windows.add(Pair(from, to))
            return flowOf(emptyMap())
        }
        override fun observePrograms(
            channelId: String,
            from: java.time.Instant,
            to: java.time.Instant,
        ) = flowOf<List<Program>>(emptyList())

        override suspend fun refresh() { refreshes++ }
    }

    private class FakeFavoritesRepository : FavoritesRepository {
        override val settings = flowOf(FavoriteSettings())

        override suspend fun initialize(channelIds: List<String>) = Unit

        override suspend fun toggle(channelId: String) = Unit

        override suspend fun move(channelId: String, direction: Int) = Unit
    }
}
