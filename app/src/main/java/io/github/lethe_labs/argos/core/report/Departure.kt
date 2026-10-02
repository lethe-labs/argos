package io.github.lethe_labs.argos.core.report

import io.github.lethe_labs.argos.core.seal.SealCode
import io.github.lethe_labs.argos.core.snapshot.DeviceSnapshot

data class Departure(
    val sessionId: String,
    val departedAtMs: Long,
    val snapshot: DeviceSnapshot,
    val journalHeadSeq: Long,
    val journalHeadHash: String,
) {
    fun sealRecord(): String = SealCode.record(
        sessionId = sessionId,
        departedAtMs = departedAtMs,
        snapshotFingerprint = snapshot.fingerprint(),
        journalHeadSeq = journalHeadSeq,
        journalHeadHash = journalHeadHash,
    )
}
