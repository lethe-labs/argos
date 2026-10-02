package io.github.lethe_labs.argos.platform

import android.content.Context
import io.github.lethe_labs.argos.core.report.Departure
import org.json.JSONObject
import java.io.File

/**
 * Stores the current departure. Credential-encrypted storage on purpose: the
 * snapshot lists every installed app, which says a lot about the user, and is
 * only ever needed once the phone is unlocked.
 */
class SessionStore(context: Context) {

    private val file = File(context.noBackupFilesDir, "departure.json")

    fun load(): Departure? = runCatching {
        if (!file.exists()) null else Json.departureFromJson(JSONObject(file.readText()))
    }.getOrNull()

    fun save(departure: Departure) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(Json.departureToJson(departure).toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    fun clear() {
        file.delete()
    }
}
