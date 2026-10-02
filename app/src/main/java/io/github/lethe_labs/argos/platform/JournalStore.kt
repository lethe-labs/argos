package io.github.lethe_labs.argos.platform

import android.content.Context
import io.github.lethe_labs.argos.core.journal.HashChain
import io.github.lethe_labs.argos.core.journal.JournalEntry
import io.github.lethe_labs.argos.core.journal.JournalEventType
import org.json.JSONObject
import java.io.File

/**
 * Persists the hash-chained journal, one JSON object per line.
 *
 * Lives in device-protected storage on purpose: the receivers must be able to
 * log failed unlocks and reboots before the first unlock after a restart,
 * while credential-encrypted storage is still locked -- the very window in
 * which a seized phone is attacked. The trade-off is that this file is
 * readable earlier than credential-encrypted data, so it only ever holds event
 * types and timestamps, never the snapshot.
 */
class JournalStore(context: Context) {

    private val file = File(context.createDeviceProtectedStorageContext().noBackupFilesDir, "journal.jsonl")
    private val lock = Any()

    /** Null when the file exists but cannot be parsed: that is itself a sign of tampering. */
    fun readAll(): List<JournalEntry>? = synchronized(lock) {
        if (!file.exists()) return emptyList()
        runCatching {
            file.readLines()
                .filter { it.isNotBlank() }
                .map { Json.entryFromJson(JSONObject(it)) }
        }.getOrNull()
    }

    /**
     * Returns null if the journal is unreadable. A corrupted chain is never
     * silently extended: the report will flag it as tampered instead.
     */
    fun append(type: String, detail: String = "", timestampMs: Long = System.currentTimeMillis()): JournalEntry? =
        synchronized(lock) {
            val chain = readAll() ?: return null
            val entry = HashChain.next(chain, timestampMs, type, detail)
            runCatching {
                file.parentFile?.mkdirs()
                file.appendText(Json.entryToJson(entry).toString() + "\n")
                entry
            }.getOrNull()
        }

    /**
     * BOOT_COMPLETED and LOCKED_BOOT_COMPLETED both fire on one boot; the boot
     * counter makes sure a given boot is only logged once.
     */
    fun appendBootOnce(bootCount: Int?): JournalEntry? = synchronized(lock) {
        val chain = readAll() ?: return null
        if (bootCount != null && chain.any { it.type == JournalEventType.BOOT && it.detail == bootCount.toString() }) {
            return null
        }
        append(JournalEventType.BOOT, bootCount?.toString() ?: "")
    }
}
