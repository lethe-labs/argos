package io.github.lethe_labs.argos.platform

import android.app.admin.DevicePolicyManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import io.github.lethe_labs.argos.core.snapshot.DeviceSnapshot

/**
 * Reads the device state through public APIs only, no root. Each read is
 * isolated: a field that cannot be read comes back null or empty instead of
 * failing the whole snapshot.
 */
object SnapshotCollector {

    fun collect(context: Context): DeviceSnapshot {
        val cr = context.contentResolver
        val telephony = context.getSystemService(TelephonyManager::class.java)
        return DeviceSnapshot(
            takenAtWallMs = System.currentTimeMillis(),
            takenAtElapsedMs = SystemClock.elapsedRealtime(),
            bootCount = readGlobalInt(cr, Settings.Global.BOOT_COUNT),
            adbEnabled = readGlobalInt(cr, Settings.Global.ADB_ENABLED)?.let { it == 1 },
            developerOptionsEnabled = readGlobalInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)?.let { it == 1 },
            installedPackages = installedPackages(context),
            accessibilityServices = accessibilityServices(cr),
            deviceAdmins = deviceAdmins(context),
            simState = runCatching { telephony?.simState }.getOrNull(),
            simOperator = runCatching { telephony?.simOperator }.getOrNull()?.takeIf { it.isNotEmpty() },
            usageAccessGranted = UsageReader.hasAccess(context),
        )
    }

    fun readBootCount(context: Context): Int? = readGlobalInt(context.contentResolver, Settings.Global.BOOT_COUNT)

    private fun readGlobalInt(cr: ContentResolver, name: String): Int? =
        runCatching { Settings.Global.getInt(cr, name) }.getOrNull()

    @Suppress("DEPRECATION")
    private fun installedPackages(context: Context): Set<String> = runCatching {
        val pm = context.packageManager
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getInstalledPackages(0)
        }
        infos.map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    private fun accessibilityServices(cr: ContentResolver): Set<String> =
        runCatching { Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }
            .getOrNull()
            ?.split(':')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    private fun deviceAdmins(context: Context): Set<String> = runCatching {
        context.getSystemService(DevicePolicyManager::class.java)
            ?.activeAdmins
            .orEmpty()
            .map { it.flattenToShortString() }
            .toSet()
    }.getOrDefault(emptySet())
}
