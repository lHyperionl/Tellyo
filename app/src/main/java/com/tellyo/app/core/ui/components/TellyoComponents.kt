package com.tellyo.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tellyo.app.core.ui.theme.TellyoColors
import com.tellyo.app.core.ui.theme.LocalTellyoReduceMotion

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
    val idleBorder: Color = TellyoColors.Divider,
    val selectedBorder: Color = TellyoColors.PrimaryBlueDark,
    val focusedBorder: Color = TellyoColors.PrimaryBlueBright,
    val focusedScale: Float = 1.04f,
    val glowElevation: Dp = 15.dp,
)

object TellyoFocusStyles {
    val Standard = TellyoFocusStyle()

    val Navigation = TellyoFocusStyle(
        idleBackground = Color.Transparent,
        selectedBackground = TellyoColors.PrimaryBlueDark,
        focusedBackground = TellyoColors.PrimaryBlue,
        idleBorder = Color.Transparent,
        selectedBorder = TellyoColors.PrimaryBlueDark,
        focusedBorder = TellyoColors.PrimaryBlueBright,
        focusedScale = 1.03f,
        glowElevation = 12.dp,
    )

    val FilledSelection = TellyoFocusStyle(
        selectedBackground = TellyoColors.PrimaryBlueDark,
        focusedBackground = TellyoColors.PrimaryBlue,
        selectedBorder = TellyoColors.PrimaryBlueDark,
        focusedBorder = TellyoColors.PrimaryBlueBright,
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
        TellyoIcon(
            icon = TellyoIcon.PLAY,
            tint = TellyoColors.PrimaryBlueBright,
            modifier = Modifier.size(iconSize),
            contentDescription = "Tellyo",
        )
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
                color = TellyoColors.PrimaryBlueBright,
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
    Canvas(
        modifier = modifier.semantics {
            contentDescription?.let { this.contentDescription = it }
        },
    ) {
        drawTellyoIcon(icon, tint)
    }
}

private fun DrawScope.drawTellyoIcon(icon: TellyoIcon, tint: Color) {
    val stroke = Stroke(
        width = 2.2.dp.toPx(),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
    when (icon) {
        TellyoIcon.PLAY -> {
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.16f)
                lineTo(size.width * 0.80f, size.height * 0.50f)
                lineTo(size.width * 0.28f, size.height * 0.84f)
                close()
            }
            drawPath(path = path, color = tint, style = Fill)
        }

        TellyoIcon.HOME -> {
            val path = Path().apply {
                moveTo(size.width * 0.16f, size.height * 0.46f)
                lineTo(size.width * 0.50f, size.height * 0.16f)
                lineTo(size.width * 0.84f, size.height * 0.46f)
                moveTo(size.width * 0.25f, size.height * 0.40f)
                lineTo(size.width * 0.25f, size.height * 0.84f)
                lineTo(size.width * 0.75f, size.height * 0.84f)
                lineTo(size.width * 0.75f, size.height * 0.40f)
            }
            drawPath(path = path, color = tint, style = stroke)
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.46f, size.height * 0.84f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.46f, size.height * 0.60f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }

        TellyoIcon.LIVE_TV -> {
            drawRoundRect(
                color = tint,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.14f, size.height * 0.27f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.52f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                style = stroke,
            )
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.18f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.30f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.60f, size.height * 0.18f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.30f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }

        TellyoIcon.GUIDE -> {
            for (index in 0..2) {
                val y = size.height * (0.27f + index * 0.24f)
                drawCircle(tint, radius = 1.8.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.20f, y))
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.34f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.84f, y),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
        }

        TellyoIcon.FAVORITES -> {
            val path = Path().apply {
                moveTo(size.width * 0.50f, size.height * 0.80f)
                cubicTo(
                    size.width * 0.42f, size.height * 0.69f,
                    size.width * 0.16f, size.height * 0.53f,
                    size.width * 0.16f, size.height * 0.33f,
                )
                cubicTo(
                    size.width * 0.16f, size.height * 0.08f,
                    size.width * 0.45f, size.height * 0.10f,
                    size.width * 0.50f, size.height * 0.28f,
                )
                cubicTo(
                    size.width * 0.55f, size.height * 0.10f,
                    size.width * 0.84f, size.height * 0.08f,
                    size.width * 0.84f, size.height * 0.33f,
                )
                cubicTo(
                    size.width * 0.84f, size.height * 0.53f,
                    size.width * 0.58f, size.height * 0.69f,
                    size.width * 0.50f, size.height * 0.80f,
                )
                close()
            }
            drawPath(path = path, color = tint, style = stroke)
        }

        TellyoIcon.SEARCH -> {
            drawCircle(tint, radius = size.minDimension * 0.27f, center = center, style = stroke)
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.68f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.86f, size.height * 0.86f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }

        TellyoIcon.SETTINGS -> {
            drawCircle(tint, radius = size.minDimension * 0.28f, center = center, style = stroke)
            drawCircle(tint, radius = size.minDimension * 0.09f, center = center, style = stroke)
            for (index in 0 until 8) {
                val angle = Math.toRadians(index * 45.0)
                val inner = size.minDimension * 0.37f
                val outer = size.minDimension * 0.47f
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * inner,
                        center.y + kotlin.math.sin(angle).toFloat() * inner,
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * outer,
                        center.y + kotlin.math.sin(angle).toFloat() * outer,
                    ),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
        }

        TellyoIcon.INFO -> {
            drawCircle(tint, radius = size.minDimension * 0.35f, center = center, style = stroke)
            drawCircle(tint, radius = 1.5.dp.toPx(), center = center.copy(y = size.height * 0.34f))
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.44f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.68f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }

        TellyoIcon.SIGNAL -> {
            for (index in 0..3) {
                val barHeight = size.height * (0.20f + index * 0.17f)
                drawRoundRect(
                    color = tint,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * (0.16f + index * 0.19f), size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.11f, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                )
            }
        }

        TellyoIcon.CHEVRON_RIGHT -> {
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.18f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.50f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.50f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.82f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }

        TellyoIcon.CHEVRON_UP -> {
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.62f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.30f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.30f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.62f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }

        TellyoIcon.CHEVRON_DOWN -> {
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.38f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.70f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.70f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.38f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
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
    val backgroundColor by animateColorAsState(
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
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isFocused) {
                    Modifier.shadow(
                        elevation = focusStyle.glowElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = TellyoColors.FocusGlow,
                        spotColor = TellyoColors.FocusGlow,
                    )
                } else {
                    Modifier
                },
            )
            .background(backgroundColor, shape)
            .border(if (isFocused) 3.dp else 1.dp, borderColor, shape)
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
    onClick: () -> Unit = {},
) {
    TellyoFocusableSurface(
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(10.dp),
        focusRequester = focusRequester,
        contentDescription = text,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon?.let { TellyoIcon(it, tint = TellyoColors.TextSecondary, modifier = Modifier.size(20.dp)) }
            Text(text = text, color = TellyoColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
            text = text.uppercase(),
            color = TellyoColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.7.sp,
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
) {
    val (label, color) = when (logoKey) {
        "stvr" -> ":1" to Color(0xFFFF1D55)
        "joj" -> "JOJ" to Color(0xFFF7F9FC)
        "joj-plus" -> "JOJ+" to Color(0xFFE3273E)
        "joj-krimi" -> "KRIMI" to Color(0xFFE3273E)
        "joj-24" -> "JOJ24" to Color(0xFFE3273E)
        "joj-sport" -> "JOJ Š" to Color(0xFFF59E0B)
        "jojko" -> "JOJko" to Color(0xFF35B8E8)
        "joj-svet" -> "JOJ S" to Color(0xFFF7F9FC)
        "markiza" -> "M" to Color(0xFFE4236B)
        "markiza-doma" -> "DOMA" to Color(0xFFE4236B)
        "markiza-dajto" -> "DAJTO" to Color(0xFF36A7E8)
        "markiza-krimi" -> "KRIMI" to Color(0xFFEF4444)
        "markiza-klasik" -> "KLASIK" to Color(0xFFF3B33D)
        "ta3" -> "ta3" to Color(0xFFF7F9FC)
        "24" -> ":24" to Color(0xFFE02B7B)
        "dvojka" -> ":2" to Color(0xFF14B8B7)
        else -> "rtv:" to Color(0xFFE52866)
    }
    Text(
        text = label,
        modifier = modifier,
        color = color,
        fontSize = textSize ?: if (large) 42.sp else 23.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.2).sp,
    )
}

@Composable
fun SpacerWidth(width: Dp) = Spacer(Modifier.width(width))
