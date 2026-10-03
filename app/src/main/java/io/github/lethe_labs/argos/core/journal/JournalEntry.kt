package io.github.lethe_labs.argos.core.journal

data class JournalEntry(
    val seq: Long,
    val timestampMs: Long,
    val type: String,
    val detail: String,
    val prevHash: String,
    val hash: String,
)

object JournalEventType {
    const val BOOT = "BOOT"
    const val UNLOCK_FAILED = "UNLOCK_FAILED"
    const val UNLOCK_SUCCEEDED = "UNLOCK_SUCCEEDED"
    const val ADMIN_ENABLED = "ADMIN_ENABLED"
    const val ADMIN_DISABLED = "ADMIN_DISABLED"
    const val DEPARTURE = "DEPARTURE"
    const val RETURN = "RETURN"
    const val SIM_STATE = "SIM_STATE"

    /** Detail: "<file name>|<sha256 of the JPEG>", so a swapped or deleted photo shows. */
    const val PHOTO_TAKEN = "PHOTO_TAKEN"
    const val PHOTO_FAILED = "PHOTO_FAILED"
}
