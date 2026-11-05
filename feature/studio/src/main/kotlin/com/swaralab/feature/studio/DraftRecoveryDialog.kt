package com.swaralab.feature.studio

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.swaralab.domain.audio.RecoverableDraft
import com.swaralab.ui.LocalAppCopy

@Composable
fun DraftRecoveryDialog(
    draft: RecoverableDraft,
    onRecover: () -> Unit,
    onDiscard: () -> Unit,
) {
    val copy = LocalAppCopy.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text(copy.unfinishedTake) },
        text = { Text("${copy.unfinishedTakeBody} · ${draft.durationMs / 1000}s") },
        confirmButton = {
            TextButton(onClick = onRecover) { Text(copy.continueLast) }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text(copy.startOver) }
        },
    )
}
