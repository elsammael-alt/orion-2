package com.orion.app.service
import android.os.Build
import com.orion.app.MainActivity
import com.orion.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.orion.app.OrionApp
import com.orion.app.ai.GeminiClient
import com.orion.app.ai.LocalLLMClient
import com.orion.app.appcontrol.AppControlHelper
import com.orion.app.data.NoteStore
import com.orion.app.speech.*
import com.orion.app.speech.CommandType
import com.orion.app.speech.OfflineCommandParser
import com.orion.app.util.AlarmHelper

class OrionService : Service() {
    companion object {
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.orion.app.START_LISTENING"
        const val ACTION_STOP = "com.orion.app.STOP_LISTENING"
        const val ACTION_SET_KEY = "com.orion.app.SET_API_KEY"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var app: OrionApp
    private lateinit var gemini: GeminiClient
    private lateinit var tts: TextToSpeechHelper
    private lateinit var musicPlayer: MusicPlayer
    private lateinit var localLLM: LocalLLMClient
    private lateinit var noteStore: NoteStore
    private lateinit var appControl: AppControlHelper
    private lateinit var alarmHelper: AlarmHelper
    private lateinit var offlineParser: OfflineCommandParser

    private var speechHelper: SpeechRecognizerHelper? = null
    private var wakeWordDetector: SpeechRecognizerHelper? = null

    private val _status = MutableStateFlow("Neaktivní")
    val status: StateFlow<String> = _status

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private var wakeWordListen = true
    private var currentWakeWord = "dajen"
    private var initialized = false

    override fun onCreate() {
        super.onCreate()
        try {
            app = application as OrionApp
            gemini = GeminiClient(app.prefs)
            tts = TextToSpeechHelper(this) {}
            musicPlayer = MusicPlayer(this)
            localLLM = LocalLLMClient()
            noteStore = NoteStore(this)
            appControl = AppControlHelper(this)
            alarmHelper = AlarmHelper(this)
            offlineParser = OfflineCommandParser()
            currentWakeWord = app.prefs.wakeWord
            initialized = true
            startForegroundSafe()
        } catch (e: Exception) {
            Log.e("OrionService", "onCreate failed", e)
        }
    }

    private fun startForegroundSafe() {
        try {
            val notification = createNotification("Orion připraven — řekni '$currentWakeWord'")
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("OrionService", "startForeground failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!initialized) {
            // Retry init
            try {
                app = application as OrionApp
                gemini = GeminiClient(app.prefs)
                tts = TextToSpeechHelper(this) {}
                musicPlayer = MusicPlayer(this)
                localLLM = LocalLLMClient()
                noteStore = NoteStore(this)
                appControl = AppControlHelper(this)
                alarmHelper = AlarmHelper(this)
                offlineParser = OfflineCommandParser()
                currentWakeWord = app.prefs.wakeWord
                initialized = true
                startForegroundSafe()
            } catch (e: Exception) {
                Log.e("OrionService", "retry init failed", e)
                return START_STICKY
            }
        }

        when (intent?.action) {
            ACTION_START -> startListening()
            ACTION_STOP -> stopListening()
            ACTION_SET_KEY -> {
                app.prefs.apiKey = intent.getStringExtra("key") ?: ""
                gemini = GeminiClient(app.prefs)
            }
            else -> if (wakeWordListen) startWakeWordDetection()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "orion_channel")
            .setContentTitle("Orion")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun startWakeWordDetection() {
        if (!hasAudioPermission()) {
            _status.value = "Chybí oprávnění pro mikrofon"
            return
        }
        wakeWordListen = true
        currentWakeWord = app.prefs.wakeWord
        _status.value = "Poslouchám na '$currentWakeWord'..."

        try {
            wakeWordDetector = SpeechRecognizerHelper(
                context = this,
                onResult = { text -> processWakeWord(text) },
                onError = { _status.value = "Wake word chyba, restart..." }
            )
            wakeWordDetector?.startListening()
            updateNotification("Čekám na \"$currentWakeWord\"")
        } catch (e: Exception) {
            Log.e("OrionService", "startWakeWordDetection failed", e)
        }
    }

    private fun processWakeWord(text: String) {
        val lower = text.lowercase()
        if (lower.contains(currentWakeWord.lowercase())) {
            wakeWordDetector?.stopListening()
            wakeWordListen = false
            _status.value = "Rozpoznávám příkaz..."
            updateNotification("Rozpoznávám příkaz...")
            tts.speak("Ano?")
            GlobalScope.launch {
                delay(800)
                startCommandListening()
            }
        } else {
            if (lower.contains("stop") || lower.contains("zruš") || lower.contains("přestaň")) {
                try { alarmHelper.cancelAll() } catch (e: Exception) { }
                try { musicPlayer.stop() } catch (e: Exception) { }
            }
        }
    }

    private fun startCommandListening() {
        if (!hasAudioPermission()) return
        try {
            speechHelper?.destroy()
            speechHelper = SpeechRecognizerHelper(
                context = this,
                onResult = { command ->
                    _status.value = "Zpracovávám příkaz..."
                    processCommand(command)
                },
                onPartial = { partial -> _status.value = "Poslouchám: $partial..." },
                onError = {
                    _status.value = "Chyba, vracím se k wake word"
                    updateNotification("Orion čeká na \"$currentWakeWord\"")
                    resetToWakeWord()
                }
            )
            speechHelper?.startListening()
        } catch (e: Exception) {
            Log.e("OrionService", "startCommandListening failed", e)
        }
    }

    private fun processCommand(command: String) {
        val isOnline = isNetworkAvailable()
        val geminiReady = gemini.isReady()

        if (isOnline && geminiReady) {
            serviceScope.launch {
                _status.value = "Orion přemýšlí..."
                updateNotification("Odpovídám...")
                try {
                    val reply = gemini.sendMessage(command)
                    tts.speak(reply)
                } catch (e: Exception) {
                    Log.e("OrionService", "Gemini failed", e)
                    tts.speak("Chyba při komunikaci s Gemini.")
                }
                delay(1000)
                resetToWakeWord()
            }
        } else {
            try {
                val parsed = offlineParser.parse(command)
                when (parsed.type) {
                    CommandType.TIMER -> { alarmHelper.setTimer(parsed.minutes); tts.speak("Timer na ${parsed.minutes} minut spuštěn") }
                    CommandType.ALARM -> { alarmHelper.setAlarm(parsed.hours, parsed.minutes); tts.speak("Budík na ${parsed.hours}:${String.format("%02d", parsed.minutes)} nastaven") }
                    CommandType.STOP -> { try { alarmHelper.cancelAll() } catch (e: Exception) {}; try { musicPlayer.stop() } catch (e: Exception) {}; tts.speak("Vše zrušeno") }
                    CommandType.PLAY_MUSIC -> { try { musicPlayer.loadLocalTracks(app.prefs.musicFolder); musicPlayer.playRandom() } catch (e: Exception) {}; tts.speak("Pouštím hudbu") }
                    CommandType.OPEN_APP -> { try { val opened = appControl.launchAppByName(parsed.query); tts.speak(if (opened) "Spouštím ${parsed.query}" else "Aplikaci $parsed.query jsem nenašel") } catch (e: Exception) {} }
                    CommandType.PAUSE_MUSIC -> { try { musicPlayer.stop() } catch (e: Exception) {}; tts.speak("Hudba pozastavena") }
                    CommandType.NEXT_TRACK -> { try { musicPlayer.playNext() } catch (e: Exception) {}; tts.speak("Další skladba") }
                    CommandType.PREV_TRACK -> { try { musicPlayer.playPrevious() } catch (e: Exception) {}; tts.speak("Předchozí skladba") }
                    CommandType.VOLUME_UP -> { adjustVolume(1.15f); tts.speak("Zesíleno") }
                    CommandType.VOLUME_DOWN -> { adjustVolume(0.85f); tts.speak("Ztlumeno") }
                    CommandType.NOTE_TAKE -> { val note = noteStore.add(parsed.query); tts.speak("Uloženo poznámka ${note.id}: ${parsed.query}") }
                    CommandType.NOTE_READ -> { val notes = noteStore.getAll(); if (notes.isEmpty()) tts.speak("Nemáš žádné poznámky") else { val text = notes.take(5).joinToString(". ") { "${it.id}: ${it.text}" }; tts.speak(text) } }
                    CommandType.NOTE_DELETE -> { if (noteStore.getById(parsed.minutes) != null) { noteStore.delete(parsed.minutes); tts.speak("Smazáno poznámka ${parsed.minutes}") } else tts.speak("Poznámka ${parsed.minutes} nenalezena") }
                    CommandType.NOTES_LIST -> { val count = noteStore.count(); tts.speak("Máš $count poznámek") }
                    CommandType.ASK_AI -> {
                        if (localLLM.isAvailable()) {
                            serviceScope.launch {
                                _status.value = "Orion přemýšlí (offline)..."
                                val reply = localLLM.sendMessage(parsed.query, app.prefs.getFacts())
                                tts.speak(reply)
                                resetToWakeWord()
                            }
                        } else {
                            tts.speak("Lokální AI není dostupná. Nainstaluj Ollamu pro offline odpovědi.")
                        }
                    }
                    CommandType.UNKNOWN -> {
                        if (!isOnline) tts.speak("Nerozuměl jsem. Zkus: timer, budík, pusť hudbu, nebo otevři aplikaci.")
                        else tts.speak("Promiň, nerozuměl jsem. Zkus to jinak.")
                    }
                }
            } catch (e: Exception) {
                Log.e("OrionService", "offline parse failed", e)
                tts.speak("Došlo k chybě při zpracování příkazu.")
            }
            GlobalScope.launch {
                delay(2000)
                resetToWakeWord()
            }
        }
    }

    private fun resetToWakeWord() {
        try {
            speechHelper?.destroy()
            speechHelper = null
            wakeWordListen = true
            currentWakeWord = app.prefs.wakeWord
            _status.value = "Čekám na '$currentWakeWord'..."
            updateNotification("Orion čeká na \"$currentWakeWord\"")
            startWakeWordDetection()
        } catch (e: Exception) {
            Log.e("OrionService", "resetToWakeWord failed", e)
        }
    }

    private fun startListening() {
        _isListening.value = true
        try {
            wakeWordDetector?.destroy()
            wakeWordDetector = null
            speechHelper?.destroy()
            speechHelper = null
            startCommandListening()
        } catch (e: Exception) {
            Log.e("OrionService", "startListening failed", e)
        }
    }

    private fun stopListening() {
        _isListening.value = false
        try {
            speechHelper?.destroy()
            speechHelper = null
            wakeWordDetector?.destroy()
            wakeWordDetector = null
            wakeWordListen = false
            _status.value = "Pozastaveno"
            updateNotification("Orion pozastaven")
        } catch (e: Exception) {
            Log.e("OrionService", "stopListening failed", e)
        }
    }

    private fun updateNotification(text: String) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification(text))
        } catch (e: Exception) {
            Log.e("OrionService", "updateNotification failed", e)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun adjustVolume(multiplier: Float) {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            var newVol = (current * multiplier).toInt()
            newVol = newVol.coerceIn(0, max)
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
        } catch (e: Exception) {
            Log.e("OrionService", "Volume error", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            serviceScope.cancel()
            speechHelper?.destroy()
            wakeWordDetector?.destroy()
            tts.shutdown()
            musicPlayer.release()
        } catch (e: Exception) {
            Log.e("OrionService", "onDestroy failed", e)
        }
    }
}
