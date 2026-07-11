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
    PAUSE_MUSIC,
    NEXT_TRACK,
    PREV_TRACK,
    VOLUME_UP,
    VOLUME_DOWN,
    NOTE_TAKE,
    NOTE_READ,
    NOTE_DELETE,
    OPEN_APP,
    NOTES_LIST,
    ASK_AI,
    UNKNOWN
}

class OfflineCommandParser {
    private val tag = "OfflineParser"

    fun parse(input: String): ParsedCommand {
        val text = input.lowercase().trim()
        Log.d(tag, "Parsing: $text")

        // Timer
        if (matchesAny(text, listOf("timer", "časovač", "odpočet"))) {
            val min = extractMinutes(text)
            if (min > 0) return ParsedCommand(CommandType.TIMER, input, minutes = min)
        }

        // Alarm
        val alarm = extractAlarm(text)
        if (alarm != null) {
            return ParsedCommand(CommandType.ALARM, input, hours = alarm.first, minutes = alarm.second)
        }

        // Stop / cancel
        if (matchesAny(text, listOf("stop", "zastav", "zruš", "ukonči", "přestaň"))) {
            return ParsedCommand(CommandType.STOP, input)
        }

        // Music - play
        if (matchesAny(text, listOf("pusť", "přehraj", "hraj", "zahraj")) &&
            matchesAny(text, listOf("hudb", "písnič", "píseň", "muzik", "song", "něco"))) {
            val query = extractMusicQuery(text)
            return ParsedCommand(CommandType.PLAY_MUSIC, input, query = query)
        }
        // Just "pusť hudbu" / "hraj"
        if ((text.contains("pusť") || text.contains("hraj")) && 
            (text.contains("hud") || text.contains("písnič") || text.contains("něco"))) {
            return ParsedCommand(CommandType.PLAY_MUSIC, input)
        }

        // Music control
        if (matchesAny(text, listOf("pauza", "pozastav", "zastav hudbu", "stop hudbu"))) {
            return ParsedCommand(CommandType.PAUSE_MUSIC, input)
        }
        if (matchesAny(text, listOf("další", "další písnič", "skip", "přeskoč", "další skladba"))) {
            return ParsedCommand(CommandType.NEXT_TRACK, input)
        }
        if (matchesAny(text, listOf("předchozí", "minulá", "předchozí písnič"))) {
            return ParsedCommand(CommandType.PREV_TRACK, input)
        }
        if (matchesAny(text, listOf("ztlum", "ztiš", "snížit hlasitos", "míň"))) {
            return ParsedCommand(CommandType.VOLUME_DOWN, input)
        }
        if (matchesAny(text, listOf("zesil", "zesílit", "zvýšit hlasitos", "víc", "nahlas"))) {
            return ParsedCommand(CommandType.VOLUME_UP, input)
        }

        // Notes
        // "zapiš / poznamenej si / ulož ..."
        val noteTakeMatch = Regex("(?:zapiš|poznamenej|ulož|zapamat.|zapiš si|napiš si)\\s+(.+)").find(text)
        if (noteTakeMatch != null) {
            return ParsedCommand(CommandType.NOTE_TAKE, input, query = noteTakeMatch.groupValues[1].trim())
        }
        if (matchesAny(text, listOf("poznámk", "noty", "notes")) && 
            matchesAny(text, listOf("čti", "přečti", "ukaž", "co mám"))) {
            return ParsedCommand(CommandType.NOTE_READ, input)
        }
        // "smaž / vymaž poznámku"
        if (matchesAny(text, listOf("smaž poznámk", "vymaž poznámk", "smaž not")) && extractNoteNum(text) > 0) {
            return ParsedCommand(CommandType.NOTE_DELETE, input, minutes = extractNoteNum(text))
        }
        if (matchesAny(text, listOf("seznam poznámek", "co mám v poznámkách", "poznámky", "noty"))) {
            return ParsedCommand(CommandType.NOTES_LIST, input)
        }

        // AI question (fallback: send to local LLM)
        if (text.startsWith("orione") || text.startsWith("ai") || 
            text.length > 10 && (text.contains("?") || text.contains("co je") || 
            text.contains("proč") || text.contains("jak") || text.contains("kdo"))) {
            return ParsedCommand(CommandType.ASK_AI, input, query = text)
        }

        // Open app
        val openMatch = Regex("(?:otevři|spusť|zapni)\\s+(.+)").find(text)
        if (openMatch != null) {
            return ParsedCommand(CommandType.OPEN_APP, input, query = openMatch.groupValues[1].trim())
        }

        return ParsedCommand(CommandType.UNKNOWN, input)
    }

    private fun matchesAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun extractMinutes(text: String): Int {
        val m = Regex("(\\d+)\\s*(?:minut|min|m)?").find(text)
        if (m != null) return m.groupValues[1].toIntOrNull() ?: 0
        val m2 = Regex("za\\s*(\\d+)").find(text)
        if (m2 != null) return m2.groupValues[1].toIntOrNull() ?: 0
        return 0
    }

    private fun extractAlarm(text: String): Pair<Int, Int>? {
        if (!matchesAny(text, listOf("budík", "vzbud", "probud"))) return null
        val time = Regex("(\\d{1,2})[:.](\\d{2})").find(text)
        if (time != null) {
            val h = time.groupValues[1].toIntOrNull() ?: return null
            val m = time.groupValues[2].toIntOrNull() ?: return null
            if (h in 0..23 && m in 0..59) return Pair(h, m)
        }
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

    private fun extractNoteNum(text: String): Int {
        val m = Regex("(\\d+)").find(text)
        return m?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}
