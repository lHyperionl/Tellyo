package com.tellyo.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tellyo.app.core.ui.theme.TellyoColors

/** Visible over both native video and broadcaster pages without taking remote focus. */
@Composable
fun NetworkStatusOverlay(
    isInternetAvailable: Boolean,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        if (!isInternetAvailable) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(28.dp)
                    .background(TellyoColors.BackgroundPrimary, RoundedCornerShape(12.dp))
                    .border(1.dp, TellyoColors.Live, RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Internetové pripojenie je nedostupné.",
                    color = TellyoColors.Live,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Čakám na obnovenie pripojenia…",
                    color = TellyoColors.TextSecondary,
                    fontSize = 16.sp,
                )
            }
        }
    }
}
