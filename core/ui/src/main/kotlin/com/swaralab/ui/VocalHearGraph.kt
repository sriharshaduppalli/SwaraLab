package com.swaralab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

data class VocalHearReadout(
    val waveform: List<Float> = emptyList(),
    val pitchHistory: List<Float> = emptyList(),
    val bass: Float = 0f,
    val mid: Float = 0f,
    val air: Float = 0f,
    val rmsDb: Float = -60f,
    val pitchHz: Float = 0f,
    val noteName: String = "—",
    val swaraName: String = "—",
    val cents: Int = 0,
    val active: Boolean = false,
)

@Composable
fun VocalHearGraph(
    readout: VocalHearReadout,
    modifier: Modifier = Modifier,
    title: String = "Voice, pitch, and tone",
) {
    val hasPitch = readout.pitchHz > 0f
    val voiceLevel = ((readout.rmsDb + 48f) / 42f).coerceIn(0f, 1f)
    val voiceTone = when {
        voiceLevel < 0.22f -> SwaraColors.Muted
        voiceLevel > 0.88f -> SwaraColors.Clay
        else -> SwaraColors.Teal
    }
    val pitchTone = when {
        !hasPitch -> SwaraColors.Gold
        abs(readout.cents) <= 12 -> SwaraColors.Teal
        abs(readout.cents) <= 35 -> SwaraColors.Gold
        else -> SwaraColors.Clay
    }
    val tuningLabel = when {
        !hasPitch -> if (readout.active) "Hearing the mix" else "Tap Hear mix, then watch the wave"
        abs(readout.cents) <= 12 -> "In tune"
        abs(readout.cents) <= 35 && readout.cents < 0 -> "A little low"
        abs(readout.cents) <= 35 -> "A little high"
        readout.cents < 0 -> "Low · lift the note"
        else -> "High · ease the note"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, if (readout.active) SwaraColors.Teal else SwaraColors.Line),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = SwaraColors.Ink)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (hasPitch) pitchTone.copy(alpha = 0.16f) else SwaraColors.Paper,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = if (hasPitch) {
                            "${readout.swaraName}  ·  ${readout.noteName}"
                        } else if (readout.active) {
                            "Listening"
                        } else {
                            "Ready"
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (hasPitch) pitchTone else SwaraColors.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (hasPitch) {
                            "$tuningLabel  ·  ${readout.pitchHz.roundToInt()} Hz  ·  ${readout.rmsDb.roundToInt()} dB"
                        } else {
                            tuningLabel
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = SwaraColors.Ink,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                    color = SwaraColors.Paper,
                    border = BorderStroke(1.dp, SwaraColors.Line),
                ) {
                    PitchAndWaveformCanvas(
                        waveform = readout.waveform,
                        pitchHistory = readout.pitchHistory,
                        pitchColor = pitchTone,
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp, top = 8.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("High", style = MaterialTheme.typography.labelSmall, color = SwaraColors.Muted)
                    Spacer(Modifier.weight(1f))
                    Text("Mid", style = MaterialTheme.typography.labelSmall, color = SwaraColors.Muted)
                    Spacer(Modifier.weight(1f))
                    Text("Low", style = MaterialTheme.typography.labelSmall, color = SwaraColors.Muted)
                }
            }
            ToneBar(label = "Voice", hint = when {
                !readout.active -> "—"
                voiceLevel < 0.22f -> "Quiet"
                voiceLevel > 0.88f -> "Hot"
                else -> "Good"
            }, level = voiceLevel, color = voiceTone)
            ToneBar(label = "Bass", hint = "Warmth", level = readout.bass, color = SwaraColors.Teal)
            ToneBar(label = "Mid", hint = "Words", level = readout.mid, color = SwaraColors.Gold)
            ToneBar(label = "Air", hint = "Shine", level = readout.air, color = SwaraColors.Rose)
        }
    }
}

@Composable
private fun PitchAndWaveformCanvas(
    waveform: List<Float>,
    pitchHistory: List<Float>,
    pitchColor: Color,
) {
    val path = remember { Path() }
    Canvas(modifier = Modifier.fillMaxSize().padding(start = 36.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
        val width = size.width
        val height = size.height
        val waveFloor = height * 0.78f
        val pitchHeight = height * 0.74f
        listOf(0.08f, 0.5f, 0.92f).forEach { frac ->
            val y = pitchHeight * frac
            drawLine(
                color = SwaraColors.Line.copy(alpha = 0.7f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawRect(
            color = SwaraColors.Teal.copy(alpha = 0.10f),
            topLeft = Offset(0f, pitchHeight * 0.38f),
            size = Size(width, pitchHeight * 0.24f),
        )
        val slots = maxOf(waveform.size, pitchHistory.size, 2)
        val stepX = width / (slots - 1).toFloat()
        waveform.forEachIndexed { idx, amp ->
            val x = idx * stepX
            val bar = (amp.coerceIn(0.04f, 1f) * (height - waveFloor) * 0.9f)
            drawLine(
                color = SwaraColors.Teal.copy(alpha = 0.45f),
                start = Offset(x, height),
                end = Offset(x, height - bar),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        path.reset()
        var penDown = false
        var lastPoint: Offset? = null
        pitchHistory.forEachIndexed { idx, normPitch ->
            val x = idx * stepX
            if (normPitch > 0.01f) {
                val y = pitchHeight * (1f - normPitch.coerceIn(0.05f, 0.95f))
                val point = Offset(x, y)
                if (!penDown) {
                    path.moveTo(x, y)
                    penDown = true
                } else {
                    path.lineTo(x, y)
                }
                lastPoint = point
            } else {
                penDown = false
            }
        }
        drawPath(
            path = path,
            color = pitchColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        lastPoint?.let { point ->
            drawCircle(color = pitchColor.copy(alpha = 0.22f), radius = 10.dp.toPx(), center = point)
            drawCircle(color = pitchColor, radius = 4.5.dp.toPx(), center = point)
        }
    }
}

@Composable
private fun ToneBar(
    label: String,
    hint: String,
    level: Float,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(52.dp),
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Ink,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(color = SwaraColors.Line.copy(alpha = 0.55f))
                drawRect(
                    color = SwaraColors.Teal.copy(alpha = 0.16f),
                    topLeft = Offset(size.width * 0.28f, 0f),
                    size = Size(size.width * 0.52f, size.height),
                )
                drawRect(
                    color = color,
                    size = Size(size.width * level.coerceIn(0.02f, 1f), size.height),
                )
            }
        }
        Text(
            text = hint,
            modifier = Modifier.width(58.dp),
            maxLines = 1,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}
