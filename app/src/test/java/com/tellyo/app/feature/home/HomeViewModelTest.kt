package com.tellyo.app.feature.home

import com.tellyo.app.core.epg.EpgRepository
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.model.StreamSource
import com.tellyo.app.core.player.PlaybackService
import com.tellyo.app.core.player.PlaybackState
import com.tellyo.app.core.provider.ChannelRepository
import com.tellyo.app.core.provider.DefaultChannelRepository
import com.tellyo.app.core.provider.PreviewTvProvider
import com.tellyo.app.core.provider.ProviderRegistry
import com.tellyo.app.core.settings.FavoriteSettings
import com.tellyo.app.core.settings.FavoritesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectingChannelUpdatesSelection() {
        val viewModel = HomeViewModel(
            previewRepository(),
            FakePlaybackService(),
            FakeEpgRepository(),
            FakeFavoritesRepository(),
        )

        viewModel.onAction(HomeAction.SelectChannel("joj"))

        assertEquals("joj", viewModel.uiState.value.selectedChannelId)
    }

    @Test
    fun selectingDestinationKeepsNavigationStateExplicit() {
        val viewModel = HomeViewModel(
            previewRepository(),
            FakePlaybackService(),
            FakeEpgRepository(),
            FakeFavoritesRepository(),
        )

        viewModel.onAction(HomeAction.SelectDestination(NavigationDestination.GUIDE))

        assertEquals(NavigationDestination.GUIDE, viewModel.uiState.value.selectedDestination)
    }

    private fun previewRepository(): ChannelRepository =
        DefaultChannelRepository(
            ProviderRegistry(mapOf("preview" to PreviewTvProvider())),
        )

    private class FakePlaybackService : PlaybackService {
        private val stateFlow = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
        override val state: StateFlow<PlaybackState> = stateFlow

        override suspend fun play(source: StreamSource) {
            stateFlow.value = PlaybackState.Playing(source.channelId)
        }

        override fun pause() = Unit

        override fun stop() {
            stateFlow.value = PlaybackState.Idle
        }
    }

    private class FakeEpgRepository : EpgRepository {
        override fun observePrograms(
            channelId: String,
            from: java.time.Instant,
            to: java.time.Instant,
        ) = flowOf<List<Program>>(emptyList())

        override suspend fun refresh() = Unit
    }

    private class FakeFavoritesRepository : FavoritesRepository {
        override val settings = flowOf(FavoriteSettings())

        override suspend fun initialize(channelIds: List<String>) = Unit

        override suspend fun toggle(channelId: String) = Unit

        override suspend fun move(channelId: String, direction: Int) = Unit
    }
}
