package io.github.lethe_labs.argos.core.snapshot

import io.github.lethe_labs.argos.core.Hashing

/**
 * State of the device at one instant. Nullable fields mean "could not be read
 * on this device", which is deliberately distinct from false/empty so that a
 * read failure is never mistaken for a change.
 */
data class DeviceSnapshot(
    val takenAtWallMs: Long,
    val takenAtElapsedMs: Long,
    val bootCount: Int?,
    val adbEnabled: Boolean?,
    val developerOptionsEnabled: Boolean?,
    val installedPackages: Set<String>,
    val accessibilityServices: Set<String>,
    val deviceAdmins: Set<String>,
    val simState: Int?,
    val simOperator: String?,
    /** Whether Argos may read the app usage history (null: unknown). */
    val usageAccessGranted: Boolean? = null,
) {
    /**
     * Stable digest bound into the seal, so that editing the stored departure
     * snapshot to hide a change is detected on return. Sets are sorted to make
     * the digest independent of collection order.
     */
    fun fingerprint(): String = Hashing.sha256Hex(
        listOf(
            "v1",
            takenAtWallMs.toString(),
            takenAtElapsedMs.toString(),
            bootCount.toString(),
            adbEnabled.toString(),
            developerOptionsEnabled.toString(),
            installedPackages.sorted().joinToString(","),
            accessibilityServices.sorted().joinToString(","),
            deviceAdmins.sorted().joinToString(","),
            simState.toString(),
            simOperator.toString(),
        // Appended only when known, so that a departure stored before this
        // field existed keeps its fingerprint, and therefore its seal words.
        ).plus(listOfNotNull(usageAccessGranted?.toString())).joinToString("\n"),
    )
}
