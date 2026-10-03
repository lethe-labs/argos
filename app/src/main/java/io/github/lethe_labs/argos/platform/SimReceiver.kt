package io.github.lethe_labs.argos.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import io.github.lethe_labs.argos.ArgosApp
import io.github.lethe_labs.argos.core.journal.JournalEventType
import io.github.lethe_labs.argos.core.journal.SimSlots

/**
 * Journals SIM insertions and removals as they happen. Comparing departure and
 * return snapshots is not enough: a card taken out and put back in leaves both
 * identical.
 *
 * CARRIER_CONFIG_CHANGED is the trigger because it is sent whenever a card is
 * inserted, removed or loaded, needs no permission, and is one of the few
 * implicit broadcasts a manifest receiver still gets on Android 8+.
 * SIM_STATE_CHANGED is listened to as well, where the system still delivers it.
 */
class SimReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        record(context)
    }

    companion object {
        private val ACTIONS = setOf(
            "android.telephony.action.CARRIER_CONFIG_CHANGED",
            "android.intent.action.SIM_STATE_CHANGED",
        )

        /** Logs the current per-slot state, unless it is the one last logged. */
        fun record(context: Context) {
            val states = readSlots(context) ?: return
            if (states.all { it == SimSlots.UNKNOWN }) return
            ArgosApp.container(context).journal
                .appendIfChanged(JournalEventType.SIM_STATE, SimSlots.encode(states))
        }

        @Suppress("DEPRECATION")
        private fun readSlots(context: Context): List<String>? = runCatching {
            val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
            val slots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tm.activeModemCount else tm.phoneCount
            (0 until slots.coerceAtLeast(1)).map { slot ->
                when (runCatching { tm.getSimState(slot) }.getOrNull()) {
                    TelephonyManager.SIM_STATE_ABSENT -> SimSlots.ABSENT
                    null,
                    TelephonyManager.SIM_STATE_UNKNOWN,
                    TelephonyManager.SIM_STATE_NOT_READY,
                    TelephonyManager.SIM_STATE_CARD_IO_ERROR -> SimSlots.UNKNOWN
                    // Ready, PIN/PUK-locked, network-locked, restricted: a card is in.
                    else -> SimSlots.PRESENT
                }
            }
        }.getOrNull()
    }
}
