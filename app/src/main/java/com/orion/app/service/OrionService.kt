package com.orion.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.orion.app.MainActivity
import com.orion.app.OrionApp
import com.orion.app.R
import com.orion.app.ai.GeminiClient
import com.orion.app.appcontrol.AppControlHelper
import com.orion.app.speech.*
import com.orion.app.util.AlarmHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class OrionService : Service() {
    companion object {
        const val NOTIFICATION_ID = 1

        // Command actions
        const val ACTION_START = "com.orion.app.START_LISTENING"
        const val ACTION_STOP = "com.orion.app.STOP_LISTENING"
        const val ACTION_SET_KEY = "com.orion.app.SET_API_KEY"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var app: OrionApp
    private lateinit var gemini: GeminiClient
    private lateinit var tts: TextToSpeechHelper
    private lateinit var musicPlayer: MusicPlayer
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

    override fun onCreate() {
        super.onCreate()
        app = application as OrionApp
        gemini = GeminiClient(app.prefs)
        tts = TextToSpeechHelper(this) { /* done speaking */ }
        musicPlayer = MusicPlayer(this)
        appControl = AppControlHelper(this)
        alarmHelper = AlarmHelper(this)
        offlineParser = OfflineCommandParser()
        currentWakeWord = app.prefs.wakeWord

        startForeground(NOTIFICATION_ID, createNotification("Orion čeká na $currentWakeWord..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startListening()
            ACTION_STOP -> stopListening()
            ACTION_SET_KEY -> {
                app.prefs.apiKey = intent.getStringExtra("key") ?: ""
                gemini = GeminiClient(app.prefs) // re-create with new key
            }
            else -> if (wakeWordListen) startWakeWordDetection()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        wakeWordListen = true
        currentWakeWord = app.prefs.wakeWord
        _status.value = "Poslouchám na '$currentWakeWord'..."

        wakeWordDetector = SpeechRecognizerHelper(
            context = this,
            onResult = { text ->
                processWakeWord(text)
            },
            onError = {
                _status.value = "Wake word chyba, restart..."
            }
        )
        wakeWordDetector?.startListening()
        updateNotification("Čekám na \"$currentWakeWord\"")
    }

    private fun processWakeWord(text: String) {
        val lower = text.lowercase()
        if (lower.contains(currentWakeWord.lowercase())) {
            wakeWordDetector?.stopListening()
            wakeWordListen = false
            _status.value = "Rozpoznávám příkaz..."
            updateNotification("Rozpoznávám příkaz...")
            tts.speak("Ano?")
            // Small delay then start command listening
            GlobalScope.launch {
                delay(800)
                startCommandListening()
            }
        } else {
            // Check for stop/cancel on wake word level
            if (lower.contains("stop") || lower.contains("zruš") || lower.contains("přestaň")) {
                alarmHelper.cancelAll()
                musicPlayer.stop()
            }
        }
    }

    private fun startCommandListening() {
        speechHelper?.destroy()
        speechHelper = SpeechRecognizerHelper(
            context = this,
            onResult = { command ->
                _status.value = "Zpracovávám příkaz..."
                processCommand(command)
            },
            onPartial = { partial ->
                _status.value = "Poslouchám: $partial..."
            },
            onError = { error ->
                _status.value = "Chyba, vracím se k wake word"
                updateNotification("Orion čeká na \"$currentWakeWord\"")
                resetToWakeWord()
            }
        )
        speechHelper?.startListening()
    }

    private fun processCommand(command: String) {
        // First check if we're online and Gemini is ready
        val isOnline = isNetworkAvailable()
        val geminiReady = gemini.isReady()

        if (isOnline && geminiReady) {
            serviceScope.launch {
                _status.value = "Orion přemýšlí..."
                updateNotification("Odpovídám...")
                val reply = gemini.sendMessage(command)
                tts.speak(reply)
                delay(1000)
                resetToWakeWord()
            }
        } else {
            // Offline mode — local parsing
            val parsed = offlineParser.parse(command)
            when (parsed.type) {
                CommandType.TIMER -> {
                    alarmHelper.setTimer(parsed.minutes)
                    tts.speak("Timer na ${parsed.minutes} minut spuštěn")
                }
                CommandType.ALARM -> {
                    alarmHelper.setAlarm(parsed.hours, parsed.minutes)
                    tts.speak("Budík na ${parsed.hours}:${String.format("%02d", parsed.minutes)} nastaven")
                }
                CommandType.STOP -> {
                    alarmHelper.cancelAll()
                    musicPlayer.stop()
                    tts.speak("Vše zrušeno")
                }
                CommandType.PLAY_MUSIC -> {
                    musicPlayer.loadLocalTracks(app.prefs.musicFolder)
                    musicPlayer.playRandom()
                    tts.speak("Pouštím hudbu")
                }
                CommandType.OPEN_APP -> {
                    val opened = appControl.launchAppByName(parsed.query)
                    if (opened) tts.speak("Spouštím ${parsed.query}")
                    else tts.speak("Aplikaci $parsed.query jsem nenašel")
                }
                CommandType.UNKNOWN -> {
                    if (!isOnline) {
                        tts.speak("Nerozuměl jsem. Zkus: timer, budík, pusť hudbu, nebo otevři aplikaci.")
                    } else {
                        tts.speak("Promiň, nerozuměl jsem. Zkus to jinak.")
                    }
                }
            }
            GlobalScope.launch {
                delay(2000)
                resetToWakeWord()
            }
        }
    }

    private fun resetToWakeWord() {
        speechHelper?.destroy()
        speechHelper = null
        wakeWordListen = true
        currentWakeWord = app.prefs.wakeWord
        _status.value = "Čekám na '$currentWakeWord'..."
        updateNotification("Orion čeká na \"$currentWakeWord\"")
        startWakeWordDetection()
    }

    private fun startListening() {
        _isListening.value = true
        wakeWordDetector?.destroy()
        wakeWordDetector = null
        speechHelper?.destroy()
        speechHelper = null
        startCommandListening()
    }

    private fun stopListening() {
        _isListening.value = false
        speechHelper?.destroy()
        speechHelper = null
        wakeWordDetector?.destroy()
        wakeWordDetector = null
        wakeWordListen = false
        _status.value = "Pozastaveno"
        updateNotification("Orion pozastaven")
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        speechHelper?.destroy()
        wakeWordDetector?.destroy()
        tts.shutdown()
        musicPlayer.release()
    }
}
