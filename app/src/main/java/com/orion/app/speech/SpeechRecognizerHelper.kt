package com.orion.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechRecognizerHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartial: (String) -> Unit = {},
    private val onError: (Int) -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    onError(error)
                    // Auto-restart after error
                    handler.postDelayed({ if (listening) startListening() }, 500)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResult(matches[0])
                    }
                    // Continue listening loop
                    handler.postDelayed({ if (listening) startListening() }, 300)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) onPartial(matches[0])
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        listening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "cs-CZ")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            // Restart
            handler.postDelayed({ if (listening) startListening() }, 1000)
        }
    }

    fun stopListening() {
        listening = false
        recognizer?.stopListening()
    }

    fun destroy() {
        listening = false
        recognizer?.destroy()
        recognizer = null
    }

    companion object {
        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)
    }
}
