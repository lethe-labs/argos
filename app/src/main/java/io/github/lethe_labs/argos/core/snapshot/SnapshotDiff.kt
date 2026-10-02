package io.github.lethe_labs.argos.core.snapshot

import io.github.lethe_labs.argos.core.snapshot.Anomaly.Kind
import kotlin.math.abs

object SnapshotDiff {
    /** Beyond normal drift and NTP corrections: someone set the clock. */
    const val CLOCK_TOLERANCE_MS: Long = 2 * 60 * 1000L

    fun compare(before: DeviceSnapshot, after: DeviceSnapshot): List<Anomaly> {
        val out = mutableListOf<Anomaly>()

        // The boot counter survives the app being killed, so it catches reboots
        // even if Argos was not running. The monotonic clock going backwards is
        // the fallback on devices that do not expose the counter.
        val b0 = before.bootCount
        val b1 = after.bootCount
        val bootDelta = if (b0 != null && b1 != null) b1 - b0 else null
        val rebooted = (bootDelta != null && bootDelta > 0) ||
            after.takenAtElapsedMs < before.takenAtElapsedMs
        if (rebooted) {
            out += Anomaly(Kind.REBOOTED, Severity.WARNING, bootDelta?.takeIf { it > 0 }?.toString() ?: "?")
        } else {
            // Within one boot, wall-clock time and the monotonic clock must
            // advance together. A gap means the wall clock was changed, a common
            // way to confuse timestamps in logs. Not comparable across reboots,
            // since the monotonic clock restarts at zero.
            val wall = after.takenAtWallMs - before.takenAtWallMs
            val mono = after.takenAtElapsedMs - before.takenAtElapsedMs
            if (abs(wall - mono) > CLOCK_TOLERANCE_MS) {
                out += Anomaly(Kind.CLOCK_CHANGED, Severity.WARNING, (wall - mono).toString())
            }
        }

        // Only definite false -> true transitions count. A setting that was
        // already on at departure is the user's own configuration.
        if (before.adbEnabled == false && after.adbEnabled == true) {
            out += Anomaly(Kind.ADB_ENABLED, Severity.CRITICAL)
        }
        if (before.developerOptionsEnabled == false && after.developerOptionsEnabled == true) {
            out += Anomaly(Kind.DEVELOPER_OPTIONS_ENABLED, Severity.WARNING)
        }

        (after.installedPackages - before.installedPackages).sorted()
            .forEach { out += Anomaly(Kind.APP_INSTALLED, Severity.WARNING, it) }
        // An empty list on return means the read failed, not that every app was
        // uninstalled.
        if (after.installedPackages.isNotEmpty()) {
            (before.installedPackages - after.installedPackages).sorted()
                .forEach { out += Anomaly(Kind.APP_REMOVED, Severity.INFO, it) }
        }

        // New accessibility services and device admins are the signature of
        // stalkerware: they are how such apps read the screen and resist removal.
        (after.accessibilityServices - before.accessibilityServices).sorted()
            .forEach { out += Anomaly(Kind.ACCESSIBILITY_SERVICE_ADDED, Severity.CRITICAL, it) }
        (after.deviceAdmins - before.deviceAdmins).sorted()
            .forEach { out += Anomaly(Kind.DEVICE_ADMIN_ADDED, Severity.CRITICAL, it) }

        if (before.simState != after.simState || before.simOperator != after.simOperator) {
            out += Anomaly(
                Kind.SIM_CHANGED,
                Severity.WARNING,
                "${before.simOperator ?: "-"} → ${after.simOperator ?: "-"}",
            )
        }

        return out
    }
}
