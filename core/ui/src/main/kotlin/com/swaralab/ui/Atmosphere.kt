package com.swaralab.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun View.boothHaptic(kind: Int = HapticFeedbackConstants.KEYBOARD_TAP) {
    performHapticFeedback(kind)
}

@Composable
fun rememberPressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press-scale",
    )
    return scale
}

fun Modifier.pressScale(pressedScale: Float = 0.96f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                pressed = event.changes.any { it.pressed }
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    content: @Composable () -> Unit,
) {
    val fill = if (SwaraColors.Dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.55f)
    val edge = if (SwaraColors.Dark) {
        listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
    } else {
        listOf(Color.White.copy(alpha = 0.9f), SwaraColors.Line.copy(alpha = 0.35f))
    }
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(fill)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(edge),
                shape = RoundedCornerShape(24.dp),
            )
            .then(if (padded) Modifier.padding(16.dp) else Modifier),
    ) {
        content()
    }
}

@Composable
fun GlassSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    accentBorder: Color = SwaraColors.Line,
    selected: Boolean = false,
    haptic: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val view = LocalView.current
    val fill = if (SwaraColors.Dark) Color.White.copy(alpha = if (selected) 0.12f else 0.08f)
    else Color.White.copy(alpha = if (selected) 0.72f else 0.55f)
    val edge = if (selected) {
        listOf(SwaraColors.Teal.copy(alpha = 0.9f), SwaraColors.Gold.copy(alpha = 0.35f))
    } else if (SwaraColors.Dark) {
        listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
    } else {
        listOf(accentBorder.copy(alpha = 0.5f), Color.White.copy(alpha = 0.4f))
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fill)
            .border(width = if (selected) 1.5.dp else 1.dp, brush = Brush.linearGradient(edge), shape = shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                if (haptic) view.boothHaptic()
                onClick()
            },
    ) {
        content()
    }
}

@Composable
fun AnimatedMusicBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    voiceLevel: Float = 0f,
) {
    val infinite = rememberInfiniteTransition(label = "bg")
    val shift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shift",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(3_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val energy = (intensity + voiceLevel * 0.5f).coerceIn(0.35f, 1.4f)
    val paper = SwaraColors.Paper
    val teal = SwaraColors.Teal
    val gold = SwaraColors.Gold
    val rose = SwaraColors.Rose
    val dark = SwaraColors.Dark
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(paper)
                if (dark) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0A0A0F),
                                Color(0xFF1A237E).copy(alpha = 0.55f * energy * pulse),
                                Color(0xFF006064).copy(alpha = 0.45f * energy),
                                Color(0xFF4A148C).copy(alpha = 0.3f * energy * pulse),
                                Color(0xFF0A0A0F),
                            ),
                            start = Offset(shift * 0.3f, 0f),
                            end = Offset(shift, 1400f),
                        ),
                    )
                } else {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                paper,
                                teal.copy(alpha = 0.18f * energy * pulse),
                                gold.copy(alpha = 0.12f * energy),
                                rose.copy(alpha = 0.08f * pulse),
                                paper,
                            ),
                            start = Offset(shift * 0.3f, 0f),
                            end = Offset(shift, size.height),
                        ),
                    )
                }
            },
    )
}

@Composable
fun SoftGlowOrb(
    color: Color = Color(0xFF00E5FF),
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
) {
    Box(
        modifier
            .size(size)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.35f),
                        color.copy(alpha = 0.05f),
                        Color.Transparent,
                    ),
                ),
                CircleShape,
            ),
    )
}

@Composable
fun BoothCanvas(
    intensity: Float = 0.6f,
    orbs: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedMusicBackground(intensity = intensity)
        if (orbs) {
            SoftGlowOrb(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 48.dp, y = (-72).dp),
                size = 240.dp,
            )
            SoftGlowOrb(
                color = SwaraColors.Gold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-56).dp, y = 80.dp),
                size = 220.dp,
            )
        }
        content()
    }
}

@Composable
fun BoothSearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(placeholder) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(onClick = { onQueryChange("") }) { Text("×") }
            }
        },
    )
}

@Composable
fun GlassTrackCard(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    mark: String = "♪",
) {
    GlassSurface(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SwaraColors.Teal.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(mark, color = SwaraColors.Teal, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SwaraColors.Ink,
                    maxLines = 1,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwaraColors.Muted,
                    maxLines = 2,
                )
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            } else if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.labelLarge, color = SwaraColors.Teal)
            }
        }
    }
}

@Composable
fun FavoriteButton(
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (favorite) 1.25f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "favorite",
    )
    val view = LocalView.current
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                view.boothHaptic(HapticFeedbackConstants.LONG_PRESS)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (favorite) "♥" else "♡",
            color = if (favorite) Color(0xFFFF4081) else SwaraColors.Ink.copy(alpha = 0.85f),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        )
    }
}

@Composable
fun PulsingRecordButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "●",
    color: Color = Color(0xFFFF1744),
) {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val view = LocalView.current
    Box(
        modifier
            .size(88.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(color)
            .clickable {
                view.boothHaptic(HapticFeedbackConstants.LONG_PRESS)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = if (label.length > 1) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.headlineMedium
            },
        )
    }
}

@Composable
fun BoothShimmer(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val slide by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-slide",
    )
    val card = SwaraColors.Card
    val teal = SwaraColors.Teal
    Box(
        modifier
            .height(18.dp)
            .drawBehind {
                drawRoundRect(
                    color = card.copy(alpha = 0.82f),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            teal.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        start = Offset(size.width * (slide - 0.45f), 0f),
                        end = Offset(size.width * (slide + 0.12f), size.height),
                    ),
                )
            },
    )
}

fun Modifier.stageGlow(voiceLevel: Float): Modifier = drawBehind {
    val voice = voiceLevel.coerceIn(0f, 1f)
    drawRect(Color(0xFF0A0A0F))
    drawCircle(
        color = Color(0xFF00E5FF).copy(alpha = 0.10f + voice * 0.28f),
        radius = size.minDimension * (0.55f + voice * 0.18f),
        center = Offset(size.width / 2f, size.height * 0.42f),
    )
    drawCircle(
        color = Color(0xFFBB86FC).copy(alpha = 0.08f + voice * 0.12f),
        radius = size.minDimension * 0.38f + voice * 0.1f * size.minDimension,
        center = Offset(size.width * 0.2f, size.height * 0.78f),
    )
}
