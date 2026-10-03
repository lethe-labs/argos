package io.github.lethe_labs.argos.core.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVisitsTest {

    private val gallery = "com.example.gallery"
    private val contacts = "com.example.contacts"

    private fun fg(at: Long, pkg: String) = AppEvent(at, pkg, foreground = true)
    private fun bg(at: Long, pkg: String) = AppEvent(at, pkg, foreground = false)

    private fun visits(vararg events: AppEvent, from: Long = 0L, to: Long = 1_000_000L, ignored: Set<String> = emptySet()) =
        AppVisits.from(events.toList(), from, to, ignored)

    @Test
    fun `an app opened and closed is one visit`() {
        assertEquals(listOf(AppVisit(gallery, 100_000L, 220_000L)), visits(fg(100_000L, gallery), bg(220_000L, gallery)))
    }

    @Test
    fun `visits come out oldest first`() {
        val v = visits(fg(300_000L, contacts), bg(310_000L, contacts), fg(100_000L, gallery), bg(200_000L, gallery))
        assertEquals(listOf(gallery, contacts), v.map { it.packageName })
    }

    @Test
    fun `switching between activities of one app stays one visit`() {
        val v = visits(fg(100_000L, gallery), fg(150_000L, gallery), bg(151_000L, gallery), bg(200_000L, gallery))
        assertEquals(listOf(AppVisit(gallery, 100_000L, 200_000L)), v)
    }

    @Test
    fun `a short interruption is merged into the same visit`() {
        val v = visits(fg(100_000L, gallery), bg(200_000L, gallery), fg(230_000L, gallery), bg(300_000L, gallery))
        assertEquals(listOf(AppVisit(gallery, 100_000L, 300_000L)), v)
    }

    @Test
    fun `coming back much later is a new visit`() {
        val v = visits(fg(100_000L, gallery), bg(200_000L, gallery), fg(500_000L, gallery), bg(600_000L, gallery))
        assertEquals(2, v.size)
    }

    @Test
    fun `an app still open at return is closed at return`() {
        assertEquals(listOf(AppVisit(gallery, 100_000L, 400_000L)), visits(fg(100_000L, gallery), to = 400_000L))
    }

    @Test
    fun `an app already open at departure is not counted`() {
        assertTrue(visits(bg(100_000L, gallery), from = 50_000L).isEmpty())
    }

    @Test
    fun `events outside the absence are ignored`() {
        assertTrue(visits(fg(10L, gallery), bg(20L, gallery), from = 1_000L).isEmpty())
    }

    @Test
    fun `the launcher and Argos itself are ignored`() {
        assertTrue(visits(fg(100_000L, "launcher"), bg(200_000L, "launcher"), ignored = setOf("launcher")).isEmpty())
    }

    @Test
    fun `a visit survives encoding into an anomaly detail`() {
        val v = AppVisit(gallery, 100_000L, 220_000L)
        assertEquals(v, AppVisits.decode(AppVisits.encode(v)))
    }
}
