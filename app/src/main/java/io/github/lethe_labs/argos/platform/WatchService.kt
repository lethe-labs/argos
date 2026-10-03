package io.github.lethe_labs.argos.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.lethe_labs.argos.ArgosApp
import io.github.lethe_labs.argos.MainActivity
import io.github.lethe_labs.argos.R
import io.github.lethe_labs.argos.core.journal.JournalEventType
import io.github.lethe_labs.argos.core.journal.SimSlots
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Runs for the length of an absence when the SIM photo is on, and takes a
 * front photo when a SIM card is pulled out.
 *
 * Android only lets an app use the camera from the background through a
 * camera foreground service started while the app is on screen, hence a
 * service started from "I'm leaving", with its required notification. It
 * cannot be restarted after a reboot (Android 14 forbids camera services
 * from boot), so a reboot ends the photos; the reboot itself is reported.
 */
class WatchService : Service() {

    private var receiver: BroadcastReceiver? = null
    private var slots: List<String> = emptyList()
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startWatching()) stopSelf()
        return START_NOT_STICKY
    }

    private fun startWatching(): Boolean {
        if (receiver != null) return true
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return false
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type) }
            .onFailure { return false }

        slots = SimReceiver.readSlots(this).orEmpty()
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onSimChanged()
        }
        val filter = IntentFilter().apply {
            addAction("android.telephony.action.CARRIER_CONFIG_CHANGED")
            addAction("android.intent.action.SIM_STATE_CHANGED")
        }
        // Exported so the system's SIM broadcasts always arrive. A forged one
        // is harmless: the slots are read again before any photo is taken.
        ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_EXPORTED)
        receiver = r
        return true
    }

    private fun onSimChanged() {
        val now = SimReceiver.readSlots(this) ?: return
        val removed = now.indices.any { i -> now[i] == SimSlots.ABSENT && slots.getOrNull(i) == SimSlots.PRESENT }
        // A card still initialising says nothing: keep the last known state.
        slots = now.mapIndexed { i, s -> if (s == SimSlots.UNKNOWN) slots.getOrNull(i) ?: s else s }
        if (removed) worker.execute { takePhoto() }
    }

    private fun takePhoto() {
        val container = ArgosApp.container(this)
        val at = System.currentTimeMillis()
        val stored = FrontCamera.capture(this)?.let { container.captures.save(it, at) }
        if (stored == null) {
            container.journal.append(JournalEventType.PHOTO_FAILED, timestampMs = at)
        } else {
            container.journal.append(JournalEventType.PHOTO_TAKEN, "${stored.name}|${stored.sha256}", at)
        }
    }

    override fun onDestroy() {
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        worker.shutdown()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.watch_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_watch)
            .setContentTitle(getString(R.string.watch_notification))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "watch"
        private const val NOTIFICATION_ID = 1

        /** Must be called while Argos is on screen, or Android refuses the camera. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchService::class.java))
        }
    }
}
