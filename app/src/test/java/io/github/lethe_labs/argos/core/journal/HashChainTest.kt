package io.github.lethe_labs.argos.core.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HashChainTest {

    private fun chainOf(vararg types: String): List<JournalEntry> =
        types.foldIndexed(emptyList()) { i, chain, type ->
            chain + HashChain.next(chain, 1_000L + i, type, "detail-$i")
        }

    @Test
    fun `a freshly built chain is intact`() {
        val chain = chainOf("BOOT", "DEPARTURE", "UNLOCK_FAILED", "RETURN")
        assertEquals(HashChain.Verification.Intact, HashChain.verify(chain))
    }

    @Test
    fun `an empty chain is intact`() {
        assertEquals(HashChain.Verification.Intact, HashChain.verify(emptyList()))
    }

    @Test
    fun `first entry links to the genesis value`() {
        assertEquals(HashChain.GENESIS, chainOf("BOOT").single().prevHash)
    }

    @Test
    fun `editing an entry is detected at that entry`() {
        val chain = chainOf("BOOT", "UNLOCK_FAILED", "RETURN").toMutableList()
        chain[1] = chain[1].copy(type = "UNLOCK_SUCCEEDED")
        assertEquals(HashChain.Verification.BrokenAt(1, "content"), HashChain.verify(chain))
    }

    @Test
    fun `deleting an entry in the middle is detected`() {
        val chain = chainOf("BOOT", "UNLOCK_FAILED", "RETURN").toMutableList()
        chain.removeAt(1)
        assertTrue(HashChain.verify(chain) is HashChain.Verification.BrokenAt)
    }

    @Test
    fun `swapping two entries is detected`() {
        val chain = chainOf("A", "B", "C").toMutableList()
        val tmp = chain[1]; chain[1] = chain[2]; chain[2] = tmp
        assertTrue(HashChain.verify(chain) is HashChain.Verification.BrokenAt)
    }

    @Test
    fun `recomputing a forged entry still breaks the next link`() {
        val chain = chainOf("BOOT", "UNLOCK_FAILED", "RETURN").toMutableList()
        val forged = HashChain.next(chain.subList(0, 1), chain[1].timestampMs, "UNLOCK_SUCCEEDED", chain[1].detail)
        chain[1] = forged
        assertEquals(HashChain.Verification.BrokenAt(2, "link"), HashChain.verify(chain))
    }

    @Test
    fun `field boundaries cannot be shifted to collide`() {
        val a = HashChain.hashOf(0, 1, "a|b", "c", HashChain.GENESIS)
        val b = HashChain.hashOf(0, 1, "a", "b|c", HashChain.GENESIS)
        assertNotEquals(a, b)
    }
}
