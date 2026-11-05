package com.swaralab.app.media

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Buffering : PlaybackState
    data object Playing : PlaybackState
    data object Paused : PlaybackState
    data object Ended : PlaybackState
}

/** Media3 listen-back for a local take. Not used on the record callback thread. */
class PlaybackManager(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var ticking: Job? = null
    private var currentPath: String? = null

    private val playback = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = playback.asStateFlow()
    private val positionState = MutableStateFlow(0L)
    val position: StateFlow<Long> = positionState.asStateFlow()
    private val durationState = MutableStateFlow(0L)
    val duration: StateFlow<Long> = durationState.asStateFlow()

    fun play(file: File) {
        if (!file.isFile) {
            release()
            return
        }
        if (currentPath == file.absolutePath && player != null && playback.value is PlaybackState.Paused) {
            resume()
            return
        }
        release()
        currentPath = file.absolutePath
        val exo = ExoPlayer.Builder(app).build()
        player = exo
        exo.setMediaItem(MediaItem.fromUri(file.toUri()))
        exo.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    playback.value = when (state) {
                        Player.STATE_READY -> if (exo.isPlaying) PlaybackState.Playing else PlaybackState.Paused
                        Player.STATE_ENDED -> {
                            positionState.value = 0L
                            PlaybackState.Ended
                        }
                        Player.STATE_BUFFERING -> PlaybackState.Buffering
                        else -> PlaybackState.Idle
                    }
                    val length = exo.duration
                    if (length > 0L) durationState.value = length
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playback.value = when {
                        isPlaying -> PlaybackState.Playing
                        playback.value is PlaybackState.Ended -> PlaybackState.Ended
                        else -> PlaybackState.Paused
                    }
                }
            },
        )
        exo.prepare()
        exo.playWhenReady = true
        startTicks()
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        val exo = player ?: return
        if (playback.value is PlaybackState.Ended) {
            exo.seekTo(0L)
        }
        exo.play()
    }

    fun seekTo(positionMs: Long) {
        val exo = player ?: return
        val length = exo.duration.takeIf { it > 0L } ?: durationState.value
        val clamped = positionMs.coerceIn(0L, length.coerceAtLeast(0L))
        exo.seekTo(clamped)
        positionState.value = clamped
    }

    fun release() {
        ticking?.cancel()
        ticking = null
        player?.release()
        player = null
        currentPath = null
        playback.value = PlaybackState.Idle
        positionState.value = 0L
        durationState.value = 0L
    }

    private fun startTicks() {
        ticking?.cancel()
        ticking = scope.launch {
            while (isActive) {
                val exo = player ?: break
                positionState.value = exo.currentPosition.coerceAtLeast(0L)
                val length = exo.duration
                if (length > 0L) durationState.value = length
                delay(80L)
            }
        }
    }
}
