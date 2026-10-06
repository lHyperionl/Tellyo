package com.tellyo.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tellyo.app.core.ui.theme.TellyoColors
import com.tellyo.app.core.ui.theme.LocalTellyoReduceMotion
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

enum class TellyoIcon {
    HOME,
    LIVE_TV,
    GUIDE,
    FAVORITES,
    SEARCH,
    SETTINGS,
    INFO,
    SIGNAL,
    CHEVRON_RIGHT,
    CHEVRON_UP,
    CHEVRON_DOWN,
    PLAY,
}

data class TellyoFocusStyle(
    val idleBackground: Color = TellyoColors.SurfacePrimary,
    val selectedBackground: Color = TellyoColors.SurfaceSecondary,
    val focusedBackground: Color = TellyoColors.SurfaceElevated,
    val idleBorder: Color = Color.Transparent,
    val selectedBorder: Color = Color.Transparent,
    val focusedBorder: Color = TellyoColors.FocusRing,
    val focusedScale: Float = 1.015f,
    val selectedBorderWidth: Dp = 1.dp,
    val focusedBorderWidth: Dp = 3.dp,
)

object TellyoFocusStyles {
    val Standard = TellyoFocusStyle()

    val Channel = TellyoFocusStyle(
        selectedBackground = TellyoColors.PrimaryBlueDark,
        focusedBackground = Color(0xFF2B4F73),
        selectedBorder = TellyoColors.PrimaryBlue,
        focusedBorder = TellyoColors.FocusRing,
        focusedScale = 1.025f,
        selectedBorderWidth = 2.dp,
        focusedBorderWidth = 4.dp,
    )

    val Navigation = TellyoFocusStyle(
        idleBackground = Color.Transparent,
        selectedBackground = TellyoColors.SurfaceSecondary,
        focusedBackground = TellyoColors.PrimaryBlueDark,
        idleBorder = Color.Transparent,
        selectedBorder = Color.Transparent,
        focusedBorder = TellyoColors.FocusRing,
        focusedScale = 1f,
    )

    val FilledSelection = TellyoFocusStyle(
        selectedBackground = TellyoColors.PrimaryBlueDark,
        focusedBackground = TellyoColors.SurfaceElevated,
        selectedBorder = Color.Transparent,
        focusedBorder = TellyoColors.FocusRing,
        focusedScale = 1f,
    )

    // Large imagery and tightly aligned guide cells must not grow over neighbours.
    val Hero = TellyoFocusStyle(
        idleBackground = TellyoColors.SurfacePrimary,
        focusedBackground = TellyoColors.SurfacePrimary,
        focusedScale = 1f,
    )

    val PrimaryAction = TellyoFocusStyle(
        idleBackground = TellyoColors.PrimaryBlueDark,
        focusedBackground = TellyoColors.SurfaceElevated,
        focusedScale = 1f,
    )
}

@Composable
fun TellyoLogo(
    modifier: Modifier = Modifier,
    iconSize: Dp = 30.dp,
    textSize: androidx.compose.ui.unit.TextUnit = 30.sp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TellyoBrandMark(modifier = Modifier.size(iconSize))
        Row {
            Text(
                text = "Telly",
                color = TellyoColors.TextPrimary,
                fontSize = textSize,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-1).sp,
            )
            Text(
                text = "o",
                color = TellyoColors.BrandBlue,
                fontSize = textSize,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-1).sp,
            )
        }
    }
}

@Composable
fun TellyoIcon(
    icon: TellyoIcon,
    modifier: Modifier = Modifier.size(28.dp),
    tint: Color = TellyoColors.TextPrimary,
    contentDescription: String? = null,
) {
    val image = when (icon) {
        TellyoIcon.HOME -> Icons.Outlined.Home
        TellyoIcon.LIVE_TV -> Icons.Outlined.LiveTv
        TellyoIcon.GUIDE -> Icons.AutoMirrored.Outlined.List
        TellyoIcon.FAVORITES -> Icons.Outlined.FavoriteBorder
        TellyoIcon.SEARCH -> Icons.Outlined.Search
        TellyoIcon.SETTINGS -> Icons.Outlined.Settings
        TellyoIcon.INFO -> Icons.Outlined.Info
        TellyoIcon.SIGNAL -> Icons.Outlined.SignalCellularAlt
        TellyoIcon.CHEVRON_RIGHT -> Icons.AutoMirrored.Outlined.KeyboardArrowRight
        TellyoIcon.CHEVRON_UP -> Icons.Outlined.KeyboardArrowUp
        TellyoIcon.CHEVRON_DOWN -> Icons.Outlined.KeyboardArrowDown
        TellyoIcon.PLAY -> Icons.Outlined.PlayArrow
    }
    Icon(imageVector = image, contentDescription = contentDescription, modifier = modifier, tint = tint)
}

// Preserve the supplied brand mark independently of the Material control icons.
@Composable
private fun TellyoBrandMark(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.16f)
            lineTo(size.width * 0.80f, size.height * 0.50f)
            lineTo(size.width * 0.28f, size.height * 0.84f)
            close()
        }
        drawPath(path, color = TellyoColors.BrandBlue)
    }
}
@Composable
fun TellyoFocusableSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    contentDescription: String? = null,
    focusStyle: TellyoFocusStyle = TellyoFocusStyles.Standard,
    onFocusChanged: (Boolean) -> Unit = {},
    onKeyInput: (Key, KeyEventType) -> Boolean = { _, _ -> false },
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val reduceMotion = LocalTellyoReduceMotion.current
    val focusAnimationDuration = if (reduceMotion) 0 else 180
    val scale by animateFloatAsState(
        targetValue = if (isFocused && !reduceMotion) focusStyle.focusedScale else 1f,
        animationSpec = tween(durationMillis = focusAnimationDuration),
        label = "focus-scale",
    )
    val backgroundColor = animateColorAsState(
        targetValue = when {
            isFocused -> focusStyle.focusedBackground
            selected -> focusStyle.selectedBackground
            else -> focusStyle.idleBackground
        },
        animationSpec = tween(durationMillis = focusAnimationDuration),
        label = "focus-background",
    )
    val borderColor = when {
        isFocused -> focusStyle.focusedBorder
        selected -> focusStyle.selectedBorder
        else -> focusStyle.idleBorder
    }

    Box(
        modifier = modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged {
                if (isFocused != it.isFocused) {
                    isFocused = it.isFocused
                    onFocusChanged(isFocused)
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                onDrawBehind { drawOutline(outline, color = backgroundColor.value) }
            }
            .border(
                when {
                    isFocused -> focusStyle.focusedBorderWidth
                    selected -> focusStyle.selectedBorderWidth
                    else -> 1.dp
                },
                borderColor,
                shape,
            )
            .clip(shape)
            .focusable()
            .onKeyEvent { event ->
                if (onKeyInput(event.key, event.type)) {
                    true
                } else {
                val isConfirm = event.key == Key.Enter ||
                    event.key == Key.DirectionCenter ||
                    event.key == Key.NumPadEnter
                if (isConfirm && event.type == KeyEventType.KeyUp) {
                    onClick()
                    true
                } else {
                    false
                }
                }
            }
            .then(
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        role = Role.Button
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}

@Composable
fun TellyoButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: TellyoIcon? = null,
    focusRequester: FocusRequester? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    TellyoFocusableSurface(
        modifier = modifier.height(42.dp).semantics { if (!enabled) disabled() },
        shape = RoundedCornerShape(10.dp),
        focusRequester = focusRequester,
        contentDescription = text,
        focusStyle = if (primary) TellyoFocusStyles.PrimaryAction else TellyoFocusStyles.Standard,
        onClick = { if (enabled) onClick() },
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon?.let { TellyoIcon(it, tint = TellyoColors.TextSecondary, modifier = Modifier.size(20.dp)) }
            Text(text = text, color = TellyoColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            color = TellyoColors.TextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
        trailing?.invoke()
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(TellyoColors.Live, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "NAŽIVO",
            color = TellyoColors.TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
fun TellyoProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(TellyoColors.TextMuted.copy(alpha = 0.35f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(TellyoColors.PrimaryBlue, RoundedCornerShape(height / 2)),
        )
    }
}

@Composable
fun ChannelLogo(
    logoKey: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    textSize: TextUnit? = null,
    logoUrl: String? = null,
) {
    val (label, color) = when (logoKey) {
        "stvr" -> ":1" to Color(0xFFFF1D55)
        "joj" -> "JOJ" to Color(0xFFF7F9FC)
        "joj-plus" -> "JOJ+" to Color(0xFFE3273E)
        "joj-krimi" -> "KRIMI" to Color(0xFFE3273E)
        "joj-24" -> "JOJ24" to Color(0xFFE3273E)
        "markiza" -> "M" to Color(0xFFE4236B)
        "markiza-doma" -> "DOMA" to Color(0xFFE4236B)
        "markiza-dajto" -> "DAJTO" to Color(0xFF36A7E8)
        "markiza-krimi" -> "KRIMI" to Color(0xFFEF4444)
        "markiza-klasik" -> "KLASIK" to Color(0xFFF3B33D)
        "ta3" -> "ta3" to Color(0xFFF7F9FC)
        "24" -> ":24" to Color(0xFFE02B7B)
        "dvojka" -> ":2" to Color(0xFF14B8B7)
        "sport" -> ":Š" to TellyoColors.TextPrimary
        else -> logoKey to TellyoColors.TextSecondary
    }
    val isMarkizaLogo = logoKey == "markiza" || logoKey.startsWith("markiza-")
    val imageModifier = modifier
        .width(if (large) 112.dp else if (isMarkizaLogo) 104.dp else 72.dp)
        .height(if (large) 58.dp else 36.dp)
    val bundledLogo = bundledChannelLogo(logoKey)
    val fallback: @Composable () -> Unit = {
        if (bundledLogo != null) {
            Image(
                painter = painterResource(bundledLogo),
                contentDescription = "$label logo",
                contentScale = ContentScale.Fit,
                modifier = imageModifier,
            )
        } else {
            Text(
                text = label,
                modifier = modifier,
                color = color,
                fontSize = textSize ?: if (large) 42.sp else 23.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (logoUrl.isNullOrBlank()) {
        fallback()
    } else {
        SubcomposeAsyncImage(
            model = logoUrl,
            contentDescription = "$label logo",
            contentScale = ContentScale.Fit,
            modifier = imageModifier,
            loading = { fallback() },
            error = { fallback() },
            success = { SubcomposeAsyncImageContent() },
        )
    }
}

@Composable
fun SpacerWidth(width: Dp) = Spacer(Modifier.width(width))
