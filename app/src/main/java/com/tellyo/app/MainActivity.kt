package com.tellyo.app

import android.content.Intent
import android.os.Bundle
import android.annotation.SuppressLint
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tellyo.app.core.player.PlaybackState
import com.tellyo.app.core.player.Media3PlaybackClient
import com.tellyo.app.core.time.AppClock
import com.tellyo.app.core.ui.components.NetworkStatusOverlay
import com.tellyo.app.core.ui.theme.LocalTellyoReduceMotion
import com.tellyo.app.core.ui.theme.TellyoTheme
import com.tellyo.app.feature.home.HomeScreen
import com.tellyo.app.feature.home.HomeViewModel
import com.tellyo.app.feature.guide.GuideViewModel
import com.tellyo.app.feature.guide.GuideAction
import com.tellyo.app.feature.guide.GuideUiState
import com.tellyo.app.feature.diagnostics.DiagnosticsUiState
import com.tellyo.app.feature.home.NavigationDestination
import com.tellyo.app.feature.diagnostics.DiagnosticsViewModel
import com.tellyo.app.feature.player.PlayerScreen
import com.tellyo.app.feature.player.OfficialEmbedPlayerScreen
import com.tellyo.app.feature.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private val guideViewModel: GuideViewModel by viewModels()
    private val diagnosticsViewModel: DiagnosticsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var playbackClient: Media3PlaybackClient
    @Inject lateinit var clock: AppClock

    private var playerBackKeyConsumed = false

    override fun onStart() {
        super.onStart()
        clock.start()
        playbackClient.onHostStarted()
        homeViewModel.onHostStarted()
    }

    override fun onStop() {
        homeViewModel.onHostStopped()
        playbackClient.onHostStopped()
        clock.stop()
        super.onStop()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The Android TV emulator can deliver the desktop Escape key as
        // KEYCODE_ESCAPE, KEYCODE_BACK, or KEYCODE_DEL depending on its
        // keyboard profile. PlayerView may own focus, so handle the complete
        // key press at the Activity boundary before it reaches the media view.
        val isPlayerBackKey = event.keyCode == KeyEvent.KEYCODE_ESCAPE ||
            event.keyCode == KeyEvent.KEYCODE_BACK ||
            event.keyCode == KeyEvent.KEYCODE_DEL
        val shouldConsume = playerBackKeyConsumed ||
            (isPlayerBackKey && homeViewModel.uiState.value.playerVisible)

        if (shouldConsume) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (!playerBackKeyConsumed && event.repeatCount == 0) {
                        homeViewModel.onAction(
                            com.tellyo.app.feature.home.HomeAction.ClosePlayer,
                        )
                    }
                    playerBackKeyConsumed = true
                }

                KeyEvent.ACTION_UP -> playerBackKeyConsumed = false
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
            val guideUiState = if (!uiState.playerVisible && uiState.selectedDestination == NavigationDestination.GUIDE) {
                val state by guideViewModel.uiState.collectAsStateWithLifecycle()
                state
            } else remember { GuideUiState() }
            val diagnosticsUiState = if (!uiState.playerVisible && uiState.selectedDestination == NavigationDestination.DIAGNOSTICS) {
                val state by diagnosticsViewModel.uiState.collectAsStateWithLifecycle()
                state
            } else remember { DiagnosticsUiState() }
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            TellyoTheme {
                LaunchedEffect(settingsUiState.videoQuality) {
                    playbackClient.setVideoQuality(settingsUiState.videoQuality)
                }
                CompositionLocalProvider(
                    LocalTellyoReduceMotion provides settingsUiState.reduceMotion,
                ) {
                    NetworkStatusOverlay(isInternetAvailable = uiState.isInternetAvailable) {
                        if (uiState.playerVisible) {
                            val player by playbackClient.player.collectAsStateWithLifecycle()
                            val officialEmbed = uiState.playbackState as? PlaybackState.OfficialEmbed
                            if (officialEmbed != null) {
                                OfficialEmbedPlayerScreen(
                                    url = officialEmbed.url,
                                    reloadGeneration = uiState.embedReloadGeneration,
                                    onBack = {
                                        homeViewModel.onAction(
                                            com.tellyo.app.feature.home.HomeAction.ClosePlayer,
                                        )
                                    },
                                )
                            } else PlayerScreen(
                                channelName = uiState.channels.firstOrNull {
                                    it.id == uiState.selectedChannelId
                                }?.name ?: "Tellyo",
                                programTitle = uiState.programsByChannel[uiState.selectedChannelId]
                                    .orEmpty()
                                    .firstOrNull { program ->
                                        program.start <= uiState.now && program.end.isAfter(uiState.now)
                                    }
                                    ?.title,
                                player = player,
                                playbackState = uiState.playbackState,
                                isInternetAvailable = uiState.isInternetAvailable,
                                onBack = { homeViewModel.onAction(com.tellyo.app.feature.home.HomeAction.ClosePlayer) },
                                onRetry = { homeViewModel.onAction(com.tellyo.app.feature.home.HomeAction.RetryPlayback) },
                                onOpenExternal = {
                                    (uiState.playbackState as? PlaybackState.External)?.deepLink?.let { uri ->
                                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                    }
                                },
                                onOpenGuide = {
                                    homeViewModel.onAction(
                                        com.tellyo.app.feature.home.HomeAction.ClosePlayer,
                                    )
                                    homeViewModel.onAction(
                                        com.tellyo.app.feature.home.HomeAction.SelectDestination(
                                            com.tellyo.app.feature.home.NavigationDestination.GUIDE,
                                        ),
                                    )
                                },
                            )
                        } else {
                            HomeScreen(
                                uiState = uiState,
                                onAction = homeViewModel::onAction,
                                guideUiState = guideUiState,
                                onGuideAction = { action ->
                                    if (action is GuideAction.OpenChannel) {
                                        homeViewModel.onAction(
                                            com.tellyo.app.feature.home.HomeAction.PlayChannel(action.channelId),
                                        )
                                    } else {
                                        guideViewModel.onAction(action)
                                    }
                                },
                                diagnosticsUiState = diagnosticsUiState,
                                onDiagnosticsAction = { diagnosticsViewModel.onAction(it) },
                                settingsUiState = settingsUiState,
                                onSettingsAction = settingsViewModel::onAction,
                            )
                        }
                    }
                }
            }
        }
    }
}
