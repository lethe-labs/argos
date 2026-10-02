package io.github.lethe_labs.argos.core.seal

/**
 * The seal: three words shown at departure, which the user only has to
 * recognise on return. No writing down, no typing.
 *
 * The words are an HMAC of the departure record under a fresh Android Keystore
 * key that never leaves the device. They are recomputed on return, NEVER
 * stored: if they were stored, an intruder who resets Argos could start a fake
 * departure and write the user's words into it. Derived from a new random key,
 * a fake departure gets different words, which the user notices at a glance.
 *
 * Strength, stated plainly: three words from 256 give about 24 bits. An
 * intruder who knows the PIN can read the words on the return screen, but still
 * cannot make a fake departure produce them; they could only retry departures
 * until three random words match, roughly 16 million attempts on the device
 * itself. That is the scenario this guards against, and it is out of reach in
 * practice.
 *
 * The HMAC itself is computed by the caller (the key lives in the Keystore),
 * which keeps this object free of Android types and unit-testable.
 */
object SealWords {
    const val WORD_COUNT = 3

    fun record(
        language: String,
        sessionId: String,
        departedAtMs: Long,
        snapshotFingerprint: String,
        journalHeadSeq: Long,
        journalHeadHash: String,
    ): String =
        "argos-seal-v2|$language|$sessionId|$departedAtMs|$snapshotFingerprint|$journalHeadSeq|$journalHeadHash"

    fun derive(record: String, language: String, hmac: (ByteArray) -> ByteArray): List<String> {
        val words = WordLists.forLanguage(language)
        require(words.size == 256) { "word list must hold exactly 256 words" }

        // One byte per word; repeats are skipped so the three words are always
        // different, which makes them easier to tell apart.
        val picked = LinkedHashSet<Int>()
        for (b in hmac(record.toByteArray(Charsets.UTF_8))) {
            picked += b.toInt() and 0xff
            if (picked.size == WORD_COUNT) break
        }
        require(picked.size == WORD_COUNT) { "HMAC output too short" }
        return picked.map { words[it] }
    }
}
