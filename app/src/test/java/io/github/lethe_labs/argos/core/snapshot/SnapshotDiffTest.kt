package io.github.lethe_labs.argos.core.snapshot

import io.github.lethe_labs.argos.core.later
import io.github.lethe_labs.argos.core.deviceSnapshot
import io.github.lethe_labs.argos.core.snapshot.Anomaly.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotDiffTest {

    private val hour = 3_600_000L

    private fun kinds(before: DeviceSnapshot, after: DeviceSnapshot) =
        SnapshotDiff.compare(before, after).map { it.kind }

    @Test
    fun `nothing changed reports nothing`() {
        val before = deviceSnapshot()
        assertTrue(SnapshotDiff.compare(before, before.later(5 * hour)).isEmpty())
    }

    @Test
    fun `boot counter increase is reported with the number of reboots`() {
        val before = deviceSnapshot(bootCount = 10)
        val after = before.copy(bootCount = 12, takenAtWallMs = before.takenAtWallMs + hour, takenAtElapsedMs = 1_000L)
        val anomaly = SnapshotDiff.compare(before, after).single { it.kind == Kind.REBOOTED }
        assertEquals("2", anomaly.detail)
    }

    @Test
    fun `reboot is still caught when the boot counter is unreadable`() {
        val before = deviceSnapshot(bootCount = null, elapsed = 900_000L)
        val after = before.copy(takenAtWallMs = before.takenAtWallMs + hour, takenAtElapsedMs = 2_000L)
        val anomaly = SnapshotDiff.compare(before, after).single { it.kind == Kind.REBOOTED }
        assertEquals("?", anomaly.detail)
    }

    @Test
    fun `clock change within one boot is detected`() {
        val before = deviceSnapshot()
        val after = before.later(hour).copy(takenAtWallMs = before.takenAtWallMs + hour - 3 * hour)
        assertTrue(Kind.CLOCK_CHANGED in kinds(before, after))
    }

    @Test
    fun `small clock drift is tolerated`() {
        val before = deviceSnapshot()
        val after = before.later(hour).let { it.copy(takenAtWallMs = it.takenAtWallMs + 30_000L) }
        assertTrue(kinds(before, after).isEmpty())
    }

    @Test
    fun `clock is not compared across a reboot`() {
        val before = deviceSnapshot(bootCount = 10)
        val after = before.copy(bootCount = 11, takenAtWallMs = before.takenAtWallMs + 9 * hour, takenAtElapsedMs = 1_000L)
        assertTrue(Kind.CLOCK_CHANGED !in kinds(before, after))
    }

    @Test
    fun `usb debugging switched on is critical`() {
        val before = deviceSnapshot(adb = false)
        val anomaly = SnapshotDiff.compare(before, before.later(hour).copy(adbEnabled = true)).single()
        assertEquals(Kind.ADB_ENABLED, anomaly.kind)
        assertEquals(Severity.CRITICAL, anomaly.severity)
    }

    @Test
    fun `usb debugging already on at departure is not an anomaly`() {
        val before = deviceSnapshot(adb = true)
        assertTrue(kinds(before, before.later(hour)).isEmpty())
    }

    @Test
    fun `unreadable setting is never reported as a change`() {
        val before = deviceSnapshot(adb = null)
        assertTrue(kinds(before, before.later(hour).copy(adbEnabled = true)).isEmpty())
    }

    @Test
    fun `new app is reported`() {
        val before = deviceSnapshot()
        val after = before.later(hour).copy(installedPackages = before.installedPackages + "com.spy.agent")
        val anomaly = SnapshotDiff.compare(before, after).single()
        assertEquals(Kind.APP_INSTALLED, anomaly.kind)
        assertEquals("com.spy.agent", anomaly.detail)
    }

    @Test
    fun `removed app is informational`() {
        val before = deviceSnapshot()
        val after = before.later(hour).copy(installedPackages = setOf("com.android.settings"))
        val anomaly = SnapshotDiff.compare(before, after).single()
        assertEquals(Kind.APP_REMOVED, anomaly.kind)
        assertEquals(Severity.INFO, anomaly.severity)
    }

    @Test
    fun `an empty app list on return is a read failure, not mass uninstall`() {
        val before = deviceSnapshot()
        assertTrue(kinds(before, before.later(hour).copy(installedPackages = emptySet())).isEmpty())
    }

    @Test
    fun `new accessibility service and device admin are critical`() {
        val before = deviceSnapshot()
        val after = before.later(hour).copy(
            accessibilityServices = setOf("com.spy.agent/.Watcher"),
            deviceAdmins = before.deviceAdmins + "com.spy.agent/.Admin",
        )
        val anomalies = SnapshotDiff.compare(before, after)
        assertEquals(2, anomalies.size)
        assertTrue(anomalies.all { it.severity == Severity.CRITICAL })
    }

    @Test
    fun `sim operator change is reported`() {
        val before = deviceSnapshot(simOperator = "20801")
        val anomaly = SnapshotDiff.compare(before, before.later(hour).copy(simOperator = "20810")).single()
        assertEquals(Kind.SIM_CHANGED, anomaly.kind)
    }
}
