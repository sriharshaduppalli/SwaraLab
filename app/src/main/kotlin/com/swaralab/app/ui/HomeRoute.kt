package com.swaralab.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import com.swaralab.ui.R as UiR
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.port.Analytics
import com.swaralab.domain.port.AnalyticsEvent
import com.swaralab.domain.port.NoOpAnalytics
import com.swaralab.domain.port.SessionSummary
import com.swaralab.domain.usecase.ListSessions
import com.swaralab.domain.usecase.StartSession
import com.swaralab.session.FileAppSettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.swaralab.ui.BoothRoom
import com.swaralab.ui.EmptyState
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.StatusMark
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.SwaraWordmark
import com.swaralab.ui.studioAtmosphere
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel(
    private val startSession: StartSession,
    private val listSessions: ListSessions,
    @Suppress("unused")
    private val appSettings: FileAppSettings,
    private val analytics: Analytics = NoOpAnalytics,
) : ViewModel() {
    private val openedIds = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val opened: SharedFlow<String> = openedIds.asSharedFlow()
    private val lastState = MutableStateFlow<Pair<String, String>?>(null)
    val lastSong: StateFlow<Pair<String, String>?> = lastState.asStateFlow()
    private val listed = MutableStateFlow<List<SessionSummary>>(emptyList())
    private val queryState = MutableStateFlow("")
    val searchQuery: StateFlow<String> = queryState.asStateFlow()
    private val refreshingState = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = refreshingState.asStateFlow()
    val recent: StateFlow<List<SessionSummary>> = combine(
        listed,
        queryState.debounce(300).onStart { emit(queryState.value) },
    ) { rows, query ->
        if (query.isBlank()) rows.take(12) else rows.filter { sessionMatches(it, query) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var busy = false

    init {
        viewModelScope.launch { refreshLast() }
        viewModelScope.launch {
            queryState.debounce(400).collect { query ->
                if (query.length >= 2) {
                    analytics.log(
                        AnalyticsEvent.SEARCH_PERFORMED,
                        mapOf("query_length" to query.length.toString()),
                    )
                }
            }
        }
    }

    fun start(kind: SessionKind) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                openedIds.emit(startSession(kind))
            } finally {
                busy = false
            }
        }
    }

    fun continueLast() {
        val last = lastState.value ?: return
        viewModelScope.launch { openedIds.emit(last.first) }
    }

    fun openStage() {
        if (lastState.value != null) continueLast() else start(SessionKind.SONG)
    }

    fun open(id: String) {
        viewModelScope.launch { openedIds.emit(id) }
    }

    fun onSearchQueryChange(query: String) {
        queryState.value = query
    }

    fun refreshLastSong() {
        viewModelScope.launch { refreshLast() }
    }

    fun refreshFromPull() {
        if (refreshingState.value) return
        viewModelScope.launch {
            refreshingState.value = true
            val started = System.currentTimeMillis()
            refreshLast()
            delay((500L - (System.currentTimeMillis() - started)).coerceAtLeast(0L))
            refreshingState.value = false
        }
    }

    private suspend fun refreshLast() {
        val rows = listSessions()
        lastState.value = rows.firstOrNull()?.let { it.id to it.title }
        listed.value = rows
    }

    companion object {
        fun factory(
            startSession: StartSession,
            listSessions: ListSessions,
            appSettings: FileAppSettings,
            analytics: Analytics = NoOpAnalytics,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(startSession, listSessions, appSettings, analytics) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    startSession: StartSession,
    listSessions: ListSessions,
    appSettings: FileAppSettings,
    onOpen: (String) -> Unit,
    onImport: () -> Unit = {},
    onListen: (String) -> Unit = {},
    analytics: Analytics = NoOpAnalytics,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(startSession, listSessions, appSettings, analytics),
    )
    val copy = LocalAppCopy.current
    val lastSong by viewModel.lastSong.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.opened.collect(onOpen)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLastSong()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier.fillMaxSize().studioAtmosphere(BoothRoom.HOME)) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refreshFromPull,
        modifier = Modifier.fillMaxSize(),
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = SwaraColors.Card,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, SwaraColors.Line),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(UiR.drawable.swaralab_mark),
                    contentDescription = "SwaraLab",
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(72.dp)
                        .alpha(0.28f),
                )
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Box(
                        Modifier
                            .padding(bottom = 10.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .background(SwaraColors.Teal, RoundedCornerShape(4.dp)),
                    )
                    SwaraWordmark()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = copy.todayLine,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SwaraColors.Muted,
                    )
                }
            }
        }
        lastSong?.let { (_, title) ->
            Surface(
                onClick = viewModel::continueLast,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = SwaraColors.TealWash,
                tonalElevation = 2.dp,
                shadowElevation = 3.dp,
                border = BorderStroke(1.5.dp, SwaraColors.Teal),
            ) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                    Text(
                        text = copy.continueLast,
                        style = MaterialTheme.typography.labelLarge,
                        color = SwaraColors.Teal,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(text = title, style = MaterialTheme.typography.headlineLarge, color = SwaraColors.Teal)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = copy.tapToSing,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SwaraColors.Ink,
                    )
                }
            }
        } ?: Surface(
            onClick = viewModel::openStage,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = SwaraColors.TealWash,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
            border = BorderStroke(1.5.dp, SwaraColors.Teal),
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                Text(
                    text = copy.today,
                    style = MaterialTheme.typography.labelLarge,
                    color = SwaraColors.Teal,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = copy.openStage,
                    style = MaterialTheme.typography.headlineLarge,
                    color = SwaraColors.Teal,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = copy.tapToSing,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SwaraColors.Ink,
                )
            }
        }
        HomeSearchField(
            query = searchQuery,
            placeholder = copy.searchSongs,
            onQueryChange = viewModel::onSearchQueryChange,
        )
        if (recent.isEmpty()) {
            if (searchQuery.isBlank()) {
                EmptyState(
                    title = copy.emptySongs,
                    message = copy.emptySongsBody,
                    mark = StatusMark.SONG,
                    actionLabel = copy.importTrack,
                    onAction = onImport,
                    fillMax = false,
                )
            } else {
                EmptyState(
                    title = copy.noSearchResults,
                    message = copy.searchSongs,
                    mark = StatusMark.SEARCH,
                    actionLabel = copy.cancel,
                    onAction = { viewModel.onSearchQueryChange("") },
                    fillMax = false,
                )
            }
        } else {
            Text(
                text = copy.songs,
                style = MaterialTheme.typography.titleMedium,
                color = SwaraColors.Teal,
            )
            recent.forEach { row ->
                HomeTrackRow(
                    title = row.title,
                    detail = boothRowDetail(row),
                    canListen = row.takeCount > 0,
                    listenLabel = copy.listenTake,
                    onOpen = { viewModel.open(row.id) },
                    onListen = { onListen(row.id) },
                )
            }
        }
        Spacer(Modifier.height(72.dp))
    }
    }
        Surface(
            onClick = onImport,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            shape = RoundedCornerShape(28.dp),
            color = SwaraColors.Teal,
            shadowElevation = 6.dp,
        ) {
            Text(
                text = copy.importTrack,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                color = SwaraColors.Ink,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun HomeSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(placeholder) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(onClick = { onQueryChange("") }) { Text("×") }
            }
        },
    )
}

internal fun sessionMatches(row: SessionSummary, query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return row.title.contains(needle, ignoreCase = true) ||
        boothRowDetail(row).contains(needle, ignoreCase = true)
}

@Composable
private fun HomeTrackRow(
    title: String,
    detail: String,
    canListen: Boolean,
    listenLabel: String,
    onOpen: () -> Unit,
    onListen: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, SwaraColors.Line),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = SwaraColors.Ink)
                Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = SwaraColors.Muted)
            }
            if (canListen) {
                TextButton(onClick = onListen) { Text(listenLabel) }
            }
        }
    }
}

internal fun boothRowDetail(row: SessionSummary): String {
    val day = Instant.ofEpochMilli(row.updatedAtEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val takes = if (row.takeCount == 1) "1 take" else "${row.takeCount} takes"
    val kind = when (row.kind) {
        SessionKind.PRACTICE -> "Warm-up"
        SessionKind.SONG -> "Song"
        SessionKind.VIDEO -> "Video"
    }
    return "$kind · $takes · $day"
}
