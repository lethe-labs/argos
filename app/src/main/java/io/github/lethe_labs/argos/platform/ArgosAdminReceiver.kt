package io.github.lethe_labs.argos.platform

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import io.github.lethe_labs.argos.ArgosApp
import io.github.lethe_labs.argos.R
import io.github.lethe_labs.argos.core.journal.JournalEventType

class ArgosAdminReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        journal(context).append(JournalEventType.UNLOCK_FAILED)
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, user: UserHandle) {
        journal(context).append(JournalEventType.UNLOCK_SUCCEEDED)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        journal(context).append(JournalEventType.ADMIN_ENABLED)
    }

    // Logged before monitoring stops, so switching Argos off to hide what
    // follows is itself on the record.
    override fun onDisabled(context: Context, intent: Intent) {
        journal(context).append(JournalEventType.ADMIN_DISABLED)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)

    private fun journal(context: Context) = ArgosApp.container(context).journal

    companion object {
        fun component(context: Context) = ComponentName(context, ArgosAdminReceiver::class.java)
    }
}
