package com.swaralab.feature.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swaralab.domain.port.SessionSummary
import com.swaralab.ui.BoothRoom
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.studioAtmosphere
import java.io.File

@Composable
fun StorageScreen(
    totalUsedMb: Long,
    recordings: List<SessionSummary>,
    onDelete: (String) -> Unit,
    onClearCache: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = LocalAppCopy.current
    BackHandler(onBack = onBack)
    Column(
        modifier
            .fillMaxSize()
            .studioAtmosphere(BoothRoom.PROFILE)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Text(copy.back)
        }
        Text(copy.storage, style = MaterialTheme.typography.headlineSmall, color = SwaraColors.Ink)
        Text(
            text = "${copy.usedStorage}: $totalUsedMb MB",
            style = MaterialTheme.typography.titleMedium,
            color = SwaraColors.Teal,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
            Text(copy.clearCache)
        }
        Text(
            "Takes stay. Mix exports and Android cache go.",
            style = MaterialTheme.typography.bodySmall,
            color = SwaraColors.Muted,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(recordings, key = { it.id }) { rec ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(rec.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${rec.takeCount} ${copy.takes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwaraColors.Muted,
                        )
                    }
                    TextButton(onClick = { onDelete(rec.id) }) {
                        Text(copy.delete)
                    }
                }
            }
        }
    }
}

class StorageUseCase(
    private val projectsDir: File,
) {
    fun getTotalUsedMb(): Long {
        if (!projectsDir.isDirectory) return 0L
        val bytes = projectsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return bytes / (1024L * 1024L)
    }
}
