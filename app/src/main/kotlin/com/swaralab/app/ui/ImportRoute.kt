package com.swaralab.app.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.app.media.AndroidMetadataReader
import com.swaralab.domain.audio.AudioMetadataReader
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.ImportedTrackRecord
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.model.SingMode
import com.swaralab.domain.port.Analytics
import com.swaralab.domain.port.AnalyticsEvent
import com.swaralab.domain.port.NoOpAnalytics
import com.swaralab.domain.port.TakeCatalog
import com.swaralab.domain.usecase.ImportBackingTrack
import com.swaralab.domain.usecase.RenameSession
import com.swaralab.domain.usecase.StartSession
import com.swaralab.ui.BoothRoom
import com.swaralab.ui.ErrorState
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.MediaPermissions
import com.swaralab.ui.PermissionRationale
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.studioAtmosphere
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ImportState {
    data object Idle : ImportState
    data object Processing : ImportState
    data class Ready(val sessionId: String, val title: String) : ImportState
    data class Error(val message: String) : ImportState
}

class ImportViewModel(
    private val projectsDir: File,
    private val startSession: StartSession,
    private val importBackingTrack: ImportBackingTrack,
    private val renameSession: RenameSession,
    private val takeCatalog: TakeCatalog?,
    private val metadata: AudioMetadataReader,
    private val copyStream: (Uri, File) -> Boolean,
    private val displayNameOf: (Uri) -> String,
    private val analytics: Analytics = NoOpAnalytics,
) : ViewModel() {
    private val states = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = states.asStateFlow()

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            states.value = ImportState.Processing
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val sessionId = startSession(SessionKind.SONG)
                    val rawName = displayNameOf(uri)
                    val cleanName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "backing.mp3" }
                    val relative = SessionLayout.backingFile(cleanName)
                    val dest = File(File(projectsDir, sessionId), relative)
                    dest.parentFile?.mkdirs()
                    if (!copyStream(uri, dest)) error("Could not copy that file.")
                    val meta = metadata.read(dest.absolutePath)
                    val title = meta.title?.takeIf { it.isNotBlank() } ?: rawName.substringBeforeLast('.')
                    importBackingTrack(
                        sessionId,
                        relative,
                        meta.durationMs.coerceAtLeast(1_000L),
                        title,
                        rightsConfirmed = true,
                    )
                    if (title.isNotBlank()) renameSession(sessionId, title)
                    takeCatalog?.upsertTrack(
                        sessionId,
                        ImportedTrackRecord(
                            id = relative,
                            title = title,
                            artist = meta.artist,
                            durationMs = meta.durationMs.coerceAtLeast(1_000L),
                            localRelativePath = relative,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                    analytics.log(
                        AnalyticsEvent.TRACK_IMPORTED,
                        mapOf(
                            "duration_ms" to meta.durationMs.toString(),
                            "has_artist" to (!meta.artist.isNullOrBlank()).toString(),
                        ),
                    )
                    sessionId to title
                }
            }
            states.value = result.fold(
                onSuccess = { (id, title) -> ImportState.Ready(id, title) },
                onFailure = { ImportState.Error(it.message ?: "This audio file cannot be used.") },
            )
        }
    }

    companion object {
        fun factory(
            projectsDir: File,
            startSession: StartSession,
            importBackingTrack: ImportBackingTrack,
            renameSession: RenameSession,
            takeCatalog: TakeCatalog?,
            metadata: AudioMetadataReader,
            copyStream: (Uri, File) -> Boolean,
            displayNameOf: (Uri) -> String,
            analytics: Analytics = NoOpAnalytics,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ImportViewModel(
                    projectsDir,
                    startSession,
                    importBackingTrack,
                    renameSession,
                    takeCatalog,
                    metadata,
                    copyStream,
                    displayNameOf,
                    analytics,
                )
            }
        }
    }
}

@Composable
fun ImportRoute(
    projectsDir: File,
    startSession: StartSession,
    importBackingTrack: ImportBackingTrack,
    renameSession: RenameSession,
    takeCatalog: TakeCatalog?,
    onTrackReady: (sessionId: String) -> Unit,
    onBack: () -> Unit,
    analytics: Analytics = NoOpAnalytics,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copy = LocalAppCopy.current
    val viewModel: ImportViewModel = viewModel(
        factory = ImportViewModel.factory(
            projectsDir = projectsDir,
            startSession = startSession,
            importBackingTrack = importBackingTrack,
            renameSession = renameSession,
            takeCatalog = takeCatalog,
            metadata = AndroidMetadataReader(),
            copyStream = { uri, dest ->
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                } != null
            },
            displayNameOf = { uri ->
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                } ?: uri.lastPathSegment ?: "backing.mp3"
            },
            analytics = analytics,
        ),
    )
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state) {
        val ready = state as? ImportState.Ready ?: return@LaunchedEffect
        onTrackReady(ready.sessionId)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::onFilePicked)
    }
    var showReadRationale by remember { mutableStateOf(false) }
    val readPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            picker.launch("audio/*")
        } else {
            showReadRationale = true
        }
    }

    fun openPicker() {
        picker.launch("audio/*")
    }

    fun chooseAudio() {
        if (MediaPermissions.hasAudioRead(context)) {
            openPicker()
        } else {
            readPermission.launch(MediaPermissions.audioReadPermission())
        }
    }

    if (showReadRationale) {
        PermissionRationale(
            title = copy.singFromPhone,
            message = copy.storageNeeded,
            grantLabel = copy.grantPermission,
            denyLabel = copy.notNow,
            onGrant = {
                showReadRationale = false
                readPermission.launch(MediaPermissions.audioReadPermission())
            },
            onDeny = {
                showReadRationale = false
                openPicker()
            },
            modifier = modifier.studioAtmosphere(BoothRoom.HOME),
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .studioAtmosphere(BoothRoom.HOME)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Song from this phone", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pick a file you can use. It stays on this phone. Then you sing over it.",
            color = SwaraColors.Muted,
        )
        when (val current = state) {
            ImportState.Processing -> CircularProgressIndicator(color = SwaraColors.Teal)
            is ImportState.Error -> ErrorState(
                title = "Import failed",
                message = current.message,
                retryLabel = "Choose audio file",
                onRetry = ::chooseAudio,
                secondaryLabel = copy.back,
                onSecondary = onBack,
                fillMax = false,
            )
            else -> {
                Button(onClick = ::chooseAudio) { Text("Choose audio file") }
                TextButton(onClick = onBack) { Text(copy.back) }
            }
        }
    }
}

fun importStudioRoute(sessionId: String): String = "studio/$sessionId?sing=${SingMode.FROM_PHONE.name}"
