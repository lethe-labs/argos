package io.github.lethe_labs.argos.core.report

import io.github.lethe_labs.argos.core.deviceSnapshot
import io.github.lethe_labs.argos.core.journal.HashChain
import io.github.lethe_labs.argos.core.journal.JournalEntry
import io.github.lethe_labs.argos.core.journal.JournalEventType
import io.github.lethe_labs.argos.core.journal.SimSlots
import io.github.lethe_labs.argos.core.later
import io.github.lethe_labs.argos.core.seal.WordLists
import io.github.lethe_labs.argos.core.snapshot.Anomaly.Kind
import io.github.lethe_labs.argos.core.snapshot.Severity
import io.github.lethe_labs.argos.core.usage.AppVisit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReportBuilderTest {

    private fun append(chain: List<JournalEntry>, type: String, at: Long, detail: String = "") =
        chain + HashChain.next(chain, at, type, detail)

    private fun sim(chain: List<JournalEntry>, at: Long, vararg slots: String) =
        append(chain, JournalEventType.SIM_STATE, at, SimSlots.encode(slots.toList()))

    private fun departWith(journal: List<JournalEntry>): Pair<List<JournalEntry>, Departure> {
        val withDeparture = append(journal, JournalEventType.DEPARTURE, 2L)
        val h = withDeparture.last()
        return withDeparture to Departure("s3", h.timestampMs, beforeSnapshot, h.seq, h.hash, WordLists.FRENCH)
    }

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
        visits: List<AppVisit>? = emptyList(),
    ) = ReportBuilder.build(dep, keyPresent, confirmed, now, journal, visits)

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

    @Test
    fun `a sim pulled out and put back while away is reported twice`() {
        val (atDeparture, dep) = departWith(sim(emptyList(), 1L, SimSlots.PRESENT))
        val journal = sim(sim(atDeparture, 10L, SimSlots.ABSENT), 20L, SimSlots.PRESENT)
        val r = report(journal = journal, dep = dep)
        assertEquals(listOf(Kind.SIM_REMOVED, Kind.SIM_INSERTED), r.anomalies.map { it.kind })
        assertEquals("10", r.anomalies.first().detail)
        assertEquals(IntegrityReport.Verdict.SUSPICIOUS, r.verdict)
    }

    @Test
    fun `an empty second slot is not a removed card`() {
        val (atDeparture, dep) = departWith(sim(emptyList(), 1L, SimSlots.PRESENT, SimSlots.ABSENT))
        val journal = sim(atDeparture, 10L, SimSlots.PRESENT, SimSlots.ABSENT)
        assertEquals(IntegrityReport.Verdict.CLEAN, report(journal = journal, dep = dep).verdict)
    }

    @Test
    fun `a card still initialising says nothing about removal`() {
        val (atDeparture, dep) = departWith(sim(emptyList(), 1L, SimSlots.PRESENT))
        val journal = sim(sim(atDeparture, 10L, SimSlots.UNKNOWN), 20L, SimSlots.PRESENT)
        assertEquals(IntegrityReport.Verdict.CLEAN, report(journal = journal, dep = dep).verdict)
    }

    @Test
    fun `a removal is caught even without a baseline before departure`() {
        val journal = sim(sim(journalAtDeparture, 10L, SimSlots.PRESENT), 20L, SimSlots.ABSENT)
        assertEquals(Kind.SIM_REMOVED, report(journal = journal).anomalies.single().kind)
    }

    @Test
    fun `an app opened while away is suspicious`() {
        val r = report(visits = listOf(AppVisit("com.example.gallery", 100L, 200L)))
        assertEquals(Kind.APP_OPENED, r.anomalies.single().kind)
        assertEquals(IntegrityReport.Verdict.SUSPICIOUS, r.verdict)
    }

    @Test
    fun `apps opened after the last unlock are probably the user's own`() {
        val journal = append(append(journalAtDeparture, JournalEventType.UNLOCK_SUCCEEDED, 50L), JournalEventType.UNLOCK_SUCCEEDED, 500L)
        val r = report(
            journal = journal,
            visits = listOf(AppVisit("com.example.gallery", 100L, 200L), AppVisit("com.example.mail", 600L, 700L)),
        )
        val severities = r.anomalies.filter { it.kind == Kind.APP_OPENED }.associate { it.detail.substringBefore('|') to it.severity }
        assertEquals(Severity.WARNING, severities["com.example.gallery"])
        assertEquals(Severity.INFO, severities["com.example.mail"])
    }

    @Test
    fun `an unreadable usage history is noted without raising the verdict`() {
        val r = report(dep = departure.copy(snapshot = beforeSnapshot.copy(usageAccessGranted = false)), visits = null)
        assertEquals(Kind.USAGE_ACCESS_MISSING, r.anomalies.single().kind)
        assertEquals(IntegrityReport.Verdict.CLEAN, r.verdict)
    }

    @Test
    fun `usage access turned off while away is critical`() {
        val r = report(now = nowSnapshot.copy(usageAccessGranted = false), visits = null)
        assertEquals(Kind.USAGE_ACCESS_REVOKED, r.anomalies.single().kind)
        assertEquals(Severity.CRITICAL, r.anomalies.single().severity)
    }
}
