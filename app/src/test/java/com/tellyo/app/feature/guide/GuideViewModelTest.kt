package com.tellyo.app.feature.guide

import androidx.lifecycle.ViewModelStore
import com.tellyo.app.core.epg.EpgRepository
import com.tellyo.app.core.model.Program
import com.tellyo.app.core.provider.DefaultChannelRepository
import com.tellyo.app.core.provider.InMemoryChannelCatalogRepository
import com.tellyo.app.core.provider.PreviewTvProvider
import com.tellyo.app.core.provider.ProviderRegistry
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuideViewModelTest {
    @Test
    fun currentTimeAdvancesWithoutUserActionOrNetworkRefresh() {
        val clock = com.tellyo.app.core.time.AppClock()
        val epg = CachedEpg()
        val viewModel = GuideViewModel(
            DefaultChannelRepository(ProviderRegistry(mapOf("preview" to PreviewTvProvider())),
                InMemoryChannelCatalogRepository(PreviewTvProvider.previewChannels)), epg, clock,
        ).also { store.put("clock-guide", it) }
        val later = clock.now.value.plusSeconds(600)
        clock.tick(later)
        assertEquals(later, viewModel.uiState.value.now)
        assertEquals(0, epg.refreshes)
        assertEquals(1, epg.batchObservations)
    }

    private val store = ViewModelStore()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun cachedGuideIsVisibleWithoutNetworkRefreshAndRemainsUsableDuringManualRefresh() = runTest {
        val epg = CachedEpg()
        val viewModel = newViewModel(epg)

        assertEquals(0, epg.refreshes)
        assertEquals(1, epg.batchObservations)
        assertEquals(epg.program, viewModel.uiState.value.rows.first().programs.single())
        assertFalse(viewModel.uiState.value.isRefreshing)

        viewModel.onAction(GuideAction.Refresh)
        assertTrue(viewModel.uiState.value.isRefreshing)
        viewModel.onAction(GuideAction.SelectProgram(epg.program.id))
        assertEquals(epg.program, viewModel.uiState.value.selectedProgram)
        viewModel.onAction(GuideAction.ShiftWindow(6))
        assertEquals(2, epg.batchObservations)
        assertTrue(viewModel.uiState.value.isRefreshing)
        epg.refreshGate.complete(Unit)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun emptyChannelListCompletesLoadingWithoutStartingRefresh() {
        val epg = CachedEpg()
        val viewModel = GuideViewModel(
            DefaultChannelRepository(ProviderRegistry(), InMemoryChannelCatalogRepository(emptyList())),
            epg,
        )
        store.put("guide", viewModel)
        assertTrue(viewModel.uiState.value.rows.isEmpty())
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(0, epg.refreshes)
    }

    private fun newViewModel(epg: EpgRepository): GuideViewModel = GuideViewModel(
        DefaultChannelRepository(
            ProviderRegistry(mapOf("preview" to PreviewTvProvider())),
            InMemoryChannelCatalogRepository(PreviewTvProvider.previewChannels),
        ),
        epg,
    ).also { store.put("guide", it) }

    private class CachedEpg : EpgRepository {
        var refreshes = 0
        var batchObservations = 0
        val refreshGate = CompletableDeferred<Unit>()
        val program = Program(
            id = "cached-show",
            channelId = PreviewTvProvider.previewChannels.first().id,
            title = "Cached programme",
            description = null,
            start = Instant.now().minus(Duration.ofMinutes(10)),
            end = Instant.now().plus(Duration.ofMinutes(30)),
            category = null,
        )

        override fun observePrograms(channelId: String, from: Instant, to: Instant): Flow<List<Program>> =
            error("Guide should observe the complete window in one batch")

        override fun observePrograms(channelIds: List<String>, from: Instant, to: Instant): Flow<Map<String, List<Program>>> {
            batchObservations++
            return flowOf(mapOf(program.channelId to listOf(program)))
        }

        override suspend fun refresh() {
            refreshes++
            refreshGate.await()
        }
    }
}
