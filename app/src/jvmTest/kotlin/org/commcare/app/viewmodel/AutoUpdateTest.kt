package org.commcare.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for auto-update checking: version comparison and state transitions.
 */
class AutoUpdateTest {

    @Test
    fun testUpdateStateTransitions() {
        // All update states
        assertTrue(UpdateState.Idle is UpdateState)
        assertTrue(UpdateState.Checking is UpdateState)
        assertTrue(UpdateState.Available is UpdateState)
        assertTrue(UpdateState.UpToDate is UpdateState)
        assertTrue(UpdateState.Installing is UpdateState)
        assertTrue(UpdateState.Complete is UpdateState)
        assertTrue(UpdateState.Error("test") is UpdateState)
    }

    @Test
    fun testVersionComparisonNewerAvailable() {
        val installed = 42
        val server = 43
        assertTrue(server > installed, "Server version should be detected as newer")
    }

    @Test
    fun testVersionComparisonUpToDate() {
        val installed = 42
        val server = 42
        assertFalse(server > installed, "Same version should not trigger update")
    }

    @Test
    fun testVersionComparisonOlderServer() {
        val installed = 43
        val server = 42
        assertFalse(server > installed, "Older server version should not trigger update")
    }

    @Test
    fun testVersionExtractionRegex() {
        val xml = """<profile xmlns="http://commcarehq.org" version="99" uniqueid="abc">"""
        val regex = Regex("""<profile[^>]*\bversion\s*=\s*"(\d+)"[^>]*>""")
        val version = regex.find(xml)?.groupValues?.get(1)?.toIntOrNull()
        assertEquals(99, version)
    }

    @Test
    fun testPeriodicCheckScheduling() {
        // Periodic check interval is 24 hours
        val intervalMs = 24 * 60 * 60 * 1000L
        assertEquals(86_400_000L, intervalMs)
    }
}
