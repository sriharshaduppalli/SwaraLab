package com.swaralab.feature.publish

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.domain.model.PublishTarget
import com.swaralab.domain.model.TrackRole
import com.swaralab.domain.model.VocalChain
import com.swaralab.domain.model.VocalPreset
import com.swaralab.domain.model.withTarget
import com.swaralab.domain.port.AudioEngine
import com.swaralab.domain.layout.SessionLayout
import com.swaralab.domain.port.ExportKind
import com.swaralab.domain.port.ExportRequest
import com.swaralab.domain.port.ExportResult
import com.swaralab.domain.port.RenderEngine
import com.swaralab.session.SessionInvitePack
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.UpdatePublishSettings
import com.swaralab.domain.usecase.UpdateTrackMix
import com.swaralab.domain.usecase.UpdateVocalChain
import com.swaralab.domain.usecase.UpdateVocalSyncOffset
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.PageHero
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.studioAtmosphere
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class TrackMixRowUi(
    val id: String,
    val label: String,
    val clipCount: Int,
    val gainDb: Float,
    val mute: Boolean,
)

data class PublishUi(
    val title: String,
    val target: PublishTarget,
    val tracks: List<TrackMixRowUi>,
    val chain: VocalChain,
    val syncOffsetMs: Float,
    val exportedRelativePath: String?,
    val exportedFileName: String?,
    val exportedMime: String,
    val status: String?,
    val busy: Boolean = false,
    val hasCameraVideo: Boolean = false,
    val previewDry: Boolean = true,
    val vocalPath: String? = null,
    val vocalStartMs: Long = 0L,
    val vocalDurationMs: Long = 0L,
    val backingPath: String? = null,
)

class PublishViewModel(
    private val sessionId: String,
    private val projectsDir: File,
    private val loadSession: LoadSession,
    private val updatePublish: UpdatePublishSettings,
    private val updateTrackMix: UpdateTrackMix,
    private val updateVocalChain: UpdateVocalChain,
    private val updateVocalSyncOffset: UpdateVocalSyncOffset,
    private val render: RenderEngine,
    val audio: AudioEngine,
    private val invitePack: SessionInvitePack,
) : ViewModel() {
    private val uiState = MutableStateFlow<PublishUi?>(null)
    val ui: StateFlow<PublishUi?> = uiState.asStateFlow()
    private var exportedRelativePath: String? = null
    private var exportedMime: String = "audio/wav"
    private var status: String? = null
    private var sliderSaveJob: Job? = null

    init {
        refresh()
    }

    fun select(target: PublishTarget) {
        val current = uiState.value ?: return
        uiState.value = current.copy(target = target, status = null)
        viewModelScope.launch {
            updatePublish(sessionId, target)
            status = null
            refresh()
        }
    }

    fun setTrackGain(trackId: String, gainDb: Float) {
        val current = uiState.value ?: return
        val updatedTracks = current.tracks.map {
            if (it.id == trackId) it.copy(gainDb = gainDb) else it
        }
        uiState.value = current.copy(tracks = updatedTracks)
        scheduleSliderSave()
    }

    fun toggleTrackMute(trackId: String) {
        val current = uiState.value ?: return
        val row = current.tracks.firstOrNull { it.id == trackId } ?: return
        val nextMute = !row.mute
        val updatedTracks = current.tracks.map {
            if (it.id == trackId) it.copy(mute = nextMute) else it
        }
        uiState.value = current.copy(tracks = updatedTracks)
        scheduleSliderSave()
    }

    fun setSyncOffset(offsetMs: Float) {
        val current = uiState.value ?: return
        uiState.value = current.copy(syncOffsetMs = offsetMs)
        scheduleSliderSave()
    }

    fun updateChainSliders(
        reverb: Float = uiState.value?.chain?.reverb ?: 0f,
        delay: Float = uiState.value?.chain?.delay ?: 0f,
        compression: Float = uiState.value?.chain?.compression ?: 0f,
        denoise: Float = uiState.value?.chain?.denoise ?: 0f,
        backingDuckDb: Float = uiState.value?.chain?.backingDuckDb ?: 0f,
        deEss: Float = uiState.value?.chain?.deEss ?: 0f,
        tone: Float = uiState.value?.chain?.tone ?: 0f,
        pitchCorrect: Float = uiState.value?.chain?.pitchCorrect ?: 0f,
        fadeOut: Float = uiState.value?.chain?.fadeOut ?: 0.4f,
        thicken: Float = uiState.value?.chain?.thicken ?: 0f,
        effectAmount: Float = uiState.value?.chain?.effectAmount ?: 1f,
    ) {
        val current = uiState.value ?: return
        val updatedChain = current.chain.copy(
            preset = VocalPreset.CUSTOM,
            reverb = reverb,
            delay = delay,
            compression = compression,
            denoise = denoise,
            backingDuckDb = backingDuckDb,
            deEss = deEss,
            tone = tone,
            pitchCorrect = pitchCorrect,
            fadeOut = fadeOut,
            thicken = thicken,
            effectAmount = effectAmount,
        )
        uiState.value = current.copy(chain = updatedChain)
        scheduleSliderSave()
    }

    fun setPreviewDry(dry: Boolean) {
        val current = uiState.value ?: return
        uiState.value = current.copy(previewDry = dry)
        viewModelScope.launch { playCurrentPreview() }
    }

    private fun scheduleSliderSave() {
        sliderSaveJob?.cancel()
        sliderSaveJob = viewModelScope.launch {
            delay(160L)
            flushSlidersToDisk()
            if (uiState.value?.previewDry == false && audio.state.value.playingRelativePath == "preview-mix") {
                playCurrentPreview()
            }
        }
    }

    private suspend fun flushSlidersToDisk() {
        val snap = uiState.value ?: return
        for (row in snap.tracks) {
            updateTrackMix(sessionId, row.id, row.gainDb, row.mute)
        }
        updateVocalSyncOffset(sessionId, snap.syncOffsetMs.toDouble())
        if (snap.chain.preset == VocalPreset.CUSTOM) {
            updateVocalChain(
                id = sessionId,
                reverb = snap.chain.reverb,
                delay = snap.chain.delay,
                compression = snap.chain.compression,
                denoise = snap.chain.denoise,
                backingDuckDb = snap.chain.backingDuckDb,
                deEss = snap.chain.deEss,
                tone = snap.chain.tone,
                pitchCorrect = snap.chain.pitchCorrect,
                fadeOut = snap.chain.fadeOut,
                thicken = snap.chain.thicken,
                effectAmount = snap.chain.effectAmount,
            )
        }
    }

    fun hearShareLoudness() {
        val current = uiState.value ?: return
        uiState.value = current.copy(previewDry = false)
        playCurrentPreview()
    }

    fun playCurrentPreview() {
        viewModelScope.launch {
            val snap = uiState.value ?: return@launch
            sliderSaveJob?.cancel()
            flushSlidersToDisk()
            if (snap.previewDry) {
                val vocal = snap.vocalPath ?: return@launch
                audio.playWithBacking(
                    sessionId = sessionId,
                    vocalRelativePath = vocal,
                    vocalStartMs = snap.vocalStartMs,
                    vocalDurationMs = snap.vocalDurationMs.coerceAtMost(12_000L).coerceAtLeast(1L),
                    backingRelativePath = snap.backingPath,
                    backingStartMs = 0L,
                    loopBacking = false,
                )
            } else {
                val document = loadSession(sessionId) ?: return@launch
                val aimed = document.copy(
                    chain = snap.chain,
                    publish = document.publish.withTarget(snap.target),
                )
                val preview = render.preview(aimed, 12_000L)
                if (preview != null) {
                    audio.playPcm(sessionId, preview.left, preview.right, preview.sampleRate, loop = true)
                    val lufs = aimed.publish.loudnessLufs.toInt()
                    uiState.value = uiState.value?.copy(
                        status = "This is ${snap.target.label()} loudness ($lufs LUFS). Share will sound this loud.",
                    )
                }
            }
        }
    }

    fun shareThisTake(context: Context) {
        viewModelScope.launch {
            val current = uiState.value ?: return@launch
            if (current.busy) return@launch
            val kind = when {
                current.hasCameraVideo -> ExportKind.VIDEO
                current.target == PublishTarget.WHATSAPP -> ExportKind.COMPACT
                current.target == PublishTarget.MASTER -> ExportKind.MIX
                else -> ExportKind.LYRIC_VIDEO
            }
            sliderSaveJob?.cancel()
            flushSlidersToDisk()
            audio.stopPlayback()
            uiState.value = current.copy(busy = true, status = "Preparing this take to share...")
            val document = loadSession(sessionId) ?: return@launch
            when (val result = render.export(document, ExportRequest(document.publish.target, kind))) {
                is ExportResult.Written -> {
                    exportedRelativePath = result.relativePath
                    exportedMime = mimeFor(result.relativePath)
                    status = "This take is ready. Opening share..."
                    refresh()
                    shareExportToApps(context)
                }
                is ExportResult.Waiting -> {
                    status = result.message
                    refresh()
                }
            }
        }
    }

    fun prepare() {
        viewModelScope.launch {
            val current = uiState.value ?: return@launch
            if (current.busy) return@launch
            sliderSaveJob?.cancel()
            flushSlidersToDisk()
            audio.stopPlayback()
            uiState.value = current.copy(busy = true, status = "Mixing backing music & vocal tracks...")
            val document = loadSession(sessionId) ?: return@launch
            when (val result = render.export(document, ExportRequest(document.publish.target, ExportKind.MIX))) {
                is ExportResult.Written -> {
                    exportedRelativePath = result.relativePath
                    exportedMime = mimeFor(result.relativePath)
                    status = "Master mix ready (${result.durationMs / 1000}s). Tap Play, Share, or Save."
                }
                is ExportResult.Waiting -> {
                    status = result.message
                }
            }
            refresh()
        }
    }

    fun exportKind(kind: ExportKind) {
        viewModelScope.launch {
            val current = uiState.value ?: return@launch
            if (current.busy) return@launch
            sliderSaveJob?.cancel()
            flushSlidersToDisk()
            audio.stopPlayback()
            val label = when (kind) {
                ExportKind.VOCALS -> "Exporting vocals-only stem..."
                ExportKind.BACKING -> "Exporting backing stem..."
                ExportKind.COMPACT -> "Encoding compact audio for WhatsApp and Reels..."
                ExportKind.VIDEO -> "Mixing your camera take..."
                ExportKind.LYRIC_VIDEO -> "Rendering lyric card..."
                ExportKind.MIX -> "Mixing backing music & vocal tracks..."
            }
            uiState.value = current.copy(busy = true, status = label)
            val document = loadSession(sessionId) ?: return@launch
            when (val result = render.export(document, ExportRequest(document.publish.target, kind))) {
                is ExportResult.Written -> {
                    exportedRelativePath = result.relativePath
                    exportedMime = mimeFor(result.relativePath)
                    status = when (kind) {
                        ExportKind.VOCALS -> "Vocals stem ready. Share or save it."
                        ExportKind.BACKING -> "Backing stem ready. Share or save it."
                        ExportKind.COMPACT -> "Compact M4A ready for WhatsApp, Reels, and Shorts."
                        ExportKind.VIDEO -> "Camera take ready. Share the MP4 to Reels or Shorts."
                        ExportKind.LYRIC_VIDEO -> "Lyric card ready. Share the MP4."
                        ExportKind.MIX -> "Master mix ready. Tap Play, Share, or Save."
                    }
                }
                is ExportResult.Waiting -> status = result.message
            }
            refresh()
        }
    }

    fun shareInvite(context: Context) {
        viewModelScope.launch {
            val current = uiState.value ?: return@launch
            uiState.value = current.copy(busy = true, status = "Packing lyrics, backing, and guide vocals...")
            val zipName = "${current.title.replace(Regex("[^a-zA-Z0-9]+"), "-")}-invite.swaralab"
            val zipFile = File(File(projectsDir, sessionId), SessionLayout.inviteFile(zipName))
            val ok = withContext(Dispatchers.IO) { invitePack.exportZip(sessionId, zipFile) }
            if (!ok) {
                status = "Could not pack the duet invite."
                refresh()
                return@launch
            }
            exportedRelativePath = SessionLayout.inviteFile(zipName)
            exportedMime = "application/zip"
            status = "Invite pack ready. Share it so a friend can open it in Songs."
            refresh()
            shareExportToApps(context)
        }
    }

    fun togglePlayExport() {
        val path = exportedRelativePath ?: return
        viewModelScope.launch {
            audio.playClip(sessionId, path)
        }
    }

    fun copyExportToUri(writeBytes: (File) -> Boolean) {
        val path = exportedRelativePath ?: return
        val source = File(File(projectsDir, sessionId), path)
        if (!source.isFile) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { writeBytes(source) }
            status = if (ok) {
                "Saved master WAV file to your phone."
            } else {
                "Could not save the file."
            }
            refresh()
        }
    }

    fun shareExportToApps(context: Context) {
        val current = uiState.value ?: return
        val path = exportedRelativePath ?: return
        val source = File(File(projectsDir, sessionId), path)
        if (!source.isFile) return
        val fileName = current.exportedFileName ?: "swaralab-mix.wav"
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Audio.Media.TITLE, current.title)
                        put(MediaStore.Audio.Media.MIME_TYPE, exportedMime)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/SwaraLab")
                            put(MediaStore.Audio.Media.IS_PENDING, 1)
                        }
                    }
                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        when {
                            exportedMime.startsWith("video/") ->
                                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            exportedMime.contains("zip") ->
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI
                            else ->
                                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        }
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    val inserted = resolver.insert(collection, values) ?: return@runCatching null
                    resolver.openOutputStream(inserted)?.use { out ->
                        source.inputStream().use { input -> input.copyTo(out) }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val ready = ContentValues().apply {
                            put(MediaStore.Audio.Media.IS_PENDING, 0)
                        }
                        resolver.update(inserted, ready, null, null)
                    }
                    inserted
                }.getOrNull()
            }
            if (uri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = exportedMime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, current.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share ${current.title}"))
                status = "Saved to Music/SwaraLab and opened share sheet."
                refresh()
            } else {
                status = "Use Save to phone to pick a folder on this device."
                refresh()
            }
        }
    }

    fun stopAudioOnExit() {
        audio.stopPlayback()
    }

    private fun refresh() {
        viewModelScope.launch {
            val document = loadSession(sessionId) ?: return@launch
            val publish = document.publish
            val safeTitle = document.title
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "song" }
            var voiceIndex = 0
            val trackRows = document.tracks
                .filter { it.role != TrackRole.BACKING || it.clips.isNotEmpty() }
                .map { track ->
                    val label = if (track.role == TrackRole.BACKING) {
                        "Backing music"
                    } else {
                        voiceIndex += 1
                        if (voiceIndex == 1) "Part 1 · Lead vocal" else track.name
                    }
                    TrackMixRowUi(
                        id = track.id,
                        label = label,
                        clipCount = track.clips.size,
                        gainDb = track.gainDb,
                        mute = track.mute,
                    )
                }
            uiState.value = PublishUi(
                title = document.title,
                target = publish.target,
                tracks = trackRows,
                chain = document.chain,
                syncOffsetMs = document.latency.compensationMs.toFloat(),
                exportedRelativePath = exportedRelativePath,
                exportedFileName = exportedRelativePath?.substringAfterLast('/')
                    ?: "$safeTitle-${publish.target.name.lowercase()}.wav",
                exportedMime = exportedMime,
                status = status,
                busy = false,
                previewDry = uiState.value?.previewDry ?: true,
                vocalPath = document.tracks
                    .filter { it.role != TrackRole.BACKING }
                    .flatMap { it.clips }
                    .let { clips -> clips.firstOrNull { it.preferred } ?: clips.lastOrNull() }
                    ?.sourceRelativePath,
                vocalStartMs = document.tracks
                    .filter { it.role != TrackRole.BACKING }
                    .flatMap { it.clips }
                    .let { clips -> clips.firstOrNull { it.preferred } ?: clips.lastOrNull() }
                    ?.sourceInMs ?: 0L,
                vocalDurationMs = document.tracks
                    .filter { it.role != TrackRole.BACKING }
                    .flatMap { it.clips }
                    .let { clips -> clips.firstOrNull { it.preferred } ?: clips.lastOrNull() }
                    ?.durationMs ?: 0L,
                backingPath = document.tracks.firstOrNull { it.role == TrackRole.BACKING }
                    ?.clips?.lastOrNull()?.sourceRelativePath,
                hasCameraVideo = document.tracks
                    .filter { it.role != TrackRole.BACKING }
                    .flatMap { it.clips }
                    .mapNotNull { it.videoRelativePath }
                    .any { File(projectsDir, "$sessionId/$it").isFile } ||
                    File(projectsDir, "$sessionId/${SessionLayout.VIDEO}").listFiles()
                        ?.any { it.isFile && it.extension.equals("mp4", ignoreCase = true) } == true,
            )
        }
    }

    companion object {
        fun factory(
            sessionId: String,
            projectsDir: File,
            loadSession: LoadSession,
            updatePublish: UpdatePublishSettings,
            updateTrackMix: UpdateTrackMix,
            updateVocalChain: UpdateVocalChain,
            updateVocalSyncOffset: UpdateVocalSyncOffset,
            render: RenderEngine,
            audio: AudioEngine,
            invitePack: SessionInvitePack,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PublishViewModel(
                    sessionId = sessionId,
                    projectsDir = projectsDir,
                    loadSession = loadSession,
                    updatePublish = updatePublish,
                    updateTrackMix = updateTrackMix,
                    updateVocalChain = updateVocalChain,
                    updateVocalSyncOffset = updateVocalSyncOffset,
                    render = render,
                    audio = audio,
                    invitePack = invitePack,
                )
            }
        }
    }
}

@Composable
fun PublishRoute(
    sessionId: String,
    projectsDir: File,
    loadSession: LoadSession,
    updatePublish: UpdatePublishSettings,
    updateTrackMix: UpdateTrackMix,
    updateVocalChain: UpdateVocalChain,
    updateVocalSyncOffset: UpdateVocalSyncOffset,
    render: RenderEngine,
    audio: AudioEngine,
    invitePack: SessionInvitePack,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PublishViewModel = viewModel(
        factory = PublishViewModel.factory(
            sessionId = sessionId,
            projectsDir = projectsDir,
            loadSession = loadSession,
            updatePublish = updatePublish,
            updateTrackMix = updateTrackMix,
            updateVocalChain = updateVocalChain,
            updateVocalSyncOffset = updateVocalSyncOffset,
            render = render,
            audio = audio,
            invitePack = invitePack,
        ),
    )
    val ui by viewModel.ui.collectAsState()
    val audioState by viewModel.audio.state.collectAsState()
    val state = ui ?: return
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(state.busy) {
        view.keepScreenOn = state.busy
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAudioOnExit()
        }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(state.exportedMime.ifBlank { "*/*" }),
    ) { uri ->
        if (uri != null) {
            viewModel.copyExportToUri { source ->
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        source.inputStream().use { input -> input.copyTo(out) }
                    } != null
                }.getOrDefault(false)
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .studioAtmosphere()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val copy = LocalAppCopy.current
        TextButton(onClick = onBack) { Text("Back to stage") }
        PageHero(
            title = copy.mixSave,
            body = "Balance the track and your voice, then hear it at share loudness before you export.",
            accent = SwaraColors.Teal,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SwaraColors.Card,
            border = BorderStroke(1.dp, SwaraColors.Line),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "Track Volumes & Sync", style = MaterialTheme.typography.titleLarge)
                state.tracks.forEach { track ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${track.label} (${track.clipCount})",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (track.gainDb >= 0f) "+${track.gainDb.roundToInt()} dB" else "${track.gainDb.roundToInt()} dB",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SwaraColors.Muted,
                                )
                                FilterChip(
                                    selected = track.mute,
                                    onClick = { viewModel.toggleTrackMute(track.id) },
                                    label = { Text(if (track.mute) "Muted" else "Mute") },
                                )
                            }
                        }
                        Slider(
                            value = track.gainDb,
                            onValueChange = { viewModel.setTrackGain(track.id, it) },
                            valueRange = -18f..6f,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Vocal sync offset",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val ms = state.syncOffsetMs.roundToInt()
                            Text(
                                text = if (ms >= 0) "+$ms ms" else "$ms ms",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SwaraColors.Muted,
                            )
                            if (ms != 0) {
                                TextButton(onClick = { viewModel.setSyncOffset(0f) }) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                    Slider(
                        value = state.syncOffsetMs,
                        onValueChange = { viewModel.setSyncOffset(it) },
                        valueRange = -250f..250f,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SwaraColors.Card,
            border = BorderStroke(1.dp, SwaraColors.Line),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Hear the mix", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.previewDry,
                        onClick = { viewModel.setPreviewDry(true) },
                        label = { Text("Dry") },
                    )
                    FilterChip(
                        selected = !state.previewDry,
                        onClick = { viewModel.setPreviewDry(false) },
                        label = { Text("Mix") },
                    )
                    TextButton(onClick = viewModel::playCurrentPreview) {
                        Text("Hear loop")
                    }
                    TextButton(onClick = viewModel::hearShareLoudness) {
                        Text("Hear as ${state.target.label()}")
                    }
                }
                var showMoreMix by remember { mutableStateOf(false) }
                TextButton(onClick = { showMoreMix = !showMoreMix }) {
                    Text(if (showMoreMix) "Hide mix controls" else "More mix controls")
                }
                if (showMoreMix) {
                EffectSliderRow(
                    label = "Tone (warm ↔ air)",
                    valueText = if (state.chain.tone >= 0f) {
                        "Air ${(state.chain.tone * 100).roundToInt()}%"
                    } else {
                        "Warm ${(-state.chain.tone * 100).roundToInt()}%"
                    },
                    value = state.chain.tone,
                    valueRange = -1f..1f,
                    onValueChange = { viewModel.updateChainSliders(tone = it) },
                )
                EffectSliderRow(
                    label = "De-ess",
                    valueText = "${(state.chain.deEss * 100).roundToInt()}%",
                    value = state.chain.deEss,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(deEss = it) },
                )
                EffectSliderRow(
                    label = "Pitch to key",
                    valueText = "${(state.chain.pitchCorrect * 100).roundToInt()}%",
                    value = state.chain.pitchCorrect,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(pitchCorrect = it) },
                )
                EffectSliderRow(
                    label = "Thicken",
                    valueText = "${(state.chain.thicken * 100).roundToInt()}%",
                    value = state.chain.thicken,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(thicken = it) },
                )
                EffectSliderRow(
                    label = "Effect amount",
                    valueText = "${(state.chain.effectAmount * 100).roundToInt()}%",
                    value = state.chain.effectAmount,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(effectAmount = it) },
                )
                EffectSliderRow(
                    label = "Fade out",
                    valueText = "${(state.chain.fadeOut * 100).roundToInt()}%",
                    value = state.chain.fadeOut,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(fadeOut = it) },
                )
                EffectSliderRow(
                    label = "Reverb (Room & Hall)",
                    valueText = "${(state.chain.reverb * 100).roundToInt()}%",
                    value = state.chain.reverb,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(reverb = it) },
                )
                EffectSliderRow(
                    label = "Echo (Delay)",
                    valueText = "${(state.chain.delay * 100).roundToInt()}%",
                    value = state.chain.delay,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(delay = it) },
                )
                EffectSliderRow(
                    label = "Warmth & Compression",
                    valueText = "${(state.chain.compression * 100).roundToInt()}%",
                    value = state.chain.compression,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(compression = it) },
                )
                EffectSliderRow(
                    label = "Noise clean",
                    valueText = "${(state.chain.denoise * 100).roundToInt()}%",
                    value = state.chain.denoise,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.updateChainSliders(denoise = it) },
                )
                EffectSliderRow(
                    label = "Auto-Duck Backing Music",
                    valueText = "${state.chain.backingDuckDb.roundToInt()} dB",
                    value = state.chain.backingDuckDb,
                    valueRange = 0f..9f,
                    onValueChange = { viewModel.updateChainSliders(backingDuckDb = it) },
                )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SwaraColors.Card,
            border = BorderStroke(1.dp, SwaraColors.Line),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { viewModel.shareThisTake(context) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Share this take")
                }
                Button(
                    onClick = viewModel::prepare,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.exportedRelativePath == null) {
                            "Mix backing & vocals"
                        } else {
                            "Remix with current sliders"
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.exportKind(ExportKind.VOCALS) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Vocals") }
                    OutlinedButton(
                        onClick = { viewModel.exportKind(ExportKind.BACKING) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Backing") }
                    OutlinedButton(
                        onClick = { viewModel.exportKind(ExportKind.COMPACT) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Compact") }
                }
                if (state.hasCameraVideo) {
                    Button(
                        onClick = { viewModel.exportKind(ExportKind.VIDEO) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(copy.shareCamera) }
                }
                OutlinedButton(
                    onClick = { viewModel.exportKind(ExportKind.LYRIC_VIDEO) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(copy.lyricCard) }
                OutlinedButton(
                    onClick = { viewModel.shareInvite(context) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Invite a friend to sing the other part") }
                if (state.exportedRelativePath != null) {
                    val isPlaying = audioState.playingRelativePath == state.exportedRelativePath
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!state.exportedMime.contains("zip")) {
                            OutlinedButton(
                                onClick = viewModel::togglePlayExport,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (isPlaying) "Stop" else "Play")
                            }
                        }
                        Button(
                            onClick = { viewModel.shareExportToApps(context) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Share")
                        }
                        OutlinedButton(
                            onClick = {
                                saveFileLauncher.launch(state.exportedFileName ?: "swaralab-mix.wav")
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Save")
                        }
                    }
                }
                state.status?.let { message ->
                    val failed = message.contains("Could") ||
                        message.contains("Record at least") ||
                        message.contains("Sing a camera") ||
                        message.contains("Add a backing")
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (failed) SwaraColors.Clay else SwaraColors.Teal,
                    )
                }
            }
        }

        Text(text = "Target format", style = MaterialTheme.typography.titleLarge)
        PublishTarget.entries.forEach { target ->
            val (accent, wash) = when (target) {
                PublishTarget.REELS, PublishTarget.SHORTS ->
                    SwaraColors.Rose to SwaraColors.RoseWash
                PublishTarget.WHATSAPP, PublishTarget.SQUARE ->
                    SwaraColors.Gold to SwaraColors.GoldWash
                else -> SwaraColors.Teal to SwaraColors.TealWash
            }
            ChoiceCard(
                title = target.label(),
                body = shareCopy(target),
                selected = state.target == target,
                accent = accent,
                accentWash = wash,
                onClick = { viewModel.select(target) },
            )
        }
    }
}

@Composable
private fun EffectSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

private fun PublishTarget.label(): String = when (this) {
    PublishTarget.MASTER -> "Full studio master"
    PublishTarget.REELS -> "Reels"
    PublishTarget.SHORTS -> "Shorts"
    PublishTarget.WHATSAPP -> "WhatsApp"
    PublishTarget.SQUARE -> "Square"
    PublishTarget.LANDSCAPE -> "Wide"
}

private fun mimeFor(path: String): String = when (path.substringAfterLast('.').lowercase()) {
    "mp4" -> "video/mp4"
    "m4a", "aac" -> "audio/mp4"
    "zip", "swaralab" -> "application/zip"
    else -> "audio/wav"
}

private fun shareCopy(target: PublishTarget): String = when (target) {
    PublishTarget.REELS -> "Vertical 9:16. Leveled hot for Reels (−11 LUFS). Hear as Reels before you share."
    PublishTarget.SHORTS -> "Vertical 9:16. Leveled hot for Shorts (−11 LUFS). Hear as Shorts before you share."
    PublishTarget.WHATSAPP -> "Quieter for chats and status (−16 LUFS). Hear as WhatsApp so it is not a surprise."
    PublishTarget.SQUARE -> "Square 1:1 feed. Share loudness about −13 LUFS."
    PublishTarget.LANDSCAPE -> "Wide 16:9. Share loudness about −13 LUFS."
    PublishTarget.MASTER -> "Full vocal + backing WAV at −14 LUFS, the studio master."
}
