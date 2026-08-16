package com.tellyo.app.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TellyoColors {
    val BackgroundPrimary = Color(0xFF050B18)
    val BackgroundSecondary = Color(0xFF081426)
    val SurfacePrimary = Color(0xFF0B1A2D)
    val SurfaceSecondary = Color(0xFF10233A)
    val SurfaceElevated = Color(0xFF132A45)
    val PrimaryBlue = Color(0xFF168BFF)
    val PrimaryBlueBright = Color(0xFF25A7FF)
    val PrimaryBlueDark = Color(0xFF0866E8)
    val FocusGlow = Color(0x66168BFF)
    val TextPrimary = Color(0xFFF7F9FC)
    val TextSecondary = Color(0xFFB7C0CE)
    val TextMuted = Color(0xFF7D8999)
    val Live = Color(0xFFF04444)
    val Success = Color(0xFF45C96B)
    val Warning = Color(0xFFF5B942)
    val Divider = Color(0xFF20334A)
}

object TellyoSpacing {
    val Page = 24.sp
    val NavigationWidth = 242.sp
}

/** User preference exposed to the design system without coupling components to DataStore. */
val LocalTellyoReduceMotion = compositionLocalOf { false }

private val TellyoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 48.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
)

private val TellyoColorScheme = darkColorScheme(
    primary = TellyoColors.PrimaryBlue,
    onPrimary = TellyoColors.TextPrimary,
    primaryContainer = TellyoColors.PrimaryBlueDark,
    onPrimaryContainer = TellyoColors.TextPrimary,
    secondary = TellyoColors.PrimaryBlueBright,
    background = TellyoColors.BackgroundPrimary,
    onBackground = TellyoColors.TextPrimary,
    surface = TellyoColors.SurfacePrimary,
    onSurface = TellyoColors.TextPrimary,
    surfaceVariant = TellyoColors.SurfaceSecondary,
    onSurfaceVariant = TellyoColors.TextSecondary,
    error = TellyoColors.Live,
    onError = TellyoColors.TextPrimary,
)

@Composable
fun TellyoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TellyoColorScheme,
        typography = TellyoTypography,
        content = content,
    )
}
