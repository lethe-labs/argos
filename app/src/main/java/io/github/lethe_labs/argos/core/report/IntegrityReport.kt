package io.github.lethe_labs.argos.core.report

import io.github.lethe_labs.argos.core.snapshot.Anomaly
import io.github.lethe_labs.argos.core.snapshot.Severity

enum class SealStatus {
    /** The user recognised their departure. */
    MATCH,

    /** The user did not recognise the words or the time: likely a fake departure. */
    REJECTED,

    /** The seal key is gone, so the words cannot be recomputed: the app was reset. */
    KEY_MISSING,

    /** No departure on record although the user had started one. */
    NO_SESSION,
}

data class IntegrityReport(
    val sealStatus: SealStatus,
    val journalIntact: Boolean,
    val departureAnchorFound: Boolean,
    val anomalies: List<Anomaly>,
) {
    enum class Verdict { CLEAN, SUSPICIOUS, TAMPERED }

    /**
     * TAMPERED outranks everything: if the evidence itself was altered, the
     * absence of anomalies proves nothing.
     */
    val verdict: Verdict
        get() = when {
            sealStatus != SealStatus.MATCH || !journalIntact || !departureAnchorFound -> Verdict.TAMPERED
            anomalies.any { it.severity != Severity.INFO } -> Verdict.SUSPICIOUS
            else -> Verdict.CLEAN
        }
}
