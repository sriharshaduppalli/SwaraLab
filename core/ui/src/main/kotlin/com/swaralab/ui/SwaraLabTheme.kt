package com.swaralab.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class SwaraThemeId(val tag: String, val label: String, val body: String) {
    RAGA(
        tag = "raga",
        label = "Raga Light",
        body = "Warm rag paper, peacock teal, and kundan gold. Best in daylight practice.",
    ),
    MIDNIGHT(
        tag = "midnight",
        label = "Midnight Stage",
        body = "Concert black and cyan spots. Lyrics and pitch stay clear after dark.",
    ),
    VINYL(
        tag = "vinyl",
        label = "Vinyl Amber",
        body = "Walnut booth and lamp amber. Soft for long mix nights.",
    ),
    SAFFRON(
        tag = "saffron",
        label = "Saffron Riyaz",
        body = "Temple saffron and vermillion on deep ink. A classical practice room.",
    ),
    INDIGO(
        tag = "indigo",
        label = "Indigo Booth",
        body = "Cool indigo and champagne. Easy on the eyes for late edits.",
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
    val goldWash: Color,
    val rose: Color,
    val roseWash: Color,
    val paper: Color,
    val card: Color,
    val line: Color,
    val muted: Color,
    val clay: Color,
    val glow: Color,
    val isDark: Boolean,
) {
    fun colorScheme(): ColorScheme {
        val scheme = if (isDark) {
            darkColorScheme(
                primary = teal,
                onPrimary = Color(0xFF061014),
                primaryContainer = tealWash,
                onPrimaryContainer = ink,
                secondary = gold,
                onSecondary = Color(0xFF1A1206),
                secondaryContainer = goldWash,
                onSecondaryContainer = gold,
                tertiary = rose,
                onTertiary = Color(0xFF1A0B12),
                tertiaryContainer = roseWash,
                onTertiaryContainer = rose,
                background = paper,
                onBackground = ink,
                surface = card,
                onSurface = ink,
                surfaceVariant = tealWash,
                onSurfaceVariant = muted,
                surfaceTint = teal,
                outline = line,
                outlineVariant = line.copy(alpha = 0.7f),
                error = clay,
            )
        } else {
            lightColorScheme(
                primary = teal,
                onPrimary = Color.White,
                primaryContainer = tealWash,
                onPrimaryContainer = teal,
                secondary = gold,
                onSecondary = ink,
                secondaryContainer = goldWash,
                onSecondaryContainer = gold,
                tertiary = rose,
                onTertiary = Color.White,
                tertiaryContainer = roseWash,
                onTertiaryContainer = rose,
                background = paper,
                onBackground = ink,
                surface = card,
                onSurface = ink,
                surfaceVariant = tealWash,
                onSurfaceVariant = muted,
                surfaceTint = teal,
                outline = line,
                outlineVariant = line.copy(alpha = 0.8f),
                error = clay,
            )
        }
        return scheme
    }

    companion object {
        val RagaLight = SwaraPalette(
            ink = Color(0xFF1A3034),
            teal = Color(0xFF0A6E6A),
            tealWash = Color(0xFFDCEFED),
            gold = Color(0xFFB07A12),
            goldWash = Color(0xFFF6E7C4),
            rose = Color(0xFFB03D5C),
            roseWash = Color(0xFFF8E0E7),
            paper = Color(0xFFF4EDE3),
            card = Color(0xFFFFF9F2),
            line = Color(0xFFDCCFBE),
            muted = Color(0xFF4A5C60),
            clay = Color(0xFFC44B3C),
            glow = Color(0x3326A69A),
            isDark = false,
        )

        val MidnightStage = SwaraPalette(
            ink = Color(0xFFE8F7F8),
            teal = Color(0xFF46E0D4),
            tealWash = Color(0xFF12383A),
            gold = Color(0xFFF2C65A),
            goldWash = Color(0xFF3A2E12),
            rose = Color(0xFFFF8FB3),
            roseWash = Color(0xFF3A1C2A),
            paper = Color(0xFF070B10),
            card = Color(0xFF121A22),
            line = Color(0xFF2A3A46),
            muted = Color(0xFF8AA4AC),
            clay = Color(0xFFE07A6C),
            glow = Color(0x3346E0D4),
            isDark = true,
        )

        val VinylAmber = SwaraPalette(
            ink = Color(0xFFF6E8D4),
            teal = Color(0xFFD4893A),
            tealWash = Color(0xFF3A2816),
            gold = Color(0xFFF5D08A),
            goldWash = Color(0xFF403018),
            rose = Color(0xFFC96B4A),
            roseWash = Color(0xFF3A2218),
            paper = Color(0xFF16100B),
            card = Color(0xFF241A13),
            line = Color(0xFF3E2E22),
            muted = Color(0xFFB39A82),
            clay = Color(0xFFE07A6C),
            glow = Color(0x33D4893A),
            isDark = true,
        )

        val SaffronRiyaz = SwaraPalette(
            ink = Color(0xFFF8EFE2),
            teal = Color(0xFFF07828),
            tealWash = Color(0xFF3C1C10),
            gold = Color(0xFFFFD36A),
            goldWash = Color(0xFF3F2A0E),
            rose = Color(0xFFE85A4F),
            roseWash = Color(0xFF3A1614),
            paper = Color(0xFF160C08),
            card = Color(0xFF24140E),
            line = Color(0xFF4A2C1C),
            muted = Color(0xFFC4A882),
            clay = Color(0xFFE25A4A),
            glow = Color(0x33F07828),
            isDark = true,
        )

        val IndigoBooth = SwaraPalette(
            ink = Color(0xFFE8EAF6),
            teal = Color(0xFF8B9BFF),
            tealWash = Color(0xFF1C2240),
            gold = Color(0xFFE8D5A3),
            goldWash = Color(0xFF3A3220),
            rose = Color(0xFFC084FC),
            roseWash = Color(0xFF2C1A40),
            paper = Color(0xFF0C0E1A),
            card = Color(0xFF16182A),
            line = Color(0xFF2C3050),
            muted = Color(0xFF9AA3C4),
            clay = Color(0xFFE07A6C),
            glow = Color(0x338B9BFF),
            isDark = true,
        )

        fun preview(themeId: SwaraThemeId): SwaraPalette = when (themeId) {
            SwaraThemeId.RAGA -> RagaLight
            SwaraThemeId.MIDNIGHT -> MidnightStage
            SwaraThemeId.VINYL -> VinylAmber
            SwaraThemeId.SAFFRON -> SaffronRiyaz
            SwaraThemeId.INDIGO -> IndigoBooth
            SwaraThemeId.SYSTEM -> RagaLight
        }

        fun fromDynamic(scheme: ColorScheme, dark: Boolean) = SwaraPalette(
            ink = scheme.onBackground,
            teal = scheme.primary,
            tealWash = scheme.primaryContainer,
            gold = scheme.secondary,
            goldWash = scheme.secondaryContainer,
            rose = scheme.tertiary,
            roseWash = scheme.tertiaryContainer,
            paper = scheme.background,
            card = scheme.surface,
            line = scheme.outline,
            muted = scheme.onSurfaceVariant,
            clay = scheme.error,
            glow = scheme.primary.copy(alpha = 0.2f),
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
    val GoldWash get() = ActiveSwaraPalette.goldWash
    val Rose get() = ActiveSwaraPalette.rose
    val RoseWash get() = ActiveSwaraPalette.roseWash
    val Paper get() = ActiveSwaraPalette.paper
    val Card get() = ActiveSwaraPalette.card
    val Line get() = ActiveSwaraPalette.line
    val Muted get() = ActiveSwaraPalette.muted
    val Clay get() = ActiveSwaraPalette.clay
    val Glow get() = ActiveSwaraPalette.glow
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
            SwaraThemeId.INDIGO -> SwaraPalette.IndigoBooth
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
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        typography = Typography(
            headlineLarge = base.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 34.sp,
                letterSpacing = (-0.6).sp,
            ),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
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
        Surface(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(8.dp),
            shape = CircleShape,
            color = SwaraColors.Rose,
        ) {}
        Text(
            text = "Lab",
            color = SwaraColors.Gold,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Composable
fun SwaraMarkIcon(
    mark: SwaraMark,
    selected: Boolean,
    modifier: Modifier = Modifier,
    selectedColor: Color = SwaraColors.Teal,
) {
    val color = if (selected) selectedColor else SwaraColors.Muted
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
    accent: Color = SwaraColors.Teal,
    accentWash: Color = SwaraColors.TealWash,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) accentWash else SwaraColors.Card,
        tonalElevation = if (selected) 2.dp else 1.dp,
        shadowElevation = if (selected) 3.dp else 1.dp,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) accent else SwaraColors.Line),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .padding(vertical = 10.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(5.dp),
                    color = accent,
                    shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp),
                ) {}
            }
            Column(Modifier.padding(start = 13.dp, end = 18.dp, top = 16.dp, bottom = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (selected) accent else SwaraColors.Ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = body, style = MaterialTheme.typography.bodyMedium, color = SwaraColors.Muted)
            }
        }
    }
}

@Composable
fun ThemePickCard(
    theme: SwaraThemeId,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = SwaraPalette.preview(theme)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = preview.card,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) preview.teal else preview.line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                listOf(preview.paper, preview.teal, preview.gold, preview.rose).forEach { swatch ->
                    Box(
                        Modifier
                            .size(22.dp)
                            .border(1.dp, preview.line, CircleShape)
                            .background(swatch, CircleShape),
                    )
                }
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = theme.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = preview.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = theme.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = preview.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun Modifier.studioAtmosphere(): Modifier = composed {
    val paper = SwaraColors.Paper
    val tealWash = SwaraColors.TealWash
    val goldWash = SwaraColors.GoldWash
    val glow = SwaraColors.Glow
    val roseWash = SwaraColors.RoseWash
    drawBehind {
        drawRect(paper)
        drawRect(
            brush = Brush.verticalGradient(
                0f to tealWash.copy(alpha = 0.88f),
                0.28f to goldWash.copy(alpha = 0.42f),
                0.58f to paper,
                1f to paper,
            ),
        )
        drawCircle(
            color = glow,
            radius = size.minDimension * 0.62f,
            center = Offset(size.width * 0.94f, size.height * -0.02f),
        )
        drawCircle(
            color = roseWash.copy(alpha = 0.35f),
            radius = size.minDimension * 0.34f,
            center = Offset(size.width * 0.08f, size.height * 0.18f),
        )
    }
}

@Composable
fun PageHero(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    accent: Color = SwaraColors.Teal,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = SwaraColors.Card,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, SwaraColors.Line),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Box(
                Modifier
                    .padding(bottom = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(accent, RoundedCornerShape(4.dp)),
            )
            Text(text = title, style = MaterialTheme.typography.headlineLarge, color = SwaraColors.Ink)
            Spacer(Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyLarge, color = SwaraColors.Muted)
        }
    }
}
