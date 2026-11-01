package com.swaralab.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class SwaraThemeId(val tag: String, val label: String, val body: String) {
    RAGA(
        tag = "raga",
        label = "Raga Light",
        body = "Warm paper, teal meters, and gold cues. Best in daylight.",
    ),
    MIDNIGHT(
        tag = "midnight",
        label = "Midnight Stage",
        body = "Dark concert lighting so pitch graphs and lyrics stay easy at night.",
    ),
    VINYL(
        tag = "vinyl",
        label = "Vinyl Amber",
        body = "Warm studio lamps. Softer on the eyes for long mix sessions.",
    ),
    SAFFRON(
        tag = "saffron",
        label = "Saffron Riyaz",
        body = "Deep ink with saffron and gold, closer to a classical practice room.",
    ),
    SYSTEM(
        tag = "system",
        label = "Wallpaper colors",
        body = "Material You: follow this phone's wallpaper and dark mode (Android 12+).",
    ),
    ;

    companion object {
        fun fromTag(tag: String?): SwaraThemeId =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: RAGA
    }
}

data class SwaraPalette(
    val ink: Color,
    val teal: Color,
    val tealWash: Color,
    val gold: Color,
    val paper: Color,
    val card: Color,
    val line: Color,
    val muted: Color,
    val clay: Color,
    val isDark: Boolean,
) {
    fun colorScheme(): ColorScheme {
        val scheme = if (isDark) {
            darkColorScheme(
                primary = teal,
                onPrimary = Color(0xFF041214),
                secondary = gold,
                onSecondary = Color(0xFF1A1206),
                background = paper,
                onBackground = ink,
                surface = card,
                onSurface = ink,
                surfaceVariant = tealWash,
                onSurfaceVariant = muted,
                outline = line,
                error = clay,
            )
        } else {
            lightColorScheme(
                primary = teal,
                onPrimary = Color.White,
                secondary = gold,
                onSecondary = ink,
                background = paper,
                onBackground = ink,
                surface = card,
                onSurface = ink,
                surfaceVariant = tealWash,
                onSurfaceVariant = muted,
                outline = line,
                error = clay,
            )
        }
        return scheme
    }

    companion object {
        val RagaLight = SwaraPalette(
            ink = Color(0xFF16343A),
            teal = Color(0xFF0C6E78),
            tealWash = Color(0xFFE5F3F4),
            gold = Color(0xFF8A6416),
            paper = Color(0xFFF7F4EF),
            card = Color(0xFFFFFFFF),
            line = Color(0xFFE4DDD4),
            muted = Color(0xFF3F5358),
            clay = Color(0xFFC44B3C),
            isDark = false,
        )

        val MidnightStage = SwaraPalette(
            ink = Color(0xFFE7F3F5),
            teal = Color(0xFF3EC8C8),
            tealWash = Color(0xFF16383C),
            gold = Color(0xFFE0B44B),
            paper = Color(0xFF0B1216),
            card = Color(0xFF152026),
            line = Color(0xFF2A3A40),
            muted = Color(0xFF8AA0A6),
            clay = Color(0xFFE07A6C),
            isDark = true,
        )

        val VinylAmber = SwaraPalette(
            ink = Color(0xFFF3E6D4),
            teal = Color(0xFFE39B3C),
            tealWash = Color(0xFF3A2A18),
            gold = Color(0xFFF0C36A),
            paper = Color(0xFF16110D),
            card = Color(0xFF221A14),
            line = Color(0xFF3A2E24),
            muted = Color(0xFFB39A82),
            clay = Color(0xFFE07A6C),
            isDark = true,
        )

        val SaffronRiyaz = SwaraPalette(
            ink = Color(0xFFF6EDE0),
            teal = Color(0xFFE07A1F),
            tealWash = Color(0xFF3A2412),
            gold = Color(0xFFF0C14B),
            paper = Color(0xFF140E0A),
            card = Color(0xFF1F1610),
            line = Color(0xFF3D2C1C),
            muted = Color(0xFFC4A882),
            clay = Color(0xFFE25A4A),
            isDark = true,
        )

        fun fromDynamic(scheme: ColorScheme, dark: Boolean) = SwaraPalette(
            ink = scheme.onBackground,
            teal = scheme.primary,
            tealWash = scheme.primaryContainer,
            gold = scheme.secondary,
            paper = scheme.background,
            card = scheme.surface,
            line = scheme.outline,
            muted = scheme.onSurfaceVariant,
            clay = scheme.error,
            isDark = dark,
        )
    }
}

private var ActiveSwaraPalette: SwaraPalette = SwaraPalette.RagaLight

object SwaraColors {
    val Ink get() = ActiveSwaraPalette.ink
    val Teal get() = ActiveSwaraPalette.teal
    val TealWash get() = ActiveSwaraPalette.tealWash
    val Gold get() = ActiveSwaraPalette.gold
    val Paper get() = ActiveSwaraPalette.paper
    val Card get() = ActiveSwaraPalette.card
    val Line get() = ActiveSwaraPalette.line
    val Muted get() = ActiveSwaraPalette.muted
    val Clay get() = ActiveSwaraPalette.clay
}

enum class SwaraMark {
    SING,
    SONGS,
    PROFILE,
}

@Composable
fun SwaraLabTheme(
    themeId: SwaraThemeId = SwaraThemeId.RAGA,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val palette = remember(themeId, systemDark) {
        when (themeId) {
            SwaraThemeId.RAGA -> SwaraPalette.RagaLight
            SwaraThemeId.MIDNIGHT -> SwaraPalette.MidnightStage
            SwaraThemeId.VINYL -> SwaraPalette.VinylAmber
            SwaraThemeId.SAFFRON -> SwaraPalette.SaffronRiyaz
            SwaraThemeId.SYSTEM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val scheme = if (systemDark) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
                SwaraPalette.fromDynamic(scheme, systemDark)
            } else if (systemDark) {
                SwaraPalette.MidnightStage
            } else {
                SwaraPalette.RagaLight
            }
        }
    }
    ActiveSwaraPalette = palette

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !palette.isDark
            isAppearanceLightNavigationBars = !palette.isDark
        }
    }

    val base = Typography()
    MaterialTheme(
        colorScheme = palette.colorScheme(),
        typography = Typography(
            headlineLarge = base.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 34.sp,
                letterSpacing = (-0.5).sp,
            ),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
        ),
        content = content,
    )
}

@Composable
fun SwaraWordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Swara",
            color = SwaraColors.Teal,
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "Lab",
            color = SwaraColors.Gold,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Composable
fun SwaraMarkIcon(mark: SwaraMark, selected: Boolean, modifier: Modifier = Modifier) {
    val color = if (selected) SwaraColors.Teal else SwaraColors.Muted
    Canvas(modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        when (mark) {
            SwaraMark.SING -> {
                val centerX = size.width / 2
                drawRoundRect(
                    color = color,
                    topLeft = Offset(centerX - 3.5.dp.toPx(), 3.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(7.dp.toPx(), 10.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.5.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawArc(
                    color = color,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(centerX - 6.dp.toPx(), 6.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 11.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                )
                drawLine(color, Offset(centerX, 17.dp.toPx()), Offset(centerX, 21.dp.toPx()), stroke, StrokeCap.Round)
            }
            SwaraMark.SONGS -> {
                val left = 4.dp.toPx()
                val right = size.width - 4.dp.toPx()
                listOf(0.28f, 0.5f, 0.72f).forEach { fraction ->
                    val y = size.height * fraction
                    drawLine(color, Offset(left, y), Offset(right, y), stroke, StrokeCap.Round)
                }
            }
            SwaraMark.PROFILE -> {
                drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(size.width / 2, 7.dp.toPx()))
                drawArc(
                    color = color,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(4.dp.toPx(), 12.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width - 8.dp.toPx(), 16.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
fun ChoiceCard(
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) SwaraColors.TealWash else SwaraColors.Card,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) SwaraColors.Teal else SwaraColors.Line),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = if (selected) SwaraColors.Teal else SwaraColors.Ink)
            Spacer(Modifier.height(4.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = SwaraColors.Muted)
        }
    }
}
