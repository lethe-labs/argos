package io.github.lethe_labs.argos.core.journal

/**
 * Detail of a [JournalEventType.SIM_STATE] entry: one coarse state per SIM
 * slot, comma-separated, slot 0 first. Only presence is kept, never anything
 * that identifies the card, since the journal is readable before first unlock.
 */
object SimSlots {
    const val PRESENT = "PRESENT"
    const val ABSENT = "ABSENT"

    /** Card still initialising or unreadable: carries no information. */
    const val UNKNOWN = "UNKNOWN"

    fun encode(states: List<String>): String = states.joinToString(",")

    fun decode(detail: String): List<String> = if (detail.isEmpty()) emptyList() else detail.split(',')
}
