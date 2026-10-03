package io.github.lethe_labs.argos.platform

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
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

    /**
     * Copies a photo into Pictures/Argos, where the gallery shows it, so it
     * can be kept as evidence. No permission needed from Android 10. Once
     * there, other apps with gallery access can read it, and the journal hash
     * no longer vouches for it.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun exportToGallery(context: Context, name: String): Boolean = runCatching {
        val source = file(name) ?: return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "argos-$name")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Argos")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        try {
            val out = resolver.openOutputStream(uri) ?: error("no output stream")
            out.use { o -> source.inputStream().use { it.copyTo(o) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }.getOrDefault(false)

    /** Before Android 10: copies to a file the user picked, without storage permission. */
    fun exportTo(context: Context, name: String, target: Uri): Boolean = runCatching {
        val source = file(name) ?: return false
        val out = context.contentResolver.openOutputStream(target) ?: return false
        out.use { o -> source.inputStream().use { it.copyTo(o) } }
        true
    }.getOrDefault(false)

    /** A new absence starts with no photos from the previous one. */
    fun clear() {
        dir.listFiles().orEmpty().forEach { it.delete() }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
