package io.github.lethe_labs.argos.core.report

import io.github.lethe_labs.argos.core.journal.HashChain
import io.github.lethe_labs.argos.core.journal.JournalEntry
import io.github.lethe_labs.argos.core.journal.JournalEventType
import io.github.lethe_labs.argos.core.journal.SimSlots
import io.github.lethe_labs.argos.core.snapshot.Anomaly
import io.github.lethe_labs.argos.core.snapshot.Anomaly.Kind
import io.github.lethe_labs.argos.core.snapshot.DeviceSnapshot
import io.github.lethe_labs.argos.core.snapshot.Severity
import io.github.lethe_labs.argos.core.snapshot.SnapshotDiff

object ReportBuilder {

    /**
     * @param departure the stored departure, or null if none was found.
     * @param sealKeyPresent whether the seal words could be recomputed at all.
     * @param departureConfirmed whether the user recognised the words and the
     *        departure time shown to them.
     * @param journal the journal, or null if it could not be read at all.
     */
    fun build(
        departure: Departure?,
        sealKeyPresent: Boolean,
        departureConfirmed: Boolean,
        now: DeviceSnapshot,
        journal: List<JournalEntry>?,
    ): IntegrityReport {
        val journalIntact = journal != null && HashChain.verify(journal) == HashChain.Verification.Intact

        if (departure == null) {
            // The user remembers leaving but the app has no departure: it was
            // reset or its data cleared while they were away.
            return IntegrityReport(SealStatus.NO_SESSION, journalIntact, false, emptyList())
        }

        val sealStatus = when {
            !sealKeyPresent -> SealStatus.KEY_MISSING
            !departureConfirmed -> SealStatus.REJECTED
            else -> SealStatus.MATCH
        }

        val anchor = journal?.getOrNull(departure.journalHeadSeq.toInt())
        val anchorFound = anchor != null && anchor.hash == departure.journalHeadHash

        val (eventsBefore, eventsWhileAway) = journal.orEmpty().partition { it.seq <= departure.journalHeadSeq }
        val simBaseline = eventsBefore.lastOrNull { it.type == JournalEventType.SIM_STATE }
        val anomalies = SnapshotDiff.compare(departure.snapshot, now) +
            journalAnomalies(eventsWhileAway) +
            simAnomalies(simBaseline, eventsWhileAway)

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

    /**
     * The snapshot diff only sees the SIM at departure and at return, so a card
     * pulled out and put back in between goes unnoticed. The journal records
     * each change as it happens; this replays it slot by slot from the last
     * state known before departure.
     */
    private fun simAnomalies(baseline: JournalEntry?, events: List<JournalEntry>): List<Anomaly> {
        val known = mutableMapOf<Int, String>()
        val out = mutableListOf<Anomaly>()
        fun replay(entry: JournalEntry, report: Boolean) {
            SimSlots.decode(entry.detail).forEachIndexed { slot, state ->
                if (state == SimSlots.UNKNOWN) return@forEachIndexed
                val previous = known.put(slot, state)
                if (!report) return@forEachIndexed
                val at = entry.timestampMs.toString()
                // A slot seen for the first time is no change: an empty second
                // slot is not a removed card.
                when {
                    previous == SimSlots.PRESENT && state == SimSlots.ABSENT ->
                        out += Anomaly(Kind.SIM_REMOVED, Severity.WARNING, at)
                    previous == SimSlots.ABSENT && state == SimSlots.PRESENT ->
                        out += Anomaly(Kind.SIM_INSERTED, Severity.WARNING, at)
                }
            }
        }
        baseline?.let { replay(it, report = false) }
        events.filter { it.type == JournalEventType.SIM_STATE }.forEach { replay(it, report = true) }
        return out
    }
}
