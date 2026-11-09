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
import com.swaralab.ui.BoothCanvas
import com.swaralab.ui.BoothSearchBar
import com.swaralab.ui.EmptyState
import com.swaralab.ui.GlassCard
import com.swaralab.ui.GlassSurface
import com.swaralab.ui.GlassTrackCard
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.StatusMark
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.SwaraWordmark
import com.swaralab.ui.pressScale
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
        val openedId = appSettings.sessionId()
        lastState.value = rows.firstOrNull { it.id == openedId }?.let { it.id to it.title }
            ?: rows.firstOrNull()?.let { it.id to it.title }
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

    BoothCanvas(intensity = 0.6f, modifier = modifier) {
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
        GlassCard(modifier = Modifier.fillMaxWidth(), padded = false) {
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
            GlassSurface(
                onClick = viewModel::continueLast,
                modifier = Modifier.fillMaxWidth(),
                selected = true,
            ) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                    Text(
                        text = copy.continueEditing,
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
        } ?: GlassSurface(
            onClick = viewModel::openStage,
            modifier = Modifier.fillMaxWidth(),
            selected = true,
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                Text(
                    text = copy.tabHome,
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
        BoothSearchBar(
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
                text = copy.recents,
                style = MaterialTheme.typography.titleMedium,
                color = SwaraColors.Teal,
            )
            recent.forEach { row ->
                GlassTrackCard(
                    title = row.title,
                    detail = boothRowDetail(row),
                    onClick = { viewModel.open(row.id) },
                    actionLabel = copy.listenTake.takeIf { row.takeCount > 0 },
                    onAction = { onListen(row.id) }.takeIf { row.takeCount > 0 },
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
                .padding(20.dp)
                .pressScale(),
            shape = RoundedCornerShape(28.dp),
            color = SwaraColors.Teal,
            shadowElevation = 6.dp,
        ) {
            Text(
                text = "+  ${copy.importTrack}",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                color = SwaraColors.Ink,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

internal fun sessionMatches(row: SessionSummary, query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return row.title.contains(needle, ignoreCase = true) ||
        boothRowDetail(row).contains(needle, ignoreCase = true)
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
