package com.swaralab.feature.studio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
/**
 * Best-effort on-device speech listen while the singer records.
 * If the device cannot share the microphone, [onHeard] is simply never called
 * and voice-activity lyric follow still runs.
 */
class OnDeviceLyricListener(
    context: Context,
    private val onHeard: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var languageTag: String = "en-IN"
    private var running = false

    fun start(speechTag: String) {
        languageTag = speechTag
        main.post {
            stopLocked()
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) return@post
            recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
            recognizer?.setRecognitionListener(listener)
            running = true
            listenAgain()
        }
    }

    fun stop() {
        main.post { stopLocked() }
    }

    private fun listenAgain() {
        val speech = recognizer ?: return
        if (!running) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
        runCatching { speech.startListening(intent) }
    }

    private fun stopLocked() {
        running = false
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onError(error: Int) {
            if (running) main.postDelayed({ listenAgain() }, 350L)
        }
        override fun onResults(results: Bundle?) {
            emit(results)
            if (running) listenAgain()
        }
        override fun onPartialResults(partialResults: Bundle?) = emit(partialResults)
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun emit(bundle: Bundle?) {
            val heard = bundle
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (heard.isNotEmpty()) onHeard(heard)
        }
    }
}
