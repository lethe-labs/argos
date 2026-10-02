package io.github.lethe_labs.argos.core.report

import io.github.lethe_labs.argos.core.snapshot.Anomaly
import io.github.lethe_labs.argos.core.snapshot.Severity

enum class SealStatus { MATCH, MISMATCH, NO_SESSION }

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
