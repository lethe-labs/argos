package io.github.lethe_labs.argos.platform

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import io.github.lethe_labs.argos.core.usage.AppEvent

/**
 * Reads the app usage history Android keeps by itself, so it covers the
 * absence even though Argos was not running. Needs the special "usage access"
 * the user grants in the system settings; no root.
 *
 * Android only keeps these events for a few days, so a longer absence loses
 * its beginning.
 */
object UsageReader {

    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    @Suppress("DEPRECATION")
    fun hasAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Null when the history cannot be read, which the report shows as a blind eye. */
    @Suppress("DEPRECATION")
    fun events(context: Context, fromMs: Long, toMs: Long): List<AppEvent>? {
        if (!hasAccess(context)) return null
        return runCatching {
            val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
            val events = usage.queryEvents(fromMs, toMs)
            val out = mutableListOf<AppEvent>()
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                // Same values as ACTIVITY_RESUMED and ACTIVITY_PAUSED, which
                // only exist from API 29.
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> out += AppEvent(e.timeStamp, e.packageName, foreground = true)
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> out += AppEvent(e.timeStamp, e.packageName, foreground = false)
                }
            }
            out
        }.getOrNull()
    }

    /**
     * Not worth reporting: Argos itself, the home screen and the system UI
     * are on screen every time the phone is used at all.
     */
    fun ignoredPackages(context: Context): Set<String> {
        val home = runCatching {
            context.packageManager
                .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        return home.toSet() + context.packageName + "com.android.systemui"
    }
}
