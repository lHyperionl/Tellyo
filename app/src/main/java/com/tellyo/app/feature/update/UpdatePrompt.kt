package com.tellyo.app.feature.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tellyo.app.core.ui.components.TellyoButton
import com.tellyo.app.core.ui.components.TellyoProgressBar
import com.tellyo.app.core.ui.theme.TellyoColors
import java.io.File

@Composable
fun UpdatePromptHost(viewModel: UpdateViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val apk = viewModel.uiState.value.apk
        if (apk != null && canInstallUpdates(context)) {
            launchInstaller(context, apk, viewModel::installFailed)
        } else viewModel.installFailed()
    }
    UpdatePrompt(
        state = state,
        onDownload = viewModel::downloadUpdate,
        onDismiss = viewModel::dismiss,
        onInstall = {
            state.apk?.let { apk ->
                if (canInstallUpdates(context)) {
                    launchInstaller(context, apk, viewModel::installFailed)
                } else {
                    try {
                        permissionLauncher.launch(Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        ))
                    } catch (_: Exception) {
                        viewModel.installFailed()
                    }
                }
            }
        },
    )
}

private fun canInstallUpdates(context: Context): Boolean =
    Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

private fun launchInstaller(context: Context, apk: File, onFailure: () -> Unit) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("Tellyo update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    } catch (_: Exception) {
        onFailure()
    }
}

@Composable
fun UpdatePrompt(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val update = state.update ?: return
    val actionFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LaunchedEffect(state.phase) {
            withFrameNanos { }
            actionFocus.requestFocus()
        }
        Column(
            modifier = Modifier.width(490.dp)
                .background(TellyoColors.SurfaceElevated, RoundedCornerShape(18.dp))
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Aktualizácia Tellyo", fontSize = 25.sp, fontWeight = FontWeight.SemiBold,
                color = TellyoColors.TextPrimary)
            Text("K dispozícii je verzia ${update.versionName}.", fontSize = 17.sp,
                color = TellyoColors.TextPrimary)
            Text(
                when (state.phase) {
                    UpdatePhase.AVAILABLE -> "Chcete aktualizovať aplikáciu teraz?"
                    UpdatePhase.DOWNLOADING -> "Sťahovanie aktualizácie… ${(state.progress * 100).toInt()} %"
                    UpdatePhase.READY -> "Aktualizácia je pripravená. Inštaláciu potvrďte na ďalšej obrazovke. TV môže požiadať o povolenie inštalovať aplikácie."
                },
                fontSize = 16.sp, color = TellyoColors.TextSecondary,
            )
            if (state.phase == UpdatePhase.DOWNLOADING) {
                TellyoProgressBar(state.progress, modifier = Modifier.fillMaxWidth())
            }
            state.error?.let { error ->
                Text(
                    when (error) {
                        UpdateError.DOWNLOAD -> "Aktualizáciu sa nepodarilo stiahnuť alebo overiť. Skontrolujte pripojenie a skúste znova. Aktualizácie vyžadujú oficiálnu podpísanú verziu Tellyo."
                        UpdateError.INSTALL -> "Inštalátor sa nepodarilo otvoriť. Povoľte Tellyo inštalovať aplikácie v nastaveniach TV a skúste znova."
                    },
                    fontSize = 14.sp, color = TellyoColors.Warning,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.phase != UpdatePhase.DOWNLOADING) {
                    TellyoButton(
                        text = if (state.phase == UpdatePhase.READY) "Nainštalovať"
                            else if (state.error != null) "Skúsiť znova" else "Aktualizovať",
                        focusRequester = actionFocus,
                        primary = true,
                        onClick = if (state.phase == UpdatePhase.READY) onInstall else onDownload,
                    )
                }
                TellyoButton(
                    text = if (state.phase == UpdatePhase.DOWNLOADING) "Zrušiť" else "Neskôr",
                    focusRequester = if (state.phase == UpdatePhase.DOWNLOADING) actionFocus else null,
                    onClick = onDismiss,
                )
            }
        }
    }
}
