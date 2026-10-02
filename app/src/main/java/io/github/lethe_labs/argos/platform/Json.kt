package io.github.lethe_labs.argos.platform

import io.github.lethe_labs.argos.core.journal.JournalEntry
import io.github.lethe_labs.argos.core.report.Departure
import io.github.lethe_labs.argos.core.snapshot.DeviceSnapshot
import org.json.JSONArray
import org.json.JSONObject

internal object Json {

    fun entryToJson(e: JournalEntry): JSONObject = JSONObject()
        .put("seq", e.seq)
        .put("ts", e.timestampMs)
        .put("type", e.type)
        .put("detail", e.detail)
        .put("prev", e.prevHash)
        .put("hash", e.hash)

    fun entryFromJson(o: JSONObject): JournalEntry = JournalEntry(
        seq = o.getLong("seq"),
        timestampMs = o.getLong("ts"),
        type = o.getString("type"),
        detail = o.getString("detail"),
        prevHash = o.getString("prev"),
        hash = o.getString("hash"),
    )

    fun departureToJson(d: Departure): JSONObject = JSONObject()
        .put("sessionId", d.sessionId)
        .put("departedAtMs", d.departedAtMs)
        .put("journalHeadSeq", d.journalHeadSeq)
        .put("journalHeadHash", d.journalHeadHash)
        .put("snapshot", snapshotToJson(d.snapshot))

    fun departureFromJson(o: JSONObject): Departure = Departure(
        sessionId = o.getString("sessionId"),
        departedAtMs = o.getLong("departedAtMs"),
        snapshot = snapshotFromJson(o.getJSONObject("snapshot")),
        journalHeadSeq = o.getLong("journalHeadSeq"),
        journalHeadHash = o.getString("journalHeadHash"),
    )

    private fun snapshotToJson(s: DeviceSnapshot): JSONObject = JSONObject()
        .put("wall", s.takenAtWallMs)
        .put("elapsed", s.takenAtElapsedMs)
        .put("bootCount", s.bootCount ?: JSONObject.NULL)
        .put("adb", s.adbEnabled ?: JSONObject.NULL)
        .put("devOptions", s.developerOptionsEnabled ?: JSONObject.NULL)
        .put("packages", JSONArray(s.installedPackages.sorted()))
        .put("accessibility", JSONArray(s.accessibilityServices.sorted()))
        .put("admins", JSONArray(s.deviceAdmins.sorted()))
        .put("simState", s.simState ?: JSONObject.NULL)
        .put("simOperator", s.simOperator ?: JSONObject.NULL)

    private fun snapshotFromJson(o: JSONObject): DeviceSnapshot = DeviceSnapshot(
        takenAtWallMs = o.getLong("wall"),
        takenAtElapsedMs = o.getLong("elapsed"),
        bootCount = o.optIntOrNull("bootCount"),
        adbEnabled = o.optBooleanOrNull("adb"),
        developerOptionsEnabled = o.optBooleanOrNull("devOptions"),
        installedPackages = o.getJSONArray("packages").toStringSet(),
        accessibilityServices = o.getJSONArray("accessibility").toStringSet(),
        deviceAdmins = o.getJSONArray("admins").toStringSet(),
        simState = o.optIntOrNull("simState"),
        simOperator = if (o.isNull("simOperator")) null else o.getString("simOperator"),
    )

    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? = if (isNull(key)) null else getBoolean(key)

    private fun JSONArray.toStringSet(): Set<String> = (0 until length()).map { getString(it) }.toSet()
}
