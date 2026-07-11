package com.orion.app.appcontrol

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

class AppControlHelper(private val context: Context) {
    private val tag = "AppControl"

    data class AppInfo(val name: String, val packageName: String)

    fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, 0).map {
            AppInfo(
                name = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName
            )
        }.sortedBy { it.name }
    }

    fun launchApp(packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                true
            } else false
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch $packageName", e)
            false
        }
    }

    fun launchAppByName(query: String): Boolean {
        val apps = getInstalledApps()
        val match = apps.firstOrNull {
            it.name.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
        return if (match != null) {
            launchApp(match.packageName)
        } else false
    }

    fun killApp(packageName: String): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
            am.killBackgroundProcesses(packageName)
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to kill $packageName", e)
            false
        }
    }

    fun killAppByName(query: String): Boolean {
        val apps = getInstalledApps()
        val match = apps.firstOrNull {
            it.name.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
        return if (match != null) killApp(match.packageName) else false
    }
}
