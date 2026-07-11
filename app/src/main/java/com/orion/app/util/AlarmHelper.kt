package com.orion.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AlarmHelper(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val _timerActive = MutableStateFlow(false)
    val timerActive: StateFlow<Boolean> = _timerActive

    private val _timerRemaining = MutableStateFlow(0)
    val timerRemaining: StateFlow<Int> = _timerRemaining

    fun setTimer(minutes: Int) {
        val triggerTime = SystemClock.elapsedRealtime() + (minutes * 60_000L)
        val intent = Intent(context, TimerReceiver::class.java).apply {
            putExtra("type", "timer")
            putExtra("duration", minutes * 60_000L)
        }
        val pi = PendingIntent.getBroadcast(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pi
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pi
                )
            }
        } else {
            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pi
            )
        }

        _timerActive.value = true
        _timerRemaining.value = minutes * 60

        Toast.makeText(context, "⏰ Timer na $minutes minut", Toast.LENGTH_SHORT).show()
    }

    fun setAlarm(hour: Int, minute: Int) {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
        calendar.set(java.util.Calendar.MINUTE, minute)
        calendar.set(java.util.Calendar.SECOND, 0)
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        val intent = Intent(context, TimerReceiver::class.java).apply {
            putExtra("type", "alarm")
        }
        val pi = PendingIntent.getBroadcast(
            context, 1002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pi
        )

        Toast.makeText(context, "🔔 Budík na ${hour}:${String.format("%02d", minute)}", Toast.LENGTH_SHORT).show()
    }

    fun cancelAll() {
        val pi1 = PendingIntent.getBroadcast(
            context, 1001, Intent(context, TimerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pi2 = PendingIntent.getBroadcast(
            context, 1002, Intent(context, TimerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi1)
        alarmManager.cancel(pi2)
        _timerActive.value = false
        _timerRemaining.value = 0
        Toast.makeText(context, "⏹ Vše zrušeno", Toast.LENGTH_SHORT).show()
    }

    class TimerReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val type = intent.getStringExtra("type") ?: "timer"
            val message = if (type == "alarm") "🔔 Budík! Vstávej!" else "⏰ Čas vypršel!"
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
