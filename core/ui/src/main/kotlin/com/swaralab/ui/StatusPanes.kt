package com.swaralab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lightweight marks so we do not pull a Material Icons catalog. */
object StatusMark {
    const val SONG = "♪"
    const val SEARCH = "?"
    const val MIC = "●"
    const val ERROR = "!"
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    mark: String = StatusMark.SONG,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    fillMax: Boolean = true,
) {
    StatusPane(
        mark = mark,
        markColor = SwaraColors.Teal,
        wash = SwaraColors.TealWash,
        title = title,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
        fillMax = fillMax,
        modifier = modifier,
    )
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong",
    retryLabel: String = "Try again",
    onRetry: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    fillMax: Boolean = true,
) {
    StatusPane(
        mark = StatusMark.ERROR,
        markColor = SwaraColors.Clay,
        wash = SwaraColors.Clay.copy(alpha = 0.16f),
        title = title,
        message = message,
        actionLabel = retryLabel.takeIf { onRetry != null },
        onAction = onRetry,
        secondaryLabel = secondaryLabel,
        onSecondary = onSecondary,
        fillMax = fillMax,
        modifier = modifier,
    )
}

@Composable
private fun StatusPane(
    mark: String,
    markColor: Color,
    wash: Color,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    fillMax: Boolean = true,
) {
    Column(
        modifier
            .then(if (fillMax) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (fillMax) Arrangement.Center else Arrangement.spacedBy(0.dp),
    ) {
        Box(
            Modifier
                .size(120.dp)
                .background(wash, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(mark, fontSize = 44.sp, color = markColor)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = SwaraColors.Ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = SwaraColors.Muted,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(28.dp))
            Surface(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SwaraColors.Teal,
            ) {
                Text(
                    actionLabel,
                    modifier = Modifier.padding(vertical = 14.dp),
                    textAlign = TextAlign.Center,
                    color = SwaraColors.Ink,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSecondary) { Text(secondaryLabel, color = SwaraColors.Muted) }
        }
    }
}
