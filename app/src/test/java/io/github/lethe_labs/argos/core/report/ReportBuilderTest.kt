package io.github.lethe_labs.argos.core.report

import io.github.lethe_labs.argos.core.journal.HashChain
import io.github.lethe_labs.argos.core.journal.JournalEntry
import io.github.lethe_labs.argos.core.journal.JournalEventType
import io.github.lethe_labs.argos.core.later
import io.github.lethe_labs.argos.core.seal.SealCode
import io.github.lethe_labs.argos.core.deviceSnapshot
import io.github.lethe_labs.argos.core.snapshot.Anomaly.Kind
import io.github.lethe_labs.argos.core.snapshot.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ReportBuilderTest {

    private val hmac: (ByteArray) -> ByteArray = { data ->
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec("test-key".toByteArray(), "HmacSHA256")) }.doFinal(data)
    }

    private fun append(chain: List<JournalEntry>, type: String, at: Long) = chain + HashChain.next(chain, at, type, "")

    private val beforeSnapshot = deviceSnapshot()
    private val journalAtDeparture = append(append(emptyList(), JournalEventType.BOOT, 1L), JournalEventType.DEPARTURE, 2L)
    private val head = journalAtDeparture.last()
    private val departure = Departure("s1", head.timestampMs, beforeSnapshot, head.seq, head.hash)
    private val seal = SealCode.derive(departure.sealRecord(), hmac)
    private val nowSnapshot = beforeSnapshot.later(3_600_000L)

    private fun report(
        journal: List<JournalEntry>? = journalAtDeparture,
        typed: String = seal,
        expected: String? = seal,
        dep: Departure? = departure,
    ) = ReportBuilder.build(dep, typed, expected, nowSnapshot, journal)

    @Test
    fun `untouched phone is clean`() {
        assertEquals(IntegrityReport.Verdict.CLEAN, report().verdict)
    }

    @Test
    fun `wrong seal code means tampered`() {
        val r = report(typed = "0000-0000")
        assertEquals(SealStatus.MISMATCH, r.sealStatus)
        assertEquals(IntegrityReport.Verdict.TAMPERED, r.verdict)
    }

    @Test
    fun `missing seal key means tampered`() {
        assertEquals(IntegrityReport.Verdict.TAMPERED, report(expected = null).verdict)
    }

    @Test
    fun `no departure on record while the user holds a code means tampered`() {
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
        val journal = append(journalAtDeparture, JournalEventType.UNLOCK_FAILED, 10L)
        val r = report(journal = journal)
        assertEquals(IntegrityReport.Verdict.SUSPICIOUS, r.verdict)
        assertEquals(Kind.UNLOCK_FAILED, r.anomalies.single().kind)
    }

    @Test
    fun `the user's own unlock on return alone stays clean`() {
        val journal = append(journalAtDeparture, JournalEventType.UNLOCK_SUCCEEDED, 10L)
        val r = report(journal = journal)
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
        val before = append(emptyList(), JournalEventType.UNLOCK_FAILED, 1L)
        val withDeparture = append(before, JournalEventType.DEPARTURE, 2L)
        val h = withDeparture.last()
        val dep = Departure("s2", h.timestampMs, beforeSnapshot, h.seq, h.hash)
        val code = SealCode.derive(dep.sealRecord(), hmac)
        val r = ReportBuilder.build(dep, code, code, nowSnapshot, withDeparture)
        assertEquals(IntegrityReport.Verdict.CLEAN, r.verdict)
    }

    @Test
    fun `turning monitoring off is critical`() {
        val journal = append(journalAtDeparture, JournalEventType.ADMIN_DISABLED, 10L)
        assertEquals(Severity.CRITICAL, report(journal = journal).anomalies.single().severity)
    }

    @Test
    fun `anomalies are sorted most severe first`() {
        val journal = append(journalAtDeparture, JournalEventType.UNLOCK_FAILED, 10L)
        val r = ReportBuilder.build(departure, seal, seal, nowSnapshot.copy(adbEnabled = true), journal)
        assertEquals(Severity.CRITICAL, r.anomalies.first().severity)
    }
}
