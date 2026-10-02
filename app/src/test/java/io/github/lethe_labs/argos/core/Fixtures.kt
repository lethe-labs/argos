package io.github.lethe_labs.argos.core

import io.github.lethe_labs.argos.core.snapshot.DeviceSnapshot

internal fun deviceSnapshot(
    wall: Long = 1_000_000L,
    elapsed: Long = 50_000L,
    bootCount: Int? = 10,
    adb: Boolean? = false,
    devOptions: Boolean? = false,
    packages: Set<String> = setOf("com.android.settings", "org.example.mail"),
    accessibility: Set<String> = emptySet(),
    admins: Set<String> = setOf("io.github.lethe_labs.argos/.platform.ArgosAdminReceiver"),
    simState: Int? = 5,
    simOperator: String? = "20801",
) = DeviceSnapshot(
    takenAtWallMs = wall,
    takenAtElapsedMs = elapsed,
    bootCount = bootCount,
    adbEnabled = adb,
    developerOptionsEnabled = devOptions,
    installedPackages = packages,
    accessibilityServices = accessibility,
    deviceAdmins = admins,
    simState = simState,
    simOperator = simOperator,
)

/** Same instant later in the same boot: wall and monotonic clocks advance together. */
internal fun DeviceSnapshot.later(ms: Long) = copy(
    takenAtWallMs = takenAtWallMs + ms,
    takenAtElapsedMs = takenAtElapsedMs + ms,
)
