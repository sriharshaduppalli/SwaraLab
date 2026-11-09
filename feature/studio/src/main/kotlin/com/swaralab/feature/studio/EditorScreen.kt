package com.swaralab.feature.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swaralab.domain.model.VocalPreset
import com.swaralab.ui.AppCopy
import com.swaralab.ui.SwaraColors
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

internal fun studioDurationMs(state: StudioUi): Long {
    val clipEnd = state.takes.filter { it.active }.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
    return maxOf(state.backing?.durationMs ?: 0L, clipEnd, 1L)
}

internal fun gainDbToVolume(gainDb: Float): Float =
    10f.pow(gainDb / 20f).coerceIn(0.05f, 1.5f)

internal fun volumeToGainDb(volume: Float): Float {
    val linear = volume.coerceIn(0.05f, 1.5f)
    return (20f * log10(linear)).coerceIn(-18f, 6f)
}

@Composable
internal fun EditorActionBar(
    copy: AppCopy,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onUndo, enabled = canUndo) { Text(copy.undo) }
        TextButton(onClick = onRedo, enabled = canRedo) { Text(copy.redo) }
        TextButton(onClick = onExport) { Text(copy.mixSave) }
    }
}

@Composable
internal fun TimelineOverview(
    takes: List<RecordedTakeUi>,
    durationMs: Long,
    positionMs: Long,
    punchStartMs: Long = 0L,
    punchEndMs: Long = 0L,
    onSeek: (Long) -> Unit = {},
    onMoveClip: (String, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val span = durationMs.toFloat().coerceAtLeast(1f)
    var pendingStarts by remember(takes) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var dragClipId by remember { mutableStateOf<String?>(null) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SwaraColors.Card,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .pointerInput(durationMs) {
                        detectTapGestures { offset ->
                            onSeek(((offset.x / size.width) * span).toLong().coerceAtLeast(0L))
                        }
                    }
                    .pointerInput(durationMs) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            onSeek(((change.position.x / size.width) * span).toLong().coerceAtLeast(0L))
                        }
                    },
            ) {
                val playX = ((positionMs / span) * size.width).coerceIn(0f, size.width)
                drawLine(
                    color = SwaraColors.Line,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = SwaraColors.Ink,
                    start = Offset(playX, 0f),
                    end = Offset(playX, size.height),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .pointerInput(takes, durationMs) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val lanes = takes.filter { it.active }.take(6)
                                if (lanes.isEmpty()) {
                                    dragClipId = null
                                    return@detectDragGestures
                                }
                                val laneHeight = size.height / lanes.size
                                val idx = (offset.y / laneHeight).toInt().coerceIn(0, lanes.lastIndex)
                                val take = lanes[idx]
                                val start = pendingStarts[take.id] ?: take.timelineStartMs
                                val left = (start / span) * size.width
                                val width = (take.durationMs / span) * size.width
                                dragClipId = if (offset.x in left..(left + width.coerceAtLeast(8f))) take.id else null
                            },
                            onDragEnd = {
                                val id = dragClipId
                                if (id != null) {
                                    val start = pendingStarts[id] ?: takes.firstOrNull { it.id == id }?.timelineStartMs
                                    if (start != null) onMoveClip(id, start.coerceAtLeast(0L))
                                }
                                dragClipId = null
                            },
                            onDragCancel = { dragClipId = null },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val id = dragClipId ?: return@detectDragGestures
                                val take = takes.firstOrNull { it.id == id } ?: return@detectDragGestures
                                val current = pendingStarts[id] ?: take.timelineStartMs
                                val deltaMs = ((dragAmount.x / size.width) * span).toLong()
                                pendingStarts = pendingStarts + (id to (current + deltaMs).coerceAtLeast(0L))
                            },
                        )
                    },
            ) {
                val lanes = takes.filter { it.active }.take(6)
                val laneHeight = if (lanes.isEmpty()) size.height else (size.height / lanes.size.coerceAtLeast(1))
                lanes.forEachIndexed { index, take ->
                    val start = pendingStarts[take.id] ?: take.timelineStartMs
                    val left = (start / span) * size.width
                    val width = (take.durationMs / span) * size.width
                    drawRect(
                        color = if (take.preferred) SwaraColors.Teal.copy(alpha = 0.72f) else SwaraColors.Gold.copy(alpha = 0.45f),
                        topLeft = Offset(left, index * laneHeight + 3.dp.toPx()),
                        size = Size(width.coerceAtLeast(4.dp.toPx()), laneHeight - 6.dp.toPx()),
                    )
                }
                if (punchEndMs > punchStartMs) {
                    val left = (punchStartMs / span) * size.width
                    val width = ((punchEndMs - punchStartMs) / span) * size.width
                    drawRect(
                        color = SwaraColors.Clay.copy(alpha = 0.22f),
                        topLeft = Offset(left, 0f),
                        size = Size(width.coerceAtLeast(2.dp.toPx()), size.height),
                    )
                }
                val playX = ((positionMs / span) * size.width).coerceIn(0f, size.width)
                drawLine(
                    color = SwaraColors.Ink,
                    start = Offset(playX, 0f),
                    end = Offset(playX, size.height),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
internal fun LayerEditorRow(
    layer: VoicePartUi,
    selected: Boolean,
    onSelect: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    onRecord: () -> Unit,
    onChain: (VocalPreset?) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${layer.label} · ${layer.clipCount}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) SwaraColors.Teal else SwaraColors.Ink,
            )
            TextButton(onClick = onMute) {
                Text(if (layer.mute) "Muted" else "Mute")
            }
            TextButton(onClick = onSolo) {
                Text(if (layer.solo) "Solo" else "Solo")
            }
            TextButton(onClick = onRecord) { Text("Rec") }
        }
        Text(
            text = if (layer.gainDb >= 0f) "+${layer.gainDb.roundToInt()} dB" else "${layer.gainDb.roundToInt()} dB",
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Muted,
        )
        Slider(
            value = gainDbToVolume(layer.gainDb),
            onValueChange = { onVolumeChange(volumeToGainDb(it)) },
            valueRange = 0.05f..1.5f,
        )
        if (selected) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(VocalPreset.CLOSE, VocalPreset.STAGE, VocalPreset.RADIO, VocalPreset.BRIGHT).forEach { preset ->
                    FilterChip(
                        selected = layer.chainPreset == preset,
                        onClick = { onChain(if (layer.chainPreset == preset) null else preset) },
                        label = { Text(preset.label()) },
                    )
                }
            }
        }
        Surface(onClick = onSelect, color = androidx.compose.ui.graphics.Color.Transparent) {
            Text(
                text = if (selected) "Recording into this layer" else "Tap name row · Rec to punch or stack",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
        }
    }
}

@Composable
internal fun EditorTransportBar(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val span = durationMs.toFloat().coerceAtLeast(1f)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Slider(
            value = positionMs.toFloat().coerceIn(0f, span),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..span,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(positionMs),
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            Spacer(Modifier.width(16.dp))
            Surface(
                onClick = onPlayPause,
                shape = RoundedCornerShape(24.dp),
                color = SwaraColors.TealWash,
            ) {
                Text(
                    text = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = SwaraColors.Teal,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
        }
    }
}
