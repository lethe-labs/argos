package io.github.lethe_labs.argos.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.lethe_labs.argos.ArgosApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        ArgosApp.container(context).journal.appendBootOnce(SnapshotCollector.readBootCount(context))
    }
}
