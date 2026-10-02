package io.github.lethe_labs.argos.core.report

import io.github.lethe_labs.argos.core.deviceSnapshot
import io.github.lethe_labs.argos.core.journal.HashChain
import io.github.lethe_labs.argos.core.journal.JournalEntry
import io.github.lethe_labs.argos.core.journal.JournalEventType
import io.github.lethe_labs.argos.core.later
import io.github.lethe_labs.argos.core.seal.WordLists
import io.github.lethe_labs.argos.core.snapshot.Anomaly.Kind
import io.github.lethe_labs.argos.core.snapshot.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReportBuilderTest {

    private fun append(chain: List<JournalEntry>, type: String, at: Long) = chain + HashChain.next(chain, at, type, "")

    private val beforeSnapshot = deviceSnapshot()
    private val journalAtDeparture = append(append(emptyList(), JournalEventType.BOOT, 1L), JournalEventType.DEPARTURE, 2L)
    private val head = journalAtDeparture.last()
    private val departure = Departure("s1", head.timestampMs, beforeSnapshot, head.seq, head.hash, WordLists.FRENCH)
    private val nowSnapshot = beforeSnapshot.later(3_600_000L)

    private fun report(
        journal: List<JournalEntry>? = journalAtDeparture,
        keyPresent: Boolean = true,
        confirmed: Boolean = true,
        dep: Departure? = departure,
        now: io.github.lethe_labs.argos.core.snapshot.DeviceSnapshot = nowSnapshot,
    ) = ReportBuilder.build(dep, keyPresent, confirmed, now, journal)

    @Test
    fun `untouched phone confirmed by its owner is clean`() {
        val r = report()
        assertEquals(SealStatus.MATCH, r.sealStatus)
        assertEquals(IntegrityReport.Verdict.CLEAN, r.verdict)
    }

    @Test
    fun `a departure the user does not recognise means tampered`() {
        val r = report(confirmed = false)
        assertEquals(SealStatus.REJECTED, r.sealStatus)
        assertEquals(IntegrityReport.Verdict.TAMPERED, r.verdict)
    }

    @Test
    fun `a missing seal key means tampered, whatever the user answers`() {
        val r = report(keyPresent = false, confirmed = true)
        assertEquals(SealStatus.KEY_MISSING, r.sealStatus)
        assertEquals(IntegrityReport.Verdict.TAMPERED, r.verdict)
    }

    @Test
    fun `no departure on record while the user had started one means tampered`() {
        val r = report(dep = null)
        assertEquals(SealStatus.NO_SESSION, r.sealStatus)
        assertEquals(IntegrityReport.Verdict.TAMPERED, r.verdict)
    }

    @Test
    fun `wiped journal loses the departure anchor`() {
        val r = report(journal = emptyList())
        assertFalse(r.departureAnchorFound)
        assertEquals(IntegrityReport.Verdict.TAMPERED, r.verdict)
    }

    @Test
    fun `unreadable journal means tampered`() {
        assertEquals(IntegrityReport.Verdict.TAMPERED, report(journal = null).verdict)
    }

    @Test
    fun `altered journal means tampered`() {
        val altered = journalAtDeparture.toMutableList().apply { this[0] = this[0].copy(timestampMs = 99L) }
        assertEquals(IntegrityReport.Verdict.TAMPERED, report(journal = altered).verdict)
    }

    @Test
    fun `failed unlock while away is suspicious`() {
        val r = report(journal = append(journalAtDeparture, JournalEventType.UNLOCK_FAILED, 10L))
        assertEquals(IntegrityReport.Verdict.SUSPICIOUS, r.verdict)
        assertEquals(Kind.UNLOCK_FAILED, r.anomalies.single().kind)
    }

    @Test
    fun `the user's own unlock on return alone stays clean`() {
        val r = report(journal = append(journalAtDeparture, JournalEventType.UNLOCK_SUCCEEDED, 10L))
        assertEquals(Severity.INFO, r.anomalies.single().severity)
        assertEquals(IntegrityReport.Verdict.CLEAN, r.verdict)
    }

    @Test
    fun `an earlier unlock while away is suspicious`() {
        val journal = append(append(journalAtDeparture, JournalEventType.UNLOCK_SUCCEEDED, 10L), JournalEventType.UNLOCK_SUCCEEDED, 20L)
        val r = report(journal = journal)
        assertEquals(listOf(Severity.WARNING, Severity.INFO), r.anomalies.map { it.severity })
        assertEquals(IntegrityReport.Verdict.SUSPICIOUS, r.verdict)
    }

    @Test
    fun `events before departure are ignored`() {
        val withDeparture = append(append(emptyList(), JournalEventType.UNLOCK_FAILED, 1L), JournalEventType.DEPARTURE, 2L)
        val h = withDeparture.last()
        val dep = Departure("s2", h.timestampMs, beforeSnapshot, h.seq, h.hash, WordLists.FRENCH)
        assertEquals(IntegrityReport.Verdict.CLEAN, report(journal = withDeparture, dep = dep).verdict)
    }

    @Test
    fun `turning monitoring off is critical`() {
        val r = report(journal = append(journalAtDeparture, JournalEventType.ADMIN_DISABLED, 10L))
        assertEquals(Severity.CRITICAL, r.anomalies.single().severity)
    }

    @Test
    fun `anomalies are sorted most severe first`() {
        val r = report(
            journal = append(journalAtDeparture, JournalEventType.UNLOCK_FAILED, 10L),
            now = nowSnapshot.copy(adbEnabled = true),
        )
        assertEquals(Severity.CRITICAL, r.anomalies.first().severity)
    }

    @Test
    fun `the word language is bound into the seal record`() {
        assertEquals(false, departure.sealRecord() == departure.copy(wordLanguage = WordLists.ENGLISH).sealRecord())
    }
}
