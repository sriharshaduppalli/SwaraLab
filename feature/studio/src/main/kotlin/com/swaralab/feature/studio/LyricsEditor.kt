package com.swaralab.feature.studio

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.usecase.encodeLyricLines
import com.swaralab.domain.usecase.parseLyricClock
import com.swaralab.ui.SwaraColors
import java.util.UUID
import kotlinx.coroutines.delay

data class EditableLyricLine(
    val id: String = UUID.randomUUID().toString(),
    val startTimeMs: Long,
    val text: String,
)

@Composable
fun LyricsEditor(
    initialLines: List<LyricLine>,
    onSave: (String) -> Unit,
    extraActions: @Composable () -> Unit = {},
) {
    var lines by remember(initialLines) {
        mutableStateOf(
            if (initialLines.isEmpty()) {
                listOf(EditableLyricLine(startTimeMs = 0L, text = ""))
            } else {
                initialLines.map { line ->
                    EditableLyricLine(
                        id = line.id,
                        startTimeMs = line.startMs ?: 0L,
                        text = line.text,
                    )
                }
            },
        )
    }
    var tapSyncRunning by remember { mutableStateOf(false) }
    var tapSyncStartRealtime by remember { mutableLongStateOf(0L) }
    var tapSyncElapsedMs by remember { mutableLongStateOf(0L) }
    var tapSyncLineIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(tapSyncRunning) {
        if (!tapSyncRunning) return@LaunchedEffect
        while (tapSyncRunning) {
            tapSyncElapsedMs = (SystemClock.elapsedRealtime() - tapSyncStartRealtime).coerceAtLeast(0L)
            delay(100L)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "One line per row. Times can be rough — every 4 or 5 seconds is enough, or use Tap-Sync below.",
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Muted,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!tapSyncRunning) {
                OutlinedButton(
                    onClick = {
                        tapSyncLineIndex = 0
                        tapSyncStartRealtime = SystemClock.elapsedRealtime()
                        tapSyncElapsedMs = 0L
                        tapSyncRunning = true
                    },
                ) {
                    Text("Start Tap-Sync timer")
                }
                OutlinedButton(
                    onClick = {
                        lines = lines.mapIndexed { idx, row ->
                            row.copy(startTimeMs = idx * 4_500L)
                        }
                    },
                ) {
                    Text("Auto-space 4.5s")
                }
            } else {
                val targetIdx = tapSyncLineIndex.coerceIn(0, lines.lastIndex)
                Button(
                    onClick = {
                        val stamped = tapSyncElapsedMs
                        lines = lines.toMutableList().apply {
                            this[targetIdx] = this[targetIdx].copy(startTimeMs = stamped)
                        }
                        if (targetIdx + 1 < lines.size) {
                            tapSyncLineIndex = targetIdx + 1
                        } else {
                            tapSyncRunning = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Stamp line ${targetIdx + 1} (${formatEditorTime(tapSyncElapsedMs)})")
                }
                TextButton(onClick = { tapSyncRunning = false }) {
                    Text("Done")
                }
            }
        }
        lines.forEachIndexed { index, line ->
            LyricEditorItem(
                line = line,
                onTextChange = { newText ->
                    lines = lines.toMutableList().apply { this[index] = line.copy(text = newText) }
                },
                onTimeChange = { newTime ->
                    lines = lines.toMutableList().apply { this[index] = line.copy(startTimeMs = newTime) }
                },
                onDelete = {
                    lines = lines.toMutableList().apply { removeAt(index) }
                    if (lines.isEmpty()) {
                        lines = listOf(EditableLyricLine(startTimeMs = 0L, text = ""))
                    }
                },
            )
        }
        OutlinedButton(
            onClick = {
                lines = lines + EditableLyricLine(
                    startTimeMs = lines.lastOrNull()?.startTimeMs?.plus(5_000L) ?: 0L,
                    text = "",
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add line")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val saved = lines
                        .filter { it.text.isNotBlank() }
                        .map { LyricLine(id = it.id, text = it.text.trim(), startMs = it.startTimeMs) }
                    onSave(encodeLyricLines(saved))
                },
            ) {
                Text("Save lyrics")
            }
            extraActions()
        }
    }
}

@Composable
private fun LyricEditorItem(
    line: EditableLyricLine,
    onTextChange: (String) -> Unit,
    onTimeChange: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    var timeText by remember(line.id, line.startTimeMs) {
        mutableStateOf(formatEditorTime(line.startTimeMs))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = timeText,
            onValueChange = { value ->
                timeText = value
                parseLyricClock(value)?.let(onTimeChange)
            },
            modifier = Modifier.width(88.dp),
            singleLine = true,
            label = { Text("Time") },
        )
        OutlinedTextField(
            value = line.text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text("Line") },
        )
        TextButton(onClick = onDelete) { Text("Del") }
    }
}

private fun formatEditorTime(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val minutes = clamped / 60_000L
    val seconds = (clamped % 60_000L) / 1_000L
    val hundredths = (clamped % 1_000L) / 10L
    return if (hundredths == 0L) {
        "%d:%02d".format(minutes, seconds)
    } else {
        "%d:%02d.%02d".format(minutes, seconds, hundredths)
    }
}
