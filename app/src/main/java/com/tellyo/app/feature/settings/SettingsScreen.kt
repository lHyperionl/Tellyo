package com.tellyo.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tellyo.app.core.model.VideoQualityPreference
import com.tellyo.app.core.ui.components.SectionHeading
import com.tellyo.app.core.ui.components.TellyoButton
import com.tellyo.app.core.ui.components.TellyoFocusableSurface
import com.tellyo.app.core.ui.components.TellyoFocusStyles
import com.tellyo.app.core.ui.components.TellyoIcon
import com.tellyo.app.core.ui.components.TellyoIcon as TellyoIconType
import com.tellyo.app.core.ui.theme.TellyoColors

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    navigationFocus: FocusRequester,
    entryFocus: FocusRequester? = null,
    autoFocusContent: Boolean = true,
    onAction: (SettingsAction) -> Unit,
) {
    val motionFocus = entryFocus ?: remember { FocusRequester() }
    val qualityFocus = remember { FocusRequester() }
    val resetFocus = remember { FocusRequester() }

    LaunchedEffect(autoFocusContent) {
        if (autoFocusContent) motionFocus.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 18.dp, start = 8.dp, end = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "Nastavenia",
            color = TellyoColors.TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Prispôsobte si správanie aplikácie Tellyo",
            color = TellyoColors.TextSecondary,
            fontSize = 16.sp,
        )

        SectionHeading(text = "Vzhľad a ovládanie")
        SettingsToggleRow(
            title = "Obmedziť animácie",
            description = "Vypne zväčšenie a zrýchli prechody pri ovládaní diaľkovým ovládačom.",
            enabled = state.reduceMotion,
            focusRequester = motionFocus,
            navigationFocus = navigationFocus,
            downFocus = qualityFocus,
            onClick = { onAction(SettingsAction.ToggleReduceMotion) },
        )

        SectionHeading(text = "Prehrávanie")
        SettingsChoiceRow(
            title = "Kvalita videa",
            description = state.videoQuality.description,
            value = state.videoQuality.label,
            focusRequester = qualityFocus,
            navigationFocus = navigationFocus,
            upFocus = motionFocus,
            downFocus = resetFocus,
            onClick = { onAction(SettingsAction.CycleVideoQuality) },
        )
        SettingsInfoRow(
            title = "Prehrávač",
            description = "Media3 / ExoPlayer",
            icon = TellyoIconType.PLAY,
        )
        SettingsInfoRow(
            title = "Oficiálne vysielania",
            description = "Otvárajú sa cez oficiálnu stránku poskytovateľa.",
            icon = TellyoIconType.INFO,
        )

        SectionHeading(text = "Údržba")
        TellyoButton(
            text = "Obnoviť predvolené nastavenia",
            icon = TellyoIconType.SETTINGS,
            focusRequester = resetFocus,
            modifier = Modifier
                .width(280.dp)
                .focusProperties {
                    left = navigationFocus
                    up = qualityFocus
                },
            onClick = { onAction(SettingsAction.Reset) },
        )

        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Tellyo 0.1.0  •  TV foundation",
            color = TellyoColors.TextMuted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    description: String,
    value: String,
    focusRequester: FocusRequester,
    navigationFocus: FocusRequester,
    upFocus: FocusRequester,
    downFocus: FocusRequester,
    onClick: () -> Unit,
) {
    TellyoFocusableSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .focusProperties {
                left = navigationFocus
                up = upFocus
                down = downFocus
            },
        shape = RoundedCornerShape(12.dp),
        focusRequester = focusRequester,
        contentDescription = "$title: $value",
        focusStyle = TellyoFocusStyles.Standard,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, color = TellyoColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(text = description, color = TellyoColors.TextSecondary, fontSize = 14.sp)
            }
            Box(
                modifier = Modifier
                    .background(TellyoColors.SurfaceSecondary, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text(text = value, color = TellyoColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    enabled: Boolean,
    focusRequester: FocusRequester,
    navigationFocus: FocusRequester,
    downFocus: FocusRequester,
    onClick: () -> Unit,
) {
    TellyoFocusableSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .focusProperties {
                left = navigationFocus
                down = downFocus
            },
        shape = RoundedCornerShape(12.dp),
        focusRequester = focusRequester,
        contentDescription = "$title: ${if (enabled) "zapnuté" else "vypnuté"}",
        focusStyle = TellyoFocusStyles.Standard,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    color = TellyoColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(text = description, color = TellyoColors.TextSecondary, fontSize = 14.sp)
            }
            SettingIndicator(enabled = enabled)
        }
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    description: String,
    icon: TellyoIcon,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(TellyoColors.SurfacePrimary, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TellyoIcon(icon = icon, tint = TellyoColors.PrimaryBlueBright, modifier = Modifier.width(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = TellyoColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = description, color = TellyoColors.TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SettingIndicator(enabled: Boolean) {
    Box(
        modifier = Modifier
            .width(84.dp)
            .height(36.dp)
            .background(
                if (enabled) TellyoColors.PrimaryBlue else TellyoColors.SurfaceSecondary,
                RoundedCornerShape(18.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (enabled) "ZAP" else "VYP",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val VideoQualityPreference.label: String
    get() = when (this) {
        VideoQualityPreference.AUTO -> "AUTO"
        VideoQualityPreference.HIGH -> "VYSOKÁ"
        VideoQualityPreference.STABLE -> "STABILNÁ"
    }

private val VideoQualityPreference.description: String
    get() = when (this) {
        VideoQualityPreference.AUTO -> "Prispôsobí kvalitu pripojeniu (odporúčané)."
        VideoQualityPreference.HIGH -> "Preferuje najvyššiu dostupnú kvalitu; môže viac bufferovať."
        VideoQualityPreference.STABLE -> "Uprednostní plynulosť pri slabšom pripojení."
    }
