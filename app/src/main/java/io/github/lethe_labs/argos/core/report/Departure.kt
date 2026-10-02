package io.github.lethe_labs.argos.core.report

import io.github.lethe_labs.argos.core.seal.SealWords
import io.github.lethe_labs.argos.core.snapshot.DeviceSnapshot

/**
 * @param wordLanguage language of the seal words, fixed at departure so that
 *        switching the phone's language while away does not change the words
 *        shown on return.
 */
data class Departure(
    val sessionId: String,
    val departedAtMs: Long,
    val snapshot: DeviceSnapshot,
    val journalHeadSeq: Long,
    val journalHeadHash: String,
    val wordLanguage: String,
) {
    fun sealRecord(): String = SealWords.record(
        language = wordLanguage,
        sessionId = sessionId,
        departedAtMs = departedAtMs,
        snapshotFingerprint = snapshot.fingerprint(),
        journalHeadSeq = journalHeadSeq,
        journalHeadHash = journalHeadHash,
    )
}
