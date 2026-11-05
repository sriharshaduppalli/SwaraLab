package com.swaralab.feature.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.model.Account
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.port.SessionSummary
import com.swaralab.domain.usecase.AccountReject
import com.swaralab.domain.usecase.AccountResult
import com.swaralab.domain.usecase.CurrentAccount
import com.swaralab.domain.usecase.DeleteSession
import com.swaralab.domain.usecase.ListSessions
import com.swaralab.domain.usecase.RegisterAccount
import com.swaralab.domain.usecase.SignIn
import com.swaralab.domain.usecase.SignOut
import com.swaralab.domain.port.InvitePack
import com.swaralab.ui.AppLanguage
import com.swaralab.ui.BoothRoom
import com.swaralab.ui.EmptyState
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.LocalTakeShare
import com.swaralab.ui.StatusMark
import com.swaralab.ui.PageHero
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.SwaraThemeId
import com.swaralab.ui.ThemeSwatchGrid
import com.swaralab.ui.studioAtmosphere
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class AccountMode {
    REGISTER,
    SIGN_IN,
}

enum class YouPageTab {
    TAKES,
    DUETS,
    KINDS,
    STUDIO,
}

data class BoothStats(
    val songs: Int = 0,
    val takes: Int = 0,
    val inTunePercent: Int = 0,
    val sessions: List<SessionSummary> = emptyList(),
    val invites: List<String> = emptyList(),
)

data class AccountForm(
    val mode: AccountMode = AccountMode.REGISTER,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val error: String? = null,
    val busy: Boolean = false,
    val signedIn: Account? = null,
)

class AccountViewModel(
    private val registerAccount: RegisterAccount,
    private val signIn: SignIn,
    private val signOutAccount: SignOut,
    private val currentAccount: CurrentAccount,
    private val listSessions: ListSessions,
    private val deleteSession: DeleteSession,
    private val invitePack: InvitePack,
    private val projectsDir: File,
) : ViewModel() {
    private val storage = StorageUseCase(projectsDir)
    private val formState = MutableStateFlow(AccountForm())
    val form: StateFlow<AccountForm> = formState.asStateFlow()
    private val statsState = MutableStateFlow(BoothStats())
    val stats: StateFlow<BoothStats> = statsState.asStateFlow()
    private val statusState = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = statusState.asStateFlow()
    private val noticeState = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = noticeState.asStateFlow()
    private val usedMbState = MutableStateFlow(0L)
    val usedMb: StateFlow<Long> = usedMbState.asStateFlow()

    init {
        viewModelScope.launch {
            formState.update { it.copy(signedIn = currentAccount()) }
            refreshBooth()
        }
    }

    fun refreshBooth() {
        viewModelScope.launch {
            val listed = listSessions()
            val packs = withContext(Dispatchers.IO) { listInvitePacks(projectsDir) }
            statsState.value = BoothStats(
                songs = listed.size,
                takes = listed.sumOf { it.takeCount },
                inTunePercent = listed.maxOfOrNull { it.lastPracticeScore } ?: 0,
                sessions = listed,
                invites = packs,
            )
            usedMbState.value = withContext(Dispatchers.IO) { storage.getTotalUsedMb() }
        }
    }

    fun deleteRecording(id: String) {
        viewModelScope.launch {
            deleteSession(id)
            refreshBooth()
        }
    }

    fun importInvite(copyToFile: (File) -> Boolean, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                val temp = File.createTempFile("swaralab-invite", ".zip")
                val copied = copyToFile(temp)
                val document = if (copied) invitePack.importZip(temp) else null
                temp.delete()
                document
            }
            if (imported != null) {
                statusState.value = "Invite · ${imported.title}"
                refreshBooth()
                onOpened(imported.id)
            } else {
                statusState.value = "That file is not a SwaraLab invite."
            }
        }
    }

    fun onName(value: String) = formState.update { it.copy(name = value, error = null) }

    fun onEmail(value: String) = formState.update { it.copy(email = value, error = null) }

    fun onPassword(value: String) = formState.update { it.copy(password = value, error = null) }

    fun switchMode() {
        formState.update {
            it.copy(
                mode = if (it.mode == AccountMode.REGISTER) AccountMode.SIGN_IN else AccountMode.REGISTER,
                password = "",
                error = null,
            )
        }
    }

    fun submit() {
        val current = formState.value
        if (current.busy || current.signedIn != null) return
        formState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = if (current.mode == AccountMode.REGISTER) {
                    registerAccount(current.name, current.email, current.password)
                } else {
                    signIn(current.email, current.password)
                }
                when (result) {
                    is AccountResult.Ready -> formState.update {
                        it.copy(busy = false, signedIn = result.account, password = "", error = null)
                    }
                    is AccountResult.Rejected -> formState.update {
                        it.copy(busy = false, error = result.reason.message())
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                formState.update {
                    it.copy(busy = false, error = "The account file on this phone could not be read.")
                }
            }
        }
    }

    fun clearMixCache(androidCache: File) {
        viewModelScope.launch(Dispatchers.IO) {
            var removed = 0
            androidCache.walkTopDown().forEach { file ->
                if (file.isFile && file.delete()) removed++
            }
            if (projectsDir.isDirectory) {
                projectsDir.listFiles().orEmpty().forEach { session ->
                    File(session, SessionLayout.EXPORTS).listFiles().orEmpty().forEach { file ->
                        val invite = file.name.contains("invite", ignoreCase = true) ||
                            file.extension.equals("swaralab", ignoreCase = true) ||
                            file.name.endsWith(".swaralab.zip", ignoreCase = true)
                        if (file.isFile && !invite && file.delete()) removed++
                    }
                }
            }
            noticeState.value = "cleared:$removed"
        }
    }

    fun signOut() {
        if (formState.value.busy) return
        viewModelScope.launch {
            signOutAccount()
            formState.update {
                AccountForm(mode = AccountMode.SIGN_IN)
            }
        }
    }

    companion object {
        fun factory(
            registerAccount: RegisterAccount,
            signIn: SignIn,
            signOut: SignOut,
            currentAccount: CurrentAccount,
            listSessions: ListSessions,
            deleteSession: DeleteSession,
            invitePack: InvitePack,
            projectsDir: File,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AccountViewModel(
                    registerAccount,
                    signIn,
                    signOutAccount = signOut,
                    currentAccount,
                    listSessions,
                    deleteSession,
                    invitePack,
                    projectsDir,
                )
            }
        }
    }
}

private fun AccountReject.message(): String = when (this) {
    AccountReject.EMAIL_INVALID -> "Enter a valid email address."
    AccountReject.PASSWORD_TOO_SHORT -> "Use at least 8 characters."
    AccountReject.NAME_MISSING -> "Enter the name you sing under."
    AccountReject.EMAIL_TAKEN -> "That email is already registered on this phone."
    AccountReject.UNKNOWN_EMAIL -> "No account on this phone uses that email."
    AccountReject.WRONG_PASSWORD -> "That password does not match."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountRoute(
    registerAccount: RegisterAccount,
    signIn: SignIn,
    signOut: SignOut,
    currentAccount: CurrentAccount,
    listSessions: ListSessions,
    deleteSession: DeleteSession,
    invitePack: InvitePack,
    projectsDir: File,
    onOpen: (String) -> Unit,
    onListen: (String) -> Unit = {},
    onStorage: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    appLanguage: AppLanguage,
    onAppLanguage: (AppLanguage) -> Unit,
    themeId: SwaraThemeId,
    onTheme: (SwaraThemeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AccountViewModel = viewModel(
        factory = AccountViewModel.factory(
            registerAccount,
            signIn,
            signOut,
            currentAccount,
            listSessions,
            deleteSession,
            invitePack,
            projectsDir,
        ),
    )
    val form by viewModel.form.collectAsState()
    val booth by viewModel.stats.collectAsState()
    val inviteStatus by viewModel.status.collectAsState()
    val notice by viewModel.notice.collectAsState()
    var aboutOpen by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    var licensesOpen by remember { mutableStateOf(false) }
    val signedIn = form.signedIn
    val copy = LocalAppCopy.current
    var page by remember { mutableStateOf(YouPageTab.TAKES) }
    var setFilter by remember { mutableStateOf(SessionKind.SONG) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshBooth()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    Column(
        modifier
            .fillMaxSize()
            .studioAtmosphere(BoothRoom.PROFILE)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHero(
            title = signedIn?.displayName ?: copy.singerOnPhone,
            body = copy.singerLine,
            accent = SwaraColors.Rose,
        )
        Text(
            text = copy.rightsNote,
            style = MaterialTheme.typography.bodyMedium,
            color = SwaraColors.Muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatPill(copy.songs, booth.songs.toString(), Modifier.weight(1f))
            StatPill(copy.takes, booth.takes.toString(), Modifier.weight(1f))
            StatPill(
                copy.inTune,
                if (booth.inTunePercent > 0) "${booth.inTunePercent}%" else "—",
                Modifier.weight(1f),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            YouPageTab.entries.forEach { tab ->
                FilterChip(
                    selected = page == tab,
                    onClick = { page = tab },
                    label = {
                        Text(
                            when (tab) {
                                YouPageTab.TAKES -> copy.takes
                                YouPageTab.DUETS -> copy.duets
                                YouPageTab.KINDS -> copy.kinds
                                YouPageTab.STUDIO -> copy.studio
                            },
                        )
                    },
                )
            }
        }
        when (page) {
            YouPageTab.TAKES -> {
                if (booth.sessions.isEmpty()) {
                    EmptyState(
                        title = copy.emptySongs,
                        message = copy.noTakeYet,
                        mark = StatusMark.MIC,
                        fillMax = false,
                    )
                } else {
                    booth.sessions.take(12).forEach { row ->
                        SessionCard(row, onOpen, onListen, onShare = {
                            LocalTakeShare.open(
                                context,
                                LocalTakeShare.latestTakeFile(File(projectsDir, row.id)),
                                row.title,
                            )
                        })
                    }
                }
            }
            YouPageTab.DUETS -> {
                OutlinedButton(
                    onClick = {
                        inviteLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(copy.inviteFriend)
                }
                if (booth.invites.isEmpty()) {
                    Text(copy.inviteEmpty, color = SwaraColors.Muted)
                } else {
                    booth.invites.forEach { name ->
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                inviteStatus?.let { Text(it, color = SwaraColors.Teal) }
            }
            YouPageTab.KINDS -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SessionKind.entries.forEach { kind ->
                        FilterChip(
                            selected = setFilter == kind,
                            onClick = { setFilter = kind },
                            label = {
                                Text(
                                    when (kind) {
                                        SessionKind.SONG -> copy.songs
                                        SessionKind.VIDEO -> copy.videoTitle
                                        SessionKind.PRACTICE -> copy.warmupTitle
                                    },
                                )
                            },
                        )
                    }
                }
                val setRows = booth.sessions.filter { it.kind == setFilter }
                if (setRows.isEmpty()) {
                    EmptyState(
                        title = copy.noSearchResults,
                        message = "Nothing in this kind yet.",
                        mark = StatusMark.SEARCH,
                        fillMax = false,
                    )
                } else {
                    setRows.forEach { row ->
                        SessionCard(row, onOpen, onListen, onShare = {
                            LocalTakeShare.open(
                                context,
                                LocalTakeShare.latestTakeFile(File(projectsDir, row.id)),
                                row.title,
                            )
                        })
                    }
                }
            }
            YouPageTab.STUDIO -> {
        Text(
            text = copy.appLanguage,
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppLanguage.entries.forEach { language ->
                FilterChip(
                    selected = language == appLanguage,
                    onClick = { onAppLanguage(language) },
                    label = { Text(language.label) },
                )
            }
        }
        Text(
            text = copy.appTheme,
            style = MaterialTheme.typography.titleMedium,
        )
        ThemeSwatchGrid(selected = themeId, onSelect = onTheme)
        Text(text = copy.settings, style = MaterialTheme.typography.titleMedium)
        SettingsRow(copy.storage, copy.storageBody) { onStorage() }
        SettingsRow(copy.clearCache) { viewModel.clearMixCache(context.cacheDir) }
        SettingsRow(copy.replayOnboarding, onClick = onReplayOnboarding)
        SettingsRow(copy.about) { aboutOpen = true }
        SettingsRow(copy.privacy) { privacyOpen = true }
        SettingsRow(copy.licenses) { licensesOpen = true }
        if (notice?.startsWith("cleared:") == true) {
            Text(copy.cacheCleared, color = SwaraColors.Teal)
        }
        Text(
            text = "Account on this phone",
            style = MaterialTheme.typography.titleMedium,
        )
        if (signedIn != null) {
            Text(text = signedIn.displayName, style = MaterialTheme.typography.titleLarge)
            Text(
                text = signedIn.email,
                style = MaterialTheme.typography.bodyLarge,
                color = SwaraColors.Muted,
            )
            Text(
                text = "This name stays on this phone. Your songs are not uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out")
            }
        } else {
            Text(
                text = if (form.mode == AccountMode.REGISTER) {
                    "Optional. The studio works without an account."
                } else {
                    "Sign in to the account already on this phone."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = SwaraColors.Muted,
            )
            if (form.mode == AccountMode.REGISTER) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = viewModel::onName,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Name") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }
            OutlinedTextField(
                value = form.email,
                onValueChange = viewModel::onEmail,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = form.password,
                onValueChange = viewModel::onPassword,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )
            form.error?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = viewModel::submit,
                enabled = !form.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (form.mode == AccountMode.REGISTER) "Create account" else "Sign in")
            }
            TextButton(onClick = viewModel::switchMode, enabled = !form.busy) {
                Text(
                    if (form.mode == AccountMode.REGISTER) {
                        "Already registered? Sign in"
                    } else {
                        "New here? Create an account"
                    },
                )
            }
        }
            }
        }
    }
    if (aboutOpen) {
        val version = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "—"
        }
        AlertDialog(
            onDismissRequest = { aboutOpen = false },
            title = { Text(copy.about) },
            text = {
                Text("SwaraLab $version. Local singer studio. Apache-2.0. Takes stay on this phone.")
            },
            confirmButton = {
                TextButton(onClick = { aboutOpen = false }) { Text(copy.gotIt) }
            },
        )
    }
    if (privacyOpen) {
        AlertDialog(
            onDismissRequest = { privacyOpen = false },
            title = { Text(copy.privacy) },
            text = {
                Text(
                    "SwaraLab does not collect personal data and does not send it to a server. " +
                        "Sessions, takes, and imported tracks stay in this app’s private storage. " +
                        "Share uses the sheet on this phone. Questions: bysel.trader@gmail.com",
                )
            },
            confirmButton = {
                TextButton(onClick = { privacyOpen = false }) { Text(copy.gotIt) }
            },
        )
    }
    if (licensesOpen) {
        AlertDialog(
            onDismissRequest = { licensesOpen = false },
            title = { Text(copy.licenses) },
            text = {
                Text(
                    "Copyright 2026 SwaraLab contributors. Licensed under the Apache License, " +
                        "Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0",
                )
            },
            confirmButton = {
                TextButton(onClick = { licensesOpen = false }) { Text(copy.gotIt) }
            },
        )
    }
}

@Composable
private fun SettingsRow(label: String, subtitle: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, SwaraColors.Line),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = SwaraColors.Ink,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SwaraColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, SwaraColors.Line),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = SwaraColors.Ink)
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = SwaraColors.Muted)
        }
    }
}

@Composable
private fun SessionCard(
    row: SessionSummary,
    onOpen: (String) -> Unit,
    onListen: (String) -> Unit = {},
    onShare: () -> Unit = {},
) {
    val day = Instant.ofEpochMilli(row.updatedAtEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val takes = if (row.takeCount == 1) "1 take" else "${row.takeCount} takes"
    val kind = when (row.kind) {
        SessionKind.PRACTICE -> "Warm-up"
        SessionKind.SONG -> "Song"
        SessionKind.VIDEO -> "Video"
    }
    Surface(
        onClick = { onOpen(row.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SwaraColors.Card,
        border = BorderStroke(1.dp, SwaraColors.Line),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = row.title, style = MaterialTheme.typography.titleMedium, color = SwaraColors.Ink)
            Text(
                text = "$kind · $takes · $day",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            if (row.takeCount > 0) {
                var menuOpen by remember { mutableStateOf(false) }
                val copy = LocalAppCopy.current
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onListen(row.id) }) {
                        Text(copy.listenTake)
                    }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("···") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(copy.listenTake) },
                                onClick = {
                                    menuOpen = false
                                    onListen(row.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(copy.shareTake) },
                                onClick = {
                                    menuOpen = false
                                    onShare()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(copy.openStage) },
                                onClick = {
                                    menuOpen = false
                                    onOpen(row.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun listInvitePacks(projectsDir: File): List<String> {
    if (!projectsDir.isDirectory) return emptyList()
    return projectsDir.listFiles()
        ?.flatMap { session ->
            File(session, SessionLayout.EXPORTS).listFiles().orEmpty().toList()
        }
        ?.filter { file ->
            file.isFile && (
                file.name.contains("invite", ignoreCase = true) ||
                    file.extension.equals("swaralab", ignoreCase = true) ||
                    file.name.endsWith(".swaralab.zip", ignoreCase = true)
                )
        }
        ?.map { it.name }
        ?.sorted()
        .orEmpty()
}
