package com.swaralab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SwaraColors {
    val Ink = Color(0xFF16343A)
    val Teal = Color(0xFF0C6E78)
    val TealWash = Color(0xFFE5F3F4)
    val Gold = Color(0xFFC4922A)
    val Paper = Color(0xFFF7F4EF)
    val Card = Color(0xFFFFFFFF)
    val Line = Color(0xFFE4DDD4)
    val Muted = Color(0xFF5E7276)
    val Clay = Color(0xFFC44B3C)
}

enum class SwaraMark {
    SING,
    SONGS,
    PROFILE,
}

@Composable
fun SwaraLabTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = SwaraColors.Teal,
            onPrimary = Color.White,
            background = SwaraColors.Paper,
            onBackground = SwaraColors.Ink,
            surface = SwaraColors.Card,
            onSurface = SwaraColors.Ink,
            surfaceVariant = SwaraColors.TealWash,
            secondary = SwaraColors.Gold,
            onSecondary = SwaraColors.Ink,
            outline = SwaraColors.Line,
            error = SwaraColors.Clay,
        ),
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
