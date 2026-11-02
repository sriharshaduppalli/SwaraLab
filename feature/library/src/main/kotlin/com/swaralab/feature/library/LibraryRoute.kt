package com.swaralab.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.usecase.DeleteSession
import com.swaralab.domain.usecase.ListSessions
import com.swaralab.session.SessionInvitePack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.SwaraColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class LibraryRow(
    val id: String,
    val title: String,
    val detail: String,
    val kind: SessionKind,
)

class LibraryViewModel(
    private val listSessions: ListSessions,
    private val deleteSession: DeleteSession,
    private val invitePack: SessionInvitePack,
) : ViewModel() {
    private val rowsState = MutableStateFlow<List<LibraryRow>>(emptyList())
    val rows: StateFlow<List<LibraryRow>> = rowsState.asStateFlow()
    private val statusState = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = statusState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            rowsState.value = listSessions().map { summary ->
                val day = Instant.ofEpochMilli(summary.updatedAtEpochMs)
                    .atZone(ZoneId.systemDefault())
                    .format(format)
                val takes = if (summary.takeCount == 1) "1 take" else "${summary.takeCount} takes"
                LibraryRow(
                    id = summary.id,
                    title = summary.title,
                    detail = "${summary.kind.label()} · $takes · $day",
                    kind = summary.kind,
                )
            }
        }
    }

    fun importInvite(copyToFile: (File) -> Boolean, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                val temp = File.createTempFile("swaralab-invite", ".zip")
                val copied = copyToFile(temp)
                if (!copied) {
                    temp.delete()
                    return@withContext null
                }
                val document = invitePack.importZip(temp)
                temp.delete()
                document
            }
            if (imported != null) {
                statusState.value = "Invite · ${imported.title} — your part is ready to sing."
                refresh()
                onOpened(imported.id)
            } else {
                statusState.value = "That file is not a SwaraLab invite."
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            deleteSession(id)
            refresh()
        }
    }

    companion object {
        fun factory(
            listSessions: ListSessions,
            deleteSession: DeleteSession,
            invitePack: SessionInvitePack,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(listSessions, deleteSession, invitePack) }
        }
    }
}

private fun SessionKind.label(): String = when (this) {
    SessionKind.PRACTICE -> "Warm-up"
    SessionKind.SONG -> "Song"
    SessionKind.VIDEO -> "Video"
}

@Composable
fun LibraryRoute(
    listSessions: ListSessions,
    deleteSession: DeleteSession,
    invitePack: SessionInvitePack,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(listSessions, deleteSession, invitePack),
    )
    val rows by viewModel.rows.collectAsState()
    val status by viewModel.status.collectAsState()
    val copy = LocalAppCopy.current
    var pendingDelete by remember { mutableStateOf<LibraryRow?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = copy.songs, style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Open a song to sing, edit its lyrics, or listen back to takes.",
            style = MaterialTheme.typography.bodyLarge,
            color = SwaraColors.Muted,
        )
        status?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.startsWith("That file")) SwaraColors.Clay else SwaraColors.Teal,
            )
        }
        val context = LocalContext.current
        val inviteLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                viewModel.importInvite(
                    copyToFile = { dest ->
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                dest.outputStream().use { output -> input.copyTo(output) }
                            } != null
                        }.getOrDefault(false)
                    },
                    onOpened = onOpen,
                )
            }
        }
        OutlinedButton(
            onClick = {
                inviteLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open a friend's duet invite")
        }
        if (rows.isEmpty()) {
            Text(
                text = "No songs yet. Start one from ${copy.sing}.",
                style = MaterialTheme.typography.bodyLarge,
                color = SwaraColors.Muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            rows.forEach { row ->
                val accent = when (row.kind) {
                    SessionKind.SONG -> SwaraColors.Teal
                    SessionKind.VIDEO -> SwaraColors.Rose
                    SessionKind.PRACTICE -> SwaraColors.Gold
                }
                Surface(
                    onClick = { onOpen(row.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = SwaraColors.Card,
                    border = BorderStroke(1.dp, SwaraColors.Line),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 13.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = row.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = SwaraColors.Ink,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = row.detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SwaraColors.Muted,
                                )
                            }
                            TextButton(onClick = { pendingDelete = row }) {
                                Text(copy.delete, color = SwaraColors.Clay)
                            }
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(copy.delete) },
            text = { Text(copy.deleteSongAsk) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.delete(row.id)
                        pendingDelete = null
                    },
                ) { Text(copy.delete) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(copy.cancel) }
            },
        )
    }
}
