package com.orion.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class LocalLLMClient {
    private val baseUrl = "http://127.0.0.1:11434/api"
    private var model = "llama3.2:1b"

    fun isAvailable(): Boolean {
        return try {
            val conn = URL("$baseUrl/tags").openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendMessage(text: String, facts: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = buildString {
                append("Jsi Orion, hlasový asistent. Odpovídej česky, stručně a přirozeně.")
                if (facts.isNotEmpty()) {
                    append(" Zde jsou fakta o uživateli:\n")
                    facts.forEach { (k, v) -> append("$k: $v\n") }
                }
            }

            val payload = """
            {
                "model": "$model",
                "system": "${systemPrompt.replace("\"", "\\\"")}",
                "prompt": "${text.replace("\"", "\\\"")}",
                "stream": false,
                "options": {
                    "temperature": 0.7,
                    "num_predict": 200
                }
            }
            """.trimIndent()

            val conn = URL("$baseUrl/generate").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            conn.outputStream.write(payload.toByteArray())

            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().readText()
                val response = parseResponse(resp)
                response ?: "☐ (žádná odpověď)"
            } else {
                "❌ Chyba připojení k Ollamě (HTTP ${conn.responseCode})"
            }
        } catch (e: Exception) {
            "❌ Lokální AI: ${e.localizedMessage ?: "neznámá chyba"}"
        }
    }

    private fun parseResponse(json: String): String? {
        val respKey = "\"response\":\""
        val start = json.indexOf(respKey)
        if (start == -1) return null
        val valueStart = start + respKey.length
        val sb = StringBuilder()
        var i = valueStart
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                sb.append(json[i + 1])
                i += 2
            } else if (c == '"') {
                break
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
