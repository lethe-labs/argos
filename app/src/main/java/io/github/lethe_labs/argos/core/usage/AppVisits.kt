package io.github.lethe_labs.argos.core.usage

/** One activity of [packageName] coming to the foreground or leaving it. */
data class AppEvent(val timestampMs: Long, val packageName: String, val foreground: Boolean)

/** A stretch of time during which [packageName] was on screen. */
data class AppVisit(val packageName: String, val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

object AppVisits {
    /**
     * Leaving an app for less than this and coming back counts as one visit,
     * so that a glance at a notification does not split it in two.
     */
    const val MERGE_GAP_MS: Long = 60_000L

    /**
     * Turns the system's foreground/background events into visits, oldest
     * first, within [fromMs]..[toMs].
     *
     * Several activities of one app can be resumed at once, so an app stays
     * on screen until all of them have left. An app already on screen at
     * [fromMs] has no foreground event in range and is skipped; one still on
     * screen at [toMs] is closed there.
     */
    fun from(events: List<AppEvent>, fromMs: Long, toMs: Long, ignored: Set<String>): List<AppVisit> {
        val open = mutableMapOf<String, Pair<Long, Int>>()
        val visits = mutableListOf<AppVisit>()
        events
            .filter { it.timestampMs in fromMs..toMs && it.packageName !in ignored }
            .sortedBy { it.timestampMs }
            .forEach { e ->
                val current = open[e.packageName]
                if (e.foreground) {
                    open[e.packageName] = (current?.first ?: e.timestampMs) to ((current?.second ?: 0) + 1)
                } else if (current != null) {
                    val remaining = current.second - 1
                    if (remaining > 0) {
                        open[e.packageName] = current.first to remaining
                    } else {
                        open.remove(e.packageName)
                        visits += AppVisit(e.packageName, current.first, e.timestampMs)
                    }
                }
            }
        open.forEach { (pkg, state) -> visits += AppVisit(pkg, state.first, toMs) }
        return merge(visits.sortedBy { it.startMs })
    }

    private fun merge(visits: List<AppVisit>): List<AppVisit> {
        val out = mutableListOf<AppVisit>()
        val lastIndexByPackage = mutableMapOf<String, Int>()
        visits.forEach { v ->
            val i = lastIndexByPackage[v.packageName]
            val previous = i?.let { out[it] }
            if (previous != null && v.startMs - previous.endMs <= MERGE_GAP_MS) {
                out[i] = previous.copy(endMs = maxOf(previous.endMs, v.endMs))
            } else {
                lastIndexByPackage[v.packageName] = out.size
                out += v
            }
        }
        return out
    }

    /** Anomaly detail for a visit, read back by the UI with [decode]. */
    fun encode(v: AppVisit): String = "${v.packageName}|${v.startMs}|${v.endMs}"

    fun decode(detail: String): AppVisit? {
        val parts = detail.split('|')
        if (parts.size != 3) return null
        val start = parts[1].toLongOrNull() ?: return null
        val end = parts[2].toLongOrNull() ?: return null
        return AppVisit(parts[0], start, end)
    }
}
