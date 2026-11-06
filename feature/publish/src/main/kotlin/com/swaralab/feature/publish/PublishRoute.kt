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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.swaralab.domain.port.StemSelection
import com.swaralab.domain.port.ExportRequest
import com.swaralab.domain.port.ExportResult
import com.swaralab.domain.port.RenderEngine
import com.swaralab.domain.port.InvitePack
import com.swaralab.domain.usecase.LoadSession
import com.swaralab.domain.usecase.UpdatePublishSettings
import com.swaralab.domain.usecase.UpdateTrackMix
import com.swaralab.domain.usecase.UpdateVocalChain
import com.swaralab.domain.usecase.UpdateVocalSyncOffset
import com.swaralab.ui.BoothCanvas
import com.swaralab.ui.ChoiceCard
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.PageHero
import com.swaralab.ui.SwaraColors
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
    val solo: Boolean = false,
    val pan: Float = 0f,
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
    val stems: StemSelection = StemSelection(),
    val exportProgress: Float = 0f,
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
    private val invitePack: InvitePack,
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

    fun setTrackPan(trackId: String, pan: Float) {
        val current = uiState.value ?: return
        val updatedTracks = current.tracks.map {
            if (it.id == trackId) it.copy(pan = pan.coerceIn(-1f, 1f)) else it
        }
        uiState.value = current.copy(tracks = updatedTracks)
        scheduleSliderSave()
    }

    fun toggleTrackSolo(trackId: String) {
        val current = uiState.value ?: return
        val row = current.tracks.firstOrNull { it.id == trackId } ?: return
        val nextSolo = !row.solo
        val updatedTracks = current.tracks.map {
            if (it.id == trackId) it.copy(solo = nextSolo) else it
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
            updateTrackMix(sessionId, row.id, row.gainDb, row.mute, solo = row.solo, pan = row.pan)
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
                    val peak = aimed.publish.truePeakDbtp
                    uiState.value = uiState.value?.copy(
                        status = "This is ${snap.target.label()} at $lufs LUFS, ceiling $peak dBTP. Reels and Master are not the same loudness.",
                    )
                }
            }
        }
    }

    fun shareThisTake(context: Context, packageName: String? = null) {
        viewModelScope.launch {
            val current = uiState.value ?: return@launch
            if (current.busy) return@launch
            val kind = shareKindFor(current, packageName)
            sliderSaveJob?.cancel()
            flushSlidersToDisk()
            updatePublish(sessionId, current.target)
            audio.stopPlayback()
            uiState.value = current.copy(busy = true, status = "Preparing this take to share...")
            val document = loadSession(sessionId)
            if (document == null) {
                status = "Open the song again, then share."
                refresh()
                return@launch
            }
            val aimed = document.copy(publish = document.publish.withTarget(current.target))
            when (val result = render.export(aimed, ExportRequest(current.target, kind))) {
                is ExportResult.Written -> {
                    val shareable = ensureWhatsAppSize(aimed, result.relativePath)
                    exportedRelativePath = shareable
                    exportedMime = mimeFor(shareable)
                    status = "This take is ready. Opening share..."
                    refresh()
                    shareExportToApps(context, packageName)
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
            uiState.value = current.copy(busy = true, status = "Mixing backing music & vocal tracks...", exportProgress = 0.02f)
            val document = loadSession(sessionId) ?: return@launch
            when (val result = render.export(
                document,
                ExportRequest(document.publish.target, ExportKind.MIX),
            ) { progress ->
                uiState.value = uiState.value?.copy(exportProgress = progress)
            }) {
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

    fun setStemFlag(
        mix: Boolean? = null,
        vocals: Boolean? = null,
        backing: Boolean? = null,
        leadDry: Boolean? = null,
        layers: Boolean? = null,
    ) {
        val current = uiState.value ?: return
        uiState.value = current.copy(
            stems = current.stems.copy(
                mix = mix ?: current.stems.mix,
                vocals = vocals ?: current.stems.vocals,
                backing = backing ?: current.stems.backing,
                leadDry = leadDry ?: current.stems.leadDry,
                layers = layers ?: current.stems.layers,
            ),
        )
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
                ExportKind.STEMS -> "Exporting mix, vocals, backing, and each layer..."
            }
            uiState.value = current.copy(busy = true, status = label, exportProgress = 0.02f)
            val document = loadSession(sessionId) ?: return@launch
            when (val result = render.export(
                document,
                ExportRequest(document.publish.target, kind, stems = current.stems),
            ) { progress ->
                uiState.value = uiState.value?.copy(exportProgress = progress)
            }) {
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
                        ExportKind.STEMS -> "Stems ready: mix, dry lead, all vocals, and each layer."
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

    fun shareExportToApps(context: Context, packageName: String? = null) {
        val current = uiState.value ?: return
        val path = exportedRelativePath ?: return
        var sharePath = path
        var source = File(File(projectsDir, sessionId), sharePath)
        if (!source.isFile) return
        viewModelScope.launch {
            if (source.length() > SHARE_MAX_BYTES && !exportedMime.contains("zip")) {
                val document = loadSession(sessionId)
                if (document != null) {
                    val compactPath = ensureWhatsAppSize(document, sharePath)
                    sharePath = compactPath
                    source = File(File(projectsDir, sessionId), sharePath)
                    exportedRelativePath = compactPath
                    exportedMime = mimeFor(compactPath)
                }
            }
            val fileName = File(sharePath).name.ifBlank { "swaralab-mix.m4a" }
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
                PreferredShare.open(
                    context = context,
                    uri = uri,
                    mime = exportedMime,
                    title = current.title,
                    packageName = packageName,
                )
                status = if (packageName != null) {
                    "Saved to Music/SwaraLab and opened that app."
                } else {
                    "Saved to Music/SwaraLab. WhatsApp, Instagram, and YouTube sit first if they are on this phone."
                }
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

    private suspend fun ensureWhatsAppSize(
        document: com.swaralab.domain.model.SessionDocument,
        relativePath: String,
    ): String {
        val file = File(File(projectsDir, sessionId), relativePath)
        if (!file.isFile || file.length() <= SHARE_MAX_BYTES) return relativePath
        return when (
            val compact = render.export(
                document,
                ExportRequest(document.publish.target, ExportKind.COMPACT),
            )
        ) {
            is ExportResult.Written -> compact.relativePath
            is ExportResult.Waiting -> relativePath
        }
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
                        solo = track.solo,
                        pan = track.pan,
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
                stems = uiState.value?.stems ?: StemSelection(),
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
            invitePack: InvitePack,
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
    invitePack: InvitePack,
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

    val copy = LocalAppCopy.current
    BoothCanvas(intensity = 0.5f, modifier = modifier) {
    Column(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back to stage") }
        PageHero(
            title = copy.mixSave,
            body = "Balance the track and your voice, then hear it at share loudness before you export.",
            accent = SwaraColors.Teal,
        )
        MixDeskSections(
            state = state,
            viewModel = viewModel,
            audioState = audioState,
            copy = copy,
            context = context,
            onSaveFile = { name -> saveFileLauncher.launch(name) },
        )
    }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = SwaraColors.Card,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, SwaraColors.Teal),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Button(
                    onClick = { viewModel.shareThisTake(context) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Share this take")
                }
                val installed = remember(state.exportedMime, state.hasCameraVideo) {
                    val mime = when {
                        state.exportedMime.isNotBlank() -> state.exportedMime
                        state.hasCameraVideo -> "video/mp4"
                        state.target == PublishTarget.WHATSAPP ||
                            state.target == PublishTarget.MASTER -> "audio/wav"
                        else -> "video/mp4"
                    }
                    PreferredShare.installed(context, mime)
                }
                if (installed.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        installed.forEach { target ->
                            OutlinedButton(
                                onClick = { viewModel.shareThisTake(context, target.packageName) },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(target.label, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (state.busy) {
                        state.status ?: "Preparing this take..."
                    } else if (installed.isEmpty()) {
                        "Ready for ${state.target.label()}. Share opens the apps on this phone."
                    } else {
                        "WhatsApp, Instagram, and YouTube first if they can take this file."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwaraColors.Muted,
                )
            }
        }
    }
    }
}

@Composable
internal fun EffectSliderRow(
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

internal fun PublishTarget.label(): String = when (this) {
    PublishTarget.MASTER -> "Full studio master"
    PublishTarget.REELS -> "Reels"
    PublishTarget.SHORTS -> "Shorts"
    PublishTarget.WHATSAPP -> "WhatsApp"
    PublishTarget.SQUARE -> "Square"
    PublishTarget.LANDSCAPE -> "Wide"
}

private const val SHARE_MAX_BYTES = 15L * 1024 * 1024

private fun shareKindFor(current: PublishUi, packageName: String?): ExportKind {
    val pkg = packageName.orEmpty()
    val whatsApp = pkg.contains("whatsapp") || current.target == PublishTarget.WHATSAPP
    if (whatsApp) return ExportKind.COMPACT
    if (pkg.contains("instagram") || pkg.contains("youtube")) {
        return if (current.hasCameraVideo) ExportKind.VIDEO else ExportKind.LYRIC_VIDEO
    }
    return ExportKind.COMPACT
}

private fun mimeFor(path: String): String = when (path.substringAfterLast('.').lowercase()) {
    "mp4" -> "video/mp4"
    "m4a", "aac" -> "audio/mp4"
    "zip", "swaralab" -> "application/zip"
    else -> "audio/wav"
}

internal fun shareCopy(target: PublishTarget): String = when (target) {
    PublishTarget.REELS -> "Vertical 9:16. Hot −11 LUFS, ceiling −0.3 dBTP. Hear as Reels — louder than Master."
    PublishTarget.SHORTS -> "Vertical 9:16. Hot −11 LUFS, ceiling −0.3 dBTP. Hear as Shorts before you share."
    PublishTarget.WHATSAPP -> "Small AAC under 16 MB (−16 LUFS, −2.5 dBTP). Hear as WhatsApp first."
    PublishTarget.SQUARE -> "Square 1:1. About −13 LUFS, ceiling −0.8 dBTP."
    PublishTarget.LANDSCAPE -> "Wide 16:9. About −13 LUFS, ceiling −0.8 dBTP."
    PublishTarget.MASTER -> "Full vocal + backing WAV at −14 LUFS, ceiling −1 dBTP. Quieter and cleaner than Reels."
}
