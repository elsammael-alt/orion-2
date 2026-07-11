package com.orion.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferenceStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("orion_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Wake word
    var wakeWord: String
        get() = prefs.getString("wake_word", "dajen") ?: "dajen"
        set(value) = prefs.edit().putString("wake_word", value).apply()

    // Gemini/Orion name
    var aiName: String
        get() = prefs.getString("ai_name", "Orion") ?: "Orion"
        set(value) = prefs.edit().putString("ai_name", value).apply()

    // Gemini API key
    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    // Music folder path
    var musicFolder: String
        get() = prefs.getString("music_folder", "") ?: ""
        set(value) = prefs.edit().putString("music_folder", value).apply()

    // Memory facts
    fun getFacts(): MutableMap<String, String> {
        val json = prefs.getString("facts", "{}") ?: "{}"
        val type = object : TypeToken<Map<String, String>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun saveFact(key: String, value: String) {
        val facts = getFacts()
        facts[key] = value
        prefs.edit().putString("facts", gson.toJson(facts)).apply()
    }

    fun removeFact(key: String) {
        val facts = getFacts()
        facts.remove(key)
        prefs.edit().putString("facts", gson.toJson(facts)).apply()
    }
}
