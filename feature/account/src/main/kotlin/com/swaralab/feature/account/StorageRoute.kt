package com.swaralab.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.port.SessionSummary
import com.swaralab.domain.usecase.DeleteSession
import com.swaralab.domain.usecase.ListSessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class StorageUi(
    val usedMb: Long = 0L,
    val recordings: List<SessionSummary> = emptyList(),
)

class StorageViewModel(
    private val listSessions: ListSessions,
    private val deleteSession: DeleteSession,
    private val projectsDir: File,
) : ViewModel() {
    private val storage = StorageUseCase(projectsDir)
    private val uiState = MutableStateFlow(StorageUi())
    val ui: StateFlow<StorageUi> = uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val listed = listSessions()
            val used = withContext(Dispatchers.IO) { storage.getTotalUsedMb() }
            uiState.value = StorageUi(usedMb = used, recordings = listed)
        }
    }

    fun deleteRecording(id: String) {
        viewModelScope.launch {
            deleteSession(id)
            refresh()
        }
    }

    fun clearCache(androidCache: File) {
        viewModelScope.launch(Dispatchers.IO) {
            androidCache.walkTopDown().forEach { file ->
                if (file.isFile) file.delete()
            }
            if (projectsDir.isDirectory) {
                projectsDir.listFiles().orEmpty().forEach { session ->
                    File(session, SessionLayout.EXPORTS).listFiles().orEmpty().forEach { file ->
                        val invite = file.name.contains("invite", ignoreCase = true) ||
                            file.extension.equals("swaralab", ignoreCase = true) ||
                            file.name.endsWith(".swaralab.zip", ignoreCase = true)
                        if (file.isFile && !invite) file.delete()
                    }
                }
            }
            refresh()
        }
    }

    companion object {
        fun factory(
            listSessions: ListSessions,
            deleteSession: DeleteSession,
            projectsDir: File,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { StorageViewModel(listSessions, deleteSession, projectsDir) }
        }
    }
}

@Composable
fun StorageRoute(
    listSessions: ListSessions,
    deleteSession: DeleteSession,
    projectsDir: File,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StorageViewModel = viewModel(
        factory = StorageViewModel.factory(listSessions, deleteSession, projectsDir),
    )
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    StorageScreen(
        totalUsedMb = state.usedMb,
        recordings = state.recordings,
        onDelete = viewModel::deleteRecording,
        onClearCache = { viewModel.clearCache(context.cacheDir) },
        onBack = onBack,
        modifier = modifier,
    )
}
