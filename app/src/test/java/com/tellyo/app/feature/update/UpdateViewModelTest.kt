package com.tellyo.app.feature.update

import androidx.lifecycle.ViewModelStore
import com.tellyo.app.core.update.AppUpdate
import com.tellyo.app.core.update.UpdateDownloader
import com.tellyo.app.core.update.UpdateRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val store = ViewModelStore()
    private val update = AppUpdate(1001, "0.1.1001", "https://github.com/example/app.apk", "a".repeat(64), 4)
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun checksOnceAcrossActivityRecreationAndDoesNotDownloadWithoutConsent() {
        var checks = 0
        var downloads = 0
        val model = model(repository { checks++; update }, download { downloads++; File("update.apk") })
        repeat(3) { model.checkOnStartup() }
        assertEquals(1, checks)
        assertEquals(0, downloads)
        assertEquals(update, model.uiState.value.update)
    }

    @Test fun startupNetworkFailureStaysQuiet() {
        val model = model(repository { throw IOException("offline") })
        model.checkOnStartup()
        assertNull(model.uiState.value.update)
        assertNull(model.uiState.value.error)
    }

    @Test fun laterDismissesPromptUntilNextLaunch() {
        val model = model()
        model.checkOnStartup()
        model.dismiss()
        model.checkOnStartup()
        assertNull(model.uiState.value.update)
    }

    @Test fun acceptedDownloadBecomesReadyForInstaller() {
        val model = model()
        model.checkOnStartup()
        model.downloadUpdate()
        assertEquals(UpdatePhase.READY, model.uiState.value.phase)
        assertEquals(File("update.apk"), model.uiState.value.apk)
    }

    @Test fun failedDownloadCanBeRetried() {
        var attempts = 0
        val model = model(downloader = download {
            if (++attempts == 1) throw IOException("interrupted")
            File("update.apk")
        })
        model.checkOnStartup()
        model.downloadUpdate()
        assertEquals(UpdateError.DOWNLOAD, model.uiState.value.error)
        model.downloadUpdate()
        assertEquals(UpdatePhase.READY, model.uiState.value.phase)
        assertNull(model.uiState.value.error)
    }

    @Test fun duplicateAcceptDoesNotStartAnotherDownloadAndCancelDoesNotRestorePrompt() {
        val gate = CompletableDeferred<Unit>()
        var downloads = 0
        val model = model(downloader = download { downloads++; gate.await(); File("update.apk") })
        model.checkOnStartup()
        repeat(2) { model.downloadUpdate() }
        assertEquals(1, downloads)
        model.dismiss()
        gate.complete(Unit)
        assertNull(model.uiState.value.update)
        assertNull(model.uiState.value.error)
    }

    private fun model(repo: UpdateRepository = repository { update },
        downloader: UpdateDownloader = download { File("update.apk") }) =
        UpdateViewModel(repo, downloader).also { store.put("update", it) }

    private fun repository(check: suspend () -> AppUpdate?) = object : UpdateRepository {
        override suspend fun checkForUpdate() = check()
    }

    private fun download(block: suspend () -> File) = object : UpdateDownloader {
        override suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File {
            onProgress(0.5f)
            return block()
        }
    }
}
