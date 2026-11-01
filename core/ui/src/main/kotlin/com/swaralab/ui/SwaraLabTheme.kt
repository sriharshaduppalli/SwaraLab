package com.swaralab.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object SwaraColors {
    val Ink = Color(0xFF12110F)
    val Panel = Color(0xFF1C1A17)
    val Amber = Color(0xFFE7A23A)
    val AmberInk = Color(0xFF1A1408)
    val Paper = Color(0xFFF4EFE6)
    val Moss = Color(0xFF8FB8A8)
    val Clay = Color(0xFFE15A4A)
}

@Composable
fun SwaraLabTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SwaraColors.Amber,
            onPrimary = SwaraColors.AmberInk,
            background = SwaraColors.Ink,
            surface = SwaraColors.Panel,
            onBackground = SwaraColors.Paper,
            onSurface = SwaraColors.Paper,
            secondary = SwaraColors.Moss,
            error = SwaraColors.Clay,
        ),
        typography = Typography(
            headlineLarge = base.headlineLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
            ),
            titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif),
        ),
        content = content,
    )
}

@Composable
fun ChoiceCard(
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
    }
}
