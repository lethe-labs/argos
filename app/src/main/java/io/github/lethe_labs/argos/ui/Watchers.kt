package io.github.lethe_labs.argos.ui

import androidx.annotation.StringRes
import io.github.lethe_labs.argos.R
import io.github.lethe_labs.argos.core.snapshot.Anomaly.Kind

/**
 * The eyes of Argos, clockwise from the top of the emblem. Each one
 * watches one family of signals, so a report reads as "the SIM eye saw
 * something" rather than as a flat list.
 *
 * [icon] is SVG path data on a 24×24 grid, drawn as a 1.6 stroke.
 */
enum class Watcher(@StringRes val label: Int, @StringRes val role: Int, val icon: String) {
    LOCK(
        R.string.eye_lock, R.string.eye_lock_role,
        "M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2z M8 11V8a4 4 0 0 1 8 0v3",
    ),
    GAZE(
        R.string.eye_gaze, R.string.eye_gaze_role,
        "M5 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z " +
            "M15 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z " +
            "M5 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z " +
            "M14 17a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
    ),
    WAKE(R.string.eye_wake, R.string.eye_wake_role, "M12 3v8 M6.3 7.5a8 8 0 1 0 11.4 0"),
    SIM(
        R.string.eye_sim, R.string.eye_sim_role,
        "M7 3h7l4 4v14H7z M10.5 11h4a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z",
    ),
    APPS(
        R.string.eye_apps, R.string.eye_apps_role,
        "M3.5 7.5L12 3l8.5 4.5v9L12 21l-8.5 -4.5z M3.5 7.5L12 12l8.5 -4.5 M12 12v9",
    ),
    CLOCK(R.string.eye_clock, R.string.eye_clock_role, "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0 M12 7.5V12l3 2"),
    CABLE(
        R.string.eye_cable, R.string.eye_cable_role,
        "M12 3v14 M8.5 6.5L12 3l3.5 3.5 M12 13l-4 -2.5V8 M12 15l4 -2.5V10 M10 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    ),
    SHADOW(
        R.string.eye_shadow, R.string.eye_shadow_role,
        "M2.5 12C2.5 12 6 5.5 12 5.5C18 5.5 21.5 12 21.5 12C21.5 12 18 18.5 12 18.5C6 18.5 2.5 12 2.5 12z " +
            "M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0 M4 20L20 4",
    ),
    SEAL(
        R.string.eye_seal, R.string.eye_seal_role,
        "M6 10a6 6 0 1 0 12 0a6 6 0 1 0 -12 0 M9 15.2L8 21l4 -2l4 2l-1 -5.8 M10 10l1.5 1.5L14 9",
    ),
    ;

    companion object {
        fun of(kind: Kind): Watcher = when (kind) {
            Kind.UNLOCK_FAILED, Kind.UNLOCK_SUCCEEDED, Kind.ADMIN_DISABLED -> LOCK
            Kind.APP_OPENED, Kind.USAGE_ACCESS_REVOKED, Kind.USAGE_ACCESS_MISSING -> GAZE
            Kind.REBOOTED -> WAKE
            Kind.SIM_CHANGED, Kind.SIM_REMOVED, Kind.SIM_INSERTED,
            Kind.SIM_PHOTO, Kind.SIM_PHOTO_FAILED, Kind.PHOTO_TAMPERED -> SIM
            Kind.APP_INSTALLED, Kind.APP_REMOVED -> APPS
            Kind.CLOCK_CHANGED -> CLOCK
            Kind.ADB_ENABLED, Kind.DEVELOPER_OPTIONS_ENABLED -> CABLE
            Kind.ACCESSIBILITY_SERVICE_ADDED, Kind.DEVICE_ADMIN_ADDED -> SHADOW
        }
    }
}
