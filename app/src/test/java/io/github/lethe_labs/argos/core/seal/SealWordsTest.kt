package io.github.lethe_labs.argos.core.seal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SealWordsTest {

    private fun hmacWith(key: String): (ByteArray) -> ByteArray = { data ->
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }.doFinal(data)
    }

    private fun record(language: String = WordLists.FRENCH, fingerprint: String = "fingerprint") =
        SealWords.record(language, "session-1", 1_000L, fingerprint, 3L, "headhash")

    @Test
    fun `each list holds exactly 256 distinct lowercase words`() {
        listOf(WordLists.EN, WordLists.FR).forEach { list ->
            assertEquals(256, list.size)
            assertEquals(256, list.toSet().size)
            assertTrue(list.all { it.isNotBlank() && it == it.lowercase() && ' ' !in it })
        }
    }

    @Test
    fun `three different words from the chosen language`() {
        val words = SealWords.derive(record(), WordLists.FRENCH, hmacWith("k1"))
        assertEquals(3, words.size)
        assertEquals(3, words.toSet().size)
        assertTrue(words.all { it in WordLists.FR })
    }

    @Test
    fun `same record and key always give the same words`() {
        assertEquals(
            SealWords.derive(record(), WordLists.FRENCH, hmacWith("k1")),
            SealWords.derive(record(), WordLists.FRENCH, hmacWith("k1")),
        )
    }

    @Test
    fun `a fake departure under a new key gets other words`() {
        assertNotEquals(
            SealWords.derive(record(), WordLists.FRENCH, hmacWith("original-key")),
            SealWords.derive(record(), WordLists.FRENCH, hmacWith("attacker-key")),
        )
    }

    @Test
    fun `editing the departure record changes the words`() {
        assertNotEquals(
            SealWords.derive(record(), WordLists.FRENCH, hmacWith("k1")),
            SealWords.derive(record(fingerprint = "edited"), WordLists.FRENCH, hmacWith("k1")),
        )
    }

    @Test
    fun `english departures use the english list`() {
        val words = SealWords.derive(record(WordLists.ENGLISH), WordLists.ENGLISH, hmacWith("k1"))
        assertTrue(words.all { it in WordLists.EN })
    }

    @Test
    fun `unknown device languages fall back to english`() {
        assertEquals(WordLists.FRENCH, WordLists.languageFor("fr"))
        assertEquals(WordLists.ENGLISH, WordLists.languageFor("de"))
        assertEquals(WordLists.ENGLISH, WordLists.languageFor(""))
    }

    @Test
    fun `repeated bytes never produce a repeated word`() {
        val repetitive: (ByteArray) -> ByteArray = { ByteArray(32) { i -> if (i < 5) 7 else (i * 13).toByte() } }
        val words = SealWords.derive(record(), WordLists.ENGLISH, repetitive)
        assertEquals(3, words.toSet().size)
    }
}
