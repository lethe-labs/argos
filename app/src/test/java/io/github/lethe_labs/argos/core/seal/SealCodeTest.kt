package io.github.lethe_labs.argos.core.seal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SealCodeTest {

    private fun hmacWith(key: String): (ByteArray) -> ByteArray = { data ->
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }.doFinal(data)
    }

    private val record = SealCode.record("session-1", 1_000L, "fingerprint", 3L, "headhash")

    @Test
    fun `code is two groups of four unambiguous characters`() {
        val code = SealCode.derive(record, hmacWith("k1"))
        assertTrue(code, Regex("^[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$").matches(code))
    }

    @Test
    fun `same record and key give the same code`() {
        assertEquals(SealCode.derive(record, hmacWith("k1")), SealCode.derive(record, hmacWith("k1")))
    }

    @Test
    fun `another key gives another code`() {
        assertNotEquals(SealCode.derive(record, hmacWith("k1")), SealCode.derive(record, hmacWith("k2")))
    }

    @Test
    fun `any change to the departure record changes the code`() {
        val edited = SealCode.record("session-1", 1_000L, "fingerprint-edited", 3L, "headhash")
        assertNotEquals(SealCode.derive(record, hmacWith("k1")), SealCode.derive(edited, hmacWith("k1")))
    }

    @Test
    fun `hand-copying slips are forgiven`() {
        assertTrue(SealCode.matches("0A1B-2C3D", "oa1b 2c3d"))
        assertTrue(SealCode.matches("1A1B-0000", "ia1b-oooo"))
        assertTrue(SealCode.matches("1A1B-0000", "LA1BOOOO"))
    }

    @Test
    fun `a wrong code does not match`() {
        assertFalse(SealCode.matches("0A1B-2C3D", "0A1B-2C3E"))
        assertFalse(SealCode.matches("0A1B-2C3D", "0A1B-2C3"))
        assertFalse(SealCode.matches("0A1B-2C3D", ""))
    }
}
