package io.github.lethe_labs.argos.core.report

import io.github.lethe_labs.argos.core.journal.HashChain
import io.github.lethe_labs.argos.core.journal.JournalEntry
import io.github.lethe_labs.argos.core.journal.JournalEventType
import io.github.lethe_labs.argos.core.seal.SealCode
import io.github.lethe_labs.argos.core.snapshot.Anomaly
import io.github.lethe_labs.argos.core.snapshot.Anomaly.Kind
import io.github.lethe_labs.argos.core.snapshot.DeviceSnapshot
import io.github.lethe_labs.argos.core.snapshot.Severity
import io.github.lethe_labs.argos.core.snapshot.SnapshotDiff

object ReportBuilder {

    /**
     * @param departure the stored departure, or null if none was found.
     * @param typedSeal the code the user copied on paper.
     * @param expectedSeal the code recomputed from the stored record and the
     *        Keystore key, or null if the key is gone.
     * @param journal the journal, or null if it could not be read at all.
     */
    fun build(
        departure: Departure?,
        typedSeal: String,
        expectedSeal: String?,
        now: DeviceSnapshot,
        journal: List<JournalEntry>?,
    ): IntegrityReport {
        val journalIntact = journal != null && HashChain.verify(journal) == HashChain.Verification.Intact

        if (departure == null) {
            // The user holds a paper code but the app has no departure: the app
            // was reset or its data cleared while they were away.
            return IntegrityReport(SealStatus.NO_SESSION, journalIntact, false, emptyList())
        }

        val sealStatus = if (expectedSeal != null && SealCode.matches(expectedSeal, typedSeal)) {
            SealStatus.MATCH
        } else {
            SealStatus.MISMATCH
        }

        val anchor = journal?.getOrNull(departure.journalHeadSeq.toInt())
        val anchorFound = anchor != null && anchor.hash == departure.journalHeadHash

        val eventsWhileAway = journal.orEmpty().filter { it.seq > departure.journalHeadSeq }
        val anomalies = SnapshotDiff.compare(departure.snapshot, now) + journalAnomalies(eventsWhileAway)

        return IntegrityReport(
            sealStatus = sealStatus,
            journalIntact = journalIntact,
            departureAnchorFound = anchorFound,
            anomalies = anomalies.sortedByDescending { it.severity },
        )
    }

    private fun journalAnomalies(events: List<JournalEntry>): List<Anomaly> {
        // The user has to unlock the phone to open Argos and tap "I'm back",
        // so the most recent successful unlock is almost certainly theirs. It is
        // still listed, as INFO, so they can check the time.
        val lastSuccessSeq = events.lastOrNull { it.type == JournalEventType.UNLOCK_SUCCEEDED }?.seq

        return events.mapNotNull { e ->
            val at = e.timestampMs.toString()
            when (e.type) {
                JournalEventType.UNLOCK_FAILED -> Anomaly(Kind.UNLOCK_FAILED, Severity.WARNING, at)
                JournalEventType.UNLOCK_SUCCEEDED -> Anomaly(
                    Kind.UNLOCK_SUCCEEDED,
                    if (e.seq == lastSuccessSeq) Severity.INFO else Severity.WARNING,
                    at,
                )
                // Once monitoring is off, nothing further is logged.
                JournalEventType.ADMIN_DISABLED -> Anomaly(Kind.ADMIN_DISABLED, Severity.CRITICAL, at)
                // Reboots are already reported from the boot counter.
                else -> null
            }
        }
    }
}
