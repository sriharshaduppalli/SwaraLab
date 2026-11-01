package com.swaralab.feature.studio

import android.content.Context
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors

class SingerCameraController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var pendingFile: File? = null

    fun bind(previewView: PreviewView, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener(
            {
                val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        videoCapture,
                    )
                }.recoverCatching {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        videoCapture,
                    )
                }
                pendingFile?.let { start(it) }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun start(file: File) {
        pendingFile = file
        val capture = videoCapture ?: return
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        val output = FileOutputOptions.Builder(file).build()
        recording = capture.output
            .prepareRecording(appContext, output)
            .start(executor) { event ->
                if (event is VideoRecordEvent.Finalize && event.hasError()) {
                    runCatching { file.delete() }
                }
            }
        pendingFile = null
    }

    fun stop() {
        pendingFile = null
        runCatching { recording?.stop() }
        recording = null
    }

    fun release() {
        stop()
        executor.shutdown()
    }
}

@Composable
fun SingerCameraPreview(
    controller: SingerCameraController,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(controller, lifecycleOwner) {
        controller.bind(previewView, lifecycleOwner)
        onDispose { }
    }
    Surface(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp)),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
}
