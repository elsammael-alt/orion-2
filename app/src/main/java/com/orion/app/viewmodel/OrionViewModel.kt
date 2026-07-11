package com.orion.app.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.orion.app.OrionApp
import com.orion.app.service.OrionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OrionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OrionApp

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _status = MutableStateFlow("Neaktivní")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _apiKey = MutableStateFlow(app.prefs.apiKey)
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _wakeWord = MutableStateFlow(app.prefs.wakeWord)
    val wakeWord: StateFlow<String> = _wakeWord.asStateFlow()

    private val _aiName = MutableStateFlow(app.prefs.aiName)
    val aiName: StateFlow<String> = _aiName.asStateFlow()

    private val _musicFolder = MutableStateFlow(app.prefs.musicFolder)
    val musicFolder: StateFlow<String> = _musicFolder.asStateFlow()

    fun setApiKey(key: String) {
        app.prefs.apiKey = key
        _apiKey.value = key
        val intent = Intent(getApplication(), OrionService::class.java).apply {
            action = OrionService.ACTION_SET_KEY
            putExtra("key", key)
        }
        getApplication<Application>().startService(intent)
    }

    fun setWakeWord(word: String) {
        app.prefs.wakeWord = word
        _wakeWord.value = word
    }

    fun setAiName(name: String) {
        app.prefs.aiName = name
        _aiName.value = name
    }

    fun setMusicFolder(path: String) {
        app.prefs.musicFolder = path
        _musicFolder.value = path
    }

    fun startListening() {
        val intent = Intent(getApplication(), OrionService::class.java).apply {
            action = OrionService.ACTION_START
        }
        getApplication<Application>().startForegroundService(intent)
        _listening.value = true
    }

    fun stopListening() {
        val intent = Intent(getApplication(), OrionService::class.java).apply {
            action = OrionService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
        _listening.value = false
    }

    fun addLog(entry: String) {
        _log.value = (_log.value + entry).takeLast(50)
    }

    fun setStatus(s: String) {
        _status.value = s
    }
}
