package com.tellyo.app.feature.settings

import androidx.lifecycle.ViewModelStore
import com.tellyo.app.core.model.VideoQualityPreference
import com.tellyo.app.core.settings.SettingsRepository
import com.tellyo.app.core.settings.TellyoSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val store = ViewModelStore()
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun rapidQualityActionsAreAppliedInOrderWhileFirstWriteIsPending() {
        val repository = DelayedSettings()
        val model = SettingsViewModel(repository).also { store.put("settings", it) }
        repeat(3) { model.onAction(SettingsAction.CycleVideoQuality) }
        repository.gate.complete(Unit)
        assertEquals(listOf(VideoQualityPreference.HIGH, VideoQualityPreference.STABLE, VideoQualityPreference.AUTO),
            repository.qualityWrites)
        assertEquals(VideoQualityPreference.AUTO, model.uiState.value.videoQuality)
    }

    @Test fun rapidMotionTogglesDoNotLoseTheSecondToggle() {
        val repository = DelayedSettings()
        val model = SettingsViewModel(repository).also { store.put("settings", it) }
        repeat(2) { model.onAction(SettingsAction.ToggleReduceMotion) }
        repository.gate.complete(Unit)
        assertEquals(listOf(true, false), repository.motionWrites)
        assertEquals(false, model.uiState.value.reduceMotion)
    }

    @Test fun adFilterCanBeDisabledAndResetToEnabled() {
        val repository = DelayedSettings()
        repository.gate.complete(Unit)
        val model = SettingsViewModel(repository).also { store.put("settings", it) }
        assertEquals(true, model.uiState.value.blockEmbedAds)
        model.onAction(SettingsAction.ToggleBlockEmbedAds)
        assertEquals(false, model.uiState.value.blockEmbedAds)
        assertEquals(false, repository.settings.value.blockEmbedAds)
        model.onAction(SettingsAction.Reset)
        assertEquals(true, model.uiState.value.blockEmbedAds)
        assertEquals(true, repository.settings.value.blockEmbedAds)
    }

    private class DelayedSettings : SettingsRepository {
        val gate = CompletableDeferred<Unit>()
        override val settings = MutableStateFlow(TellyoSettings())
        val qualityWrites = mutableListOf<VideoQualityPreference>()
        val motionWrites = mutableListOf<Boolean>()
        override suspend fun setLastChannel(id: String) = Unit
        override suspend fun setBlockEmbedAds(enabled: Boolean) {
            gate.await()
            settings.value = settings.value.copy(blockEmbedAds = enabled)
        }
        override suspend fun setReduceMotion(enabled: Boolean) {
            gate.await()
            motionWrites += enabled
            settings.value = settings.value.copy(reduceMotion = enabled)
        }
        override suspend fun setVideoQuality(preference: VideoQualityPreference) {
            gate.await()
            qualityWrites += preference
            settings.value = settings.value.copy(videoQuality = preference)
        }
    }
}
