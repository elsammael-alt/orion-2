package com.orion.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Note(val id: Int, val text: String, val timestamp: Long)

class NoteStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("orion_notes", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAll(): List<Note> {
        val json = prefs.getString("notes", "[]") ?: "[]"
        val type = object : TypeToken<List<Note>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(text: String): Note {
        val notes = getAll().toMutableList()
        val nextId = (notes.maxOfOrNull { it.id } ?: 0) + 1
        val note = Note(nextId, text, System.currentTimeMillis())
        notes.add(note)
        save(notes)
        return note
    }

    fun getById(id: Int): Note? = getAll().firstOrNull { it.id == id }

    fun delete(id: Int) {
        val notes = getAll().filter { it.id != id }
        save(notes)
    }

    fun clear() {
        prefs.edit().remove("notes").apply()
    }

    fun count(): Int = getAll().size

    private fun save(notes: List<Note>) {
        prefs.edit().putString("notes", gson.toJson(notes)).apply()
    }
}
