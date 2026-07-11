package com.orion.app.speech

import android.util.Log
import java.util.regex.Pattern

data class ParsedCommand(
    val type: CommandType,
    val raw: String,
    val minutes: Int = 0,
    val hours: Int = 0,
    val query: String = ""
)

enum class CommandType {
    TIMER,
    ALARM,
    STOP,
    PLAY_MUSIC,
    OPEN_APP,
    UNKNOWN
}

class OfflineCommandParser {
    private val tag = "OfflineParser"

    fun parse(input: String): ParsedCommand {
        val text = input.lowercase().trim()
        Log.d(tag, "Parsing: $text")

        // Wake word cleanup already done before calling this

        // Timer: "nastav timer na 5 minut" / "budík za 10 minut"
        val timerMin = extractMinutes(text, listOf("timer", "časovač", "odpočet"))
        if (timerMin > 0) {
            return ParsedCommand(CommandType.TIMER, input, minutes = timerMin)
        }

        // Alarm: "nastav budík na 7:30" / "vzbud mě v 7 hodin"
        val alarm = extractAlarm(text)
        if (alarm != null) {
            return ParsedCommand(CommandType.ALARM, input, hours = alarm.first, minutes = alarm.second)
        }

        // Stop / cancel
        if (text.contains("stop") || text.contains("zastav") ||
            text.contains("zruš") || text.contains("ukonči") ||
            text.contains("přestaň")) {
            return ParsedCommand(CommandType.STOP, input)
        }

        // Music
        if (text.contains("pust") && (text.contains("hudb") || text.contains("písnič") ||
                text.contains("píseň") || text.contains("muzik") || text.contains("song")) ||
            text.contains("hraj") && text.contains("hudb") ||
            text.contains("přehraj")) {
            val query = extractMusicQuery(text)
            return ParsedCommand(CommandType.PLAY_MUSIC, input, query = query)
        }

        // Open app
        val openMatch = Regex("(?:otevři|spusť|zapni)\\s+(.+)").find(text)
        if (openMatch != null) {
            return ParsedCommand(CommandType.OPEN_APP, input, query = openMatch.groupValues[1].trim())
        }

        return ParsedCommand(CommandType.UNKNOWN, input)
    }

    private fun extractMinutes(text: String, keywords: List<String>): Int {
        for (kw in keywords) {
            if (text.contains(kw)) {
                // "na 5 minut"
                val m = Regex("(\\d+)\\s*(?:minut|min|m)?").find(text)
                if (m != null) return m.groupValues[1].toIntOrNull() ?: 0
                // "za 10 minut"
                val m2 = Regex("za\\s*(\\d+)").find(text)
                if (m2 != null) return m2.groupValues[1].toIntOrNull() ?: 0
            }
        }
        return 0
    }

    private fun extractAlarm(text: String): Pair<Int, Int>? {
        if (!text.contains("budík") && !text.contains("vzbud") && !text.contains("probud")) {
            return null
        }
        // "na 7:30"
        val time = Regex("(\\d{1,2})[:.](\\d{2})").find(text)
        if (time != null) {
            val h = time.groupValues[1].toIntOrNull() ?: return null
            val m = time.groupValues[2].toIntOrNull() ?: return null
            if (h in 0..23 && m in 0..59) return Pair(h, m)
        }
        // "v 7 hodin 30"
        val hod = Regex("v\\s*(\\d{1,2})\\s*(?:hodin|hod)").find(text)
        if (hod != null) {
            val h = hod.groupValues[1].toIntOrNull() ?: return null
            val minM = Regex("(\\d{1,2})\\s*(?:minut|min)").find(text)
            val m = minM?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (h in 0..23) return Pair(h, m)
        }
        return null
    }

    private fun extractMusicQuery(text: String): String {
        val m = Regex("(?:písničku|píseň|skladbu|song)\\s+(.+)").find(text)
        return m?.groupValues?.get(1)?.trim() ?: ""
    }
}
