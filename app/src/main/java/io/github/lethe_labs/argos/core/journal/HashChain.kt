package io.github.lethe_labs.argos.core.journal

import io.github.lethe_labs.argos.core.Hashing

/**
 * Append-only journal where every entry commits to the previous one, so that
 * editing, deleting or reordering an entry in the middle breaks the chain.
 *
 * What the chain alone does NOT catch: truncating the tail (dropping the most
 * recent entries) or deleting the whole file. That is why the departure entry
 * is anchored into the seal (see ReportBuilder): if it disappears, the report
 * says so. Entries appended after departure can still be truncated by an
 * attacker with root -- documented as a known limitation.
 */
object HashChain {
    val GENESIS: String = "0".repeat(64)

    sealed class Verification {
        object Intact : Verification()
        data class BrokenAt(val index: Int, val reason: String) : Verification()
    }

    fun next(chain: List<JournalEntry>, timestampMs: Long, type: String, detail: String): JournalEntry {
        val last = chain.lastOrNull()
        val seq = (last?.seq ?: -1L) + 1
        val prev = last?.hash ?: GENESIS
        return JournalEntry(seq, timestampMs, type, detail, prev, hashOf(seq, timestampMs, type, detail, prev))
    }

    fun verify(chain: List<JournalEntry>): Verification {
        var expectedPrev = GENESIS
        chain.forEachIndexed { i, e ->
            if (e.seq != i.toLong()) return Verification.BrokenAt(i, "sequence")
            if (e.prevHash != expectedPrev) return Verification.BrokenAt(i, "link")
            if (e.hash != hashOf(e.seq, e.timestampMs, e.type, e.detail, e.prevHash)) {
                return Verification.BrokenAt(i, "content")
            }
            expectedPrev = e.hash
        }
        return Verification.Intact
    }

    // Length-prefixed fields: without them, ("a|b", "c") and ("a", "b|c")
    // would feed the same bytes to the hash.
    internal fun hashOf(seq: Long, timestampMs: Long, type: String, detail: String, prevHash: String): String =
        Hashing.sha256Hex("$seq|$timestampMs|${type.length}:$type|${detail.length}:$detail|$prevHash")
}
