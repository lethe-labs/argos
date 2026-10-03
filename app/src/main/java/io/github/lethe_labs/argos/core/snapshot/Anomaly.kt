package io.github.lethe_labs.argos.core.snapshot

enum class Severity { INFO, WARNING, CRITICAL }

data class Anomaly(
    val kind: Kind,
    val severity: Severity,
    val detail: String = "",
) {
    enum class Kind {
        REBOOTED,
        CLOCK_CHANGED,
        ADB_ENABLED,
        DEVELOPER_OPTIONS_ENABLED,
        APP_INSTALLED,
        APP_REMOVED,
        ACCESSIBILITY_SERVICE_ADDED,
        DEVICE_ADMIN_ADDED,
        SIM_CHANGED,
        SIM_REMOVED,
        SIM_INSERTED,
        UNLOCK_FAILED,
        UNLOCK_SUCCEEDED,
        ADMIN_DISABLED,
        APP_OPENED,
        USAGE_ACCESS_REVOKED,
        USAGE_ACCESS_MISSING,
        SIM_PHOTO,
        SIM_PHOTO_FAILED,
        PHOTO_TAMPERED,
    }
}
