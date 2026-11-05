package com.swaralab.feature.studio

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swaralab.domain.model.LyricLine
import com.swaralab.domain.usecase.encodeLyricLines
import com.swaralab.ui.SwaraColors
import java.util.UUID

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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "One line per row. Times can be rough — every 4 or 5 seconds is enough.",
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Muted,
        )
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
                parseEditorTime(value)?.let(onTimeChange)
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
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60L, total % 60L)
}

private fun parseEditorTime(raw: String): Long? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(':', '.')
    return when (parts.size) {
        1 -> parts[0].toLongOrNull()?.times(1_000L)
        2 -> {
            val minutes = parts[0].toLongOrNull() ?: return null
            val seconds = parts[1].toLongOrNull() ?: return null
            (minutes * 60_000L) + (seconds * 1_000L)
        }
        else -> null
    }
}
