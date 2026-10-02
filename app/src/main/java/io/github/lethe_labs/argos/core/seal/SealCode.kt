package io.github.lethe_labs.argos.core.seal

/**
 * The paper seal: a short code shown once at departure, which the user writes
 * down and keeps on them.
 *
 * It is an HMAC of the departure record under a fresh Android Keystore key
 * that never leaves the device. On return Argos recomputes it: if the app was
 * reset, reinstalled, or its departure record edited, the key or the record is
 * gone or different, and the code no longer matches. An intruder cannot forge
 * it: they have neither the key nor the paper.
 *
 * The HMAC itself is computed by the caller (the key lives in the Keystore),
 * which keeps this object free of Android types and unit-testable.
 */
object SealCode {
    // Crockford base32: no I, L, O or U, so a hand-copied code is hard to misread.
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    // 40 bits = 8 symbols. Offline forgery is out of reach without the key; 40
    // bits keeps the code short enough to copy by hand reliably.
    private const val CODE_BYTES = 5

    fun record(
        sessionId: String,
        departedAtMs: Long,
        snapshotFingerprint: String,
        journalHeadSeq: Long,
        journalHeadHash: String,
    ): String = "argos-seal-v1|$sessionId|$departedAtMs|$snapshotFingerprint|$journalHeadSeq|$journalHeadHash"

    fun derive(record: String, hmac: (ByteArray) -> ByteArray): String {
        val mac = hmac(record.toByteArray(Charsets.UTF_8))
        require(mac.size >= CODE_BYTES) { "HMAC output too short" }

        var bits = 0L
        for (i in 0 until CODE_BYTES) bits = (bits shl 8) or (mac[i].toLong() and 0xff)

        val code = StringBuilder()
        for (i in 7 downTo 0) code.append(ALPHABET[((bits shr (i * 5)) and 0x1f).toInt()])
        return code.insert(4, '-').toString()
    }

    /** Forgives what a human does when copying a code by hand. */
    fun normalize(input: String): String =
        input.uppercase()
            .filterNot { it == '-' || it.isWhitespace() }
            .map {
                when (it) {
                    'O' -> '0'
                    'I', 'L' -> '1'
                    else -> it
                }
            }
            .joinToString("")

    fun matches(expected: String, typed: String): Boolean {
        val a = normalize(expected)
        val b = normalize(typed)
        if (a.isEmpty() || a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
