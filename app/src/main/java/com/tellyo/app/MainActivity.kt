package com.tellyo.app

import android.content.Intent
import android.os.Bundle
import android.annotation.SuppressLint
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.tellyo.app.core.player.PlaybackState
import com.tellyo.app.core.player.Media3PlaybackClient
import com.tellyo.app.core.ui.theme.LocalTellyoReduceMotion
import com.tellyo.app.core.ui.theme.TellyoTheme
import com.tellyo.app.feature.home.HomeScreen
import com.tellyo.app.feature.home.HomeViewModel
import com.tellyo.app.feature.guide.GuideViewModel
import com.tellyo.app.feature.guide.GuideAction
import com.tellyo.app.feature.diagnostics.DiagnosticsViewModel
import com.tellyo.app.feature.player.PlayerScreen
import com.tellyo.app.feature.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private val guideViewModel: GuideViewModel by viewModels()
    private val diagnosticsViewModel: DiagnosticsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var playbackClient: Media3PlaybackClient

    private var playerBackKeyConsumed = false

    override fun onStart() {
        super.onStart()
        playbackClient.onHostStarted()
    }

    override fun onStop() {
        playbackClient.onHostStopped()
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by homeViewModel.uiState.collectAsState()
            val guideUiState by guideViewModel.uiState.collectAsState()
            val diagnosticsUiState by diagnosticsViewModel.uiState.collectAsState()
            val settingsUiState by settingsViewModel.uiState.collectAsState()
            val player by playbackClient.player.collectAsState()
            TellyoTheme {
                LaunchedEffect(settingsUiState.videoQuality) {
                    playbackClient.setVideoQuality(settingsUiState.videoQuality)
                }
                CompositionLocalProvider(
                    LocalTellyoReduceMotion provides settingsUiState.reduceMotion,
                ) {
                    if (uiState.playerVisible) {
                        PlayerScreen(
                            channelName = uiState.channels.firstOrNull {
                                it.id == uiState.selectedChannelId
                            }?.name ?: "Tellyo",
                            player = player,
                            playbackState = uiState.playbackState,
                            onBack = { homeViewModel.onAction(com.tellyo.app.feature.home.HomeAction.ClosePlayer) },
                            onRetry = { homeViewModel.onAction(com.tellyo.app.feature.home.HomeAction.RetryPlayback) },
                            onOpenExternal = {
                                (uiState.playbackState as? PlaybackState.External)?.deepLink?.let { uri ->
                                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                }
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
                            onDiagnosticsAction = diagnosticsViewModel::onAction,
                            settingsUiState = settingsUiState,
                            onSettingsAction = settingsViewModel::onAction,
                        )
                    }
                }
            }
        }
    }
}
