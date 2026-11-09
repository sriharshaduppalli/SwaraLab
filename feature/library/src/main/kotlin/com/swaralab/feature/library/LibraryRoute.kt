package com.swaralab.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.swaralab.ui.BoothCanvas
import com.swaralab.ui.EmptyState
import com.swaralab.ui.GlassSurface
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.StatusMark
import com.swaralab.ui.PageHero
import com.swaralab.ui.SwaraColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
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
    val takeCount: Int = 0,
    val lastHeard: Boolean = false,
)

class LibraryViewModel(
    private val listSessions: ListSessions,
    private val deleteSession: DeleteSession,
) : ViewModel() {
    private val rowsState = MutableStateFlow<List<LibraryRow>>(emptyList())
    val rows: StateFlow<List<LibraryRow>> = rowsState.asStateFlow()
    private val statusState = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = statusState.asStateFlow()
    private val refreshingState = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = refreshingState.asStateFlow()

    fun refresh(fromPull: Boolean = false) {
        if (fromPull && refreshingState.value) return
        viewModelScope.launch {
            if (fromPull) refreshingState.value = true
            val started = System.currentTimeMillis()
            val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            val listed = listSessions()
            val lastId = listed.maxByOrNull { it.updatedAtEpochMs }?.id
            rowsState.value = listed.map { summary ->
                val day = Instant.ofEpochMilli(summary.updatedAtEpochMs)
                    .atZone(ZoneId.systemDefault())
                    .format(format)
                val takes = if (summary.takeCount == 1) "1 take" else "${summary.takeCount} takes"
                LibraryRow(
                    id = summary.id,
                    title = summary.title,
                    detail = "${summary.kind.label()} · $takes · $day",
                    kind = summary.kind,
                    takeCount = summary.takeCount,
                    lastHeard = summary.id == lastId,
                )
            }
            if (fromPull) {
                delay((500L - (System.currentTimeMillis() - started)).coerceAtLeast(0L))
                refreshingState.value = false
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
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(listSessions, deleteSession) }
        }
    }
}

private fun SessionKind.label(): String = when (this) {
    SessionKind.PRACTICE -> "Warm-up"
    SessionKind.SONG -> "Song"
    SessionKind.VIDEO -> "Video"
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryRoute(
    listSessions: ListSessions,
    deleteSession: DeleteSession,
    onOpen: (String) -> Unit,
    onListen: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(listSessions, deleteSession),
    )
    val rows by viewModel.rows.collectAsState()
    val status by viewModel.status.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
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

    var kindFilter by remember { mutableStateOf<SessionKind?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val visible = if (kindFilter == null) rows else rows.filter { it.kind == kindFilter }

    BoothCanvas(intensity = 0.5f, modifier = modifier) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh(fromPull = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHero(
            title = copy.tabLibrary,
            body = copy.songsLine,
            accent = SwaraColors.Gold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = kindFilter == null,
                onClick = { kindFilter = null },
                label = { Text("All") },
            )
            FilterChip(
                selected = kindFilter == SessionKind.SONG,
                onClick = { kindFilter = SessionKind.SONG },
                label = { Text("Songs") },
            )
            FilterChip(
                selected = kindFilter == SessionKind.VIDEO,
                onClick = { kindFilter = SessionKind.VIDEO },
                label = { Text("Video") },
            )
            FilterChip(
                selected = kindFilter == SessionKind.PRACTICE,
                onClick = { kindFilter = SessionKind.PRACTICE },
                label = { Text("Warm-up") },
            )
        }
        status?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.startsWith("That file")) SwaraColors.Clay else SwaraColors.Teal,
            )
        }
        if (rows.isEmpty()) {
            EmptyState(
                title = copy.emptySongs,
                message = copy.emptySongsBody,
                mark = StatusMark.SONG,
                fillMax = false,
            )
        } else if (visible.isEmpty()) {
            EmptyState(
                title = copy.noSearchResults,
                message = "Nothing in this filter yet.",
                mark = StatusMark.SEARCH,
                fillMax = false,
            )
        } else {
            visible.forEach { row ->
                val accent = when (row.kind) {
                    SessionKind.SONG -> SwaraColors.Teal
                    SessionKind.VIDEO -> SwaraColors.Rose
                    SessionKind.PRACTICE -> SwaraColors.Gold
                }
                GlassSurface(
                    onClick = { onOpen(row.id) },
                    modifier = Modifier.fillMaxWidth(),
                    accentBorder = SwaraColors.Line,
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
                                if (row.lastHeard) {
                                    Text(
                                        text = "Last heard",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SwaraColors.Gold,
                                    )
                                }
                            }
                            Box {
                                TextButton(onClick = { menuFor = row.id }) {
                                    Text("···")
                                }
                                DropdownMenu(
                                    expanded = menuFor == row.id,
                                    onDismissRequest = { menuFor = null },
                                ) {
                                    if (row.takeCount > 0) {
                                        DropdownMenuItem(
                                            text = { Text(copy.listenTake) },
                                            onClick = {
                                                menuFor = null
                                                onListen(row.id)
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(copy.delete, color = SwaraColors.Clay) },
                                        onClick = {
                                            menuFor = null
                                            pendingDelete = row
                                        },
                                    )
                                }
                            }
                        }
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
