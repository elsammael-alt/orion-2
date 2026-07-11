package com.orion.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.orion.app.data.PreferenceStore

class OrionApp : Application() {
    lateinit var prefs: PreferenceStore
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceStore(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "orion_channel",
            "Orion Assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Orion background listening"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
}
