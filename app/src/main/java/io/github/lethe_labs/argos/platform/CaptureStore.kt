package io.github.lethe_labs.argos.platform

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * The front photos of the current absence. Credential-encrypted storage: they
 * are only taken while Argos watches, which is after the first unlock, and a
 * face has no business being readable earlier.
 */
class CaptureStore(context: Context) {

    class Stored(val name: String, val sha256: String)

    private val dir = File(context.noBackupFilesDir, "captures")

    fun save(jpeg: ByteArray, timestampMs: Long): Stored? = runCatching {
        dir.mkdirs()
        val file = File(dir, "sim-$timestampMs.jpg")
        file.writeBytes(jpeg)
        Stored(file.name, sha256(jpeg))
    }.getOrNull()

    fun file(name: String): File? = File(dir, name).takeIf { it.parentFile == dir && it.isFile }

    /** File name to SHA-256 of what is on disk now, for the report to check. */
    fun hashes(): Map<String, String> =
        dir.listFiles().orEmpty().filter { it.isFile }.associate { it.name to sha256(it.readBytes()) }

    /** A new absence starts with no photos from the previous one. */
    fun clear() {
        dir.listFiles().orEmpty().forEach { it.delete() }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
