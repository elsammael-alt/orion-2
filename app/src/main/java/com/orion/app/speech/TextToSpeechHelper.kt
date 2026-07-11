package com.orion.app.speech

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TextToSpeechHelper(
    context: Context,
    private val onDone: () -> Unit = {}
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("cs", "CZ")
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
                ready = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        onDone()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })
            }
        }
    }

    fun speak(text: String) {
        if (!ready) return
        val clean = text.take(4000) // TTS length limit
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "orion_utt")
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
