package com.orion.app.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import com.orion.app.data.PreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiClient(private val prefs: PreferenceStore) {
    private val tag = "GeminiClient"
    private var model: GenerativeModel? = null
    private val history = mutableListOf<Content>()

    private fun buildModel(): GenerativeModel? {
        val key = prefs.apiKey
        if (key.isBlank()) return null
        val name = prefs.aiName
        return GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = key,
            systemInstruction = content {
                text("""You are $name, a voice assistant running on Android.
Your name is $name. You speak Czech.
You are helpful, concise, and friendly.
You can control apps on the phone, set timers and alarms, play music from the device storage.
The user's facts and preferences:
${prefs.getFacts().entries.joinToString("\n") { "- ${it.key}: ${it.value}" }}
""")
            }
        )
    }

    fun isReady(): Boolean = prefs.apiKey.isNotBlank()

    suspend fun sendMessage(text: String): String = withContext(Dispatchers.IO) {
        try {
            if (model == null) model = buildModel()
            val m = model ?: return@withContext "❌ Gemini API klíč není nastaven."

            val chat = m.startChat(history.toList())
            val response = chat.sendMessage(text)
            val reply = response.text ?: "☐ (žádná odpověď)"

            // Store context
            history.add(content { text(text) })
            history.add(content { text(reply) })
            if (history.size > 40) {
                history.removeAt(0)
                history.removeAt(0)
            }

            // Extract facts to remember
            extractAndStoreFacts(text)

            reply
        } catch (e: Exception) {
            Log.e(tag, "Gemini error", e)
            "❌ Chyba: ${e.localizedMessage ?: "neznámá"}"
        }
    }

    private fun extractAndStoreFacts(text: String) {
        // Simple fact extraction from user statements
        val patterns = listOf(
            Regex("jsem\\s+(.+?)(?:\\.|,|\$)", RegexOption.IGNORE_CASE),
            Regex("mám rád\\s+(.+?)(?:\\.|,|\$)", RegexOption.IGNORE_CASE),
            Regex("jmenuji se\\s+(.+?)(?:\\.|,|\$)", RegexOption.IGNORE_CASE),
            Regex("bydlím v\\s+(.+?)(?:\\.|,|\$)", RegexOption.IGNORE_CASE),
            Regex("je mi\\s+(\\d+)\\s*let", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val value = match.groupValues[1].trim()
                when {
                    text.contains("jsem", ignoreCase = true) &&
                        !text.contains("jmenuji", ignoreCase = true) &&
                        !text.contains("bydlím", ignoreCase = true) ->
                        prefs.saveFact("info", value)
                    text.contains("mám rád", ignoreCase = true) ->
                        prefs.saveFact("má rád", value)
                    text.contains("jmenuji", ignoreCase = true) ->
                        prefs.saveFact("jmenuje se", value)
                    text.contains("bydlím", ignoreCase = true) ->
                        prefs.saveFact("bydlí", value)
                    match.groupValues.size > 1 && text.contains("let", ignoreCase = true) ->
                        prefs.saveFact("věk", match.groupValues[1])
                }
            }
        }
    }
}
