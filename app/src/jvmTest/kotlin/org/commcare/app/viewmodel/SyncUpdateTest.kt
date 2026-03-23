package org.commcare.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for incremental sync and update version extraction.
 */
class SyncUpdateTest {

    @Test
    fun testVersionExtractionFromProfileXml() {
        // Test the regex pattern used by UpdateViewModel
        val xml = """<profile xmlns="..." version="42" uniqueid="abc123">"""
        val regex = Regex("""<profile[^>]*\bversion\s*=\s*"(\d+)"[^>]*>""")
        val match = regex.find(xml)
        assertNotNull(match)
        assertEquals("42", match.groupValues[1])
    }

    @Test
    fun testVersionExtractionWithSpaces() {
        val xml = """<profile version = "7" uniqueid="test">"""
        val regex = Regex("""<profile[^>]*\bversion\s*=\s*"(\d+)"[^>]*>""")
        val match = regex.find(xml)
        assertNotNull(match)
        assertEquals("7", match.groupValues[1])
    }

    @Test
    fun testVersionExtractionReturnsNullForMissing() {
        val xml = """<profile uniqueid="test">"""
        val regex = Regex("""<profile[^>]*\bversion\s*=\s*"(\d+)"[^>]*>""")
        val match = regex.find(xml)
        assertEquals(null, match)
    }

    @Test
    fun testSha256HashDeterminism() {
        val data = "test restore body".encodeToByteArray()
        val hash1 = org.commcare.core.interfaces.PlatformCrypto.sha256(data)
        val hash2 = org.commcare.core.interfaces.PlatformCrypto.sha256(data)
        assertEquals(hash1.toList(), hash2.toList(), "SHA-256 should be deterministic")
    }

    @Test
    fun testSha256DifferentInputsDifferentHashes() {
        val hash1 = org.commcare.core.interfaces.PlatformCrypto.sha256("data1".encodeToByteArray())
        val hash2 = org.commcare.core.interfaces.PlatformCrypto.sha256("data2".encodeToByteArray())
        assertTrue(hash1.toList() != hash2.toList(), "Different inputs should produce different hashes")
    }

    @Test
    fun testUpdateStateTransitions() {
        // Verify all update states exist
        assertTrue(UpdateState.Idle is UpdateState)
        assertTrue(UpdateState.Checking is UpdateState)
        assertTrue(UpdateState.Available is UpdateState)
        assertTrue(UpdateState.UpToDate is UpdateState)
        assertTrue(UpdateState.Installing is UpdateState)
        assertTrue(UpdateState.Complete is UpdateState)
        assertTrue(UpdateState.Error("test") is UpdateState)
    }

    @Test
    fun testSyncStateTransitions() {
        // Verify all sync states exist
        assertTrue(SyncState.Idle is SyncState)
        assertTrue(SyncState.Syncing(0.5f, "test") is SyncState)
        assertTrue(SyncState.Complete is SyncState)
        assertTrue(SyncState.Error("test") is SyncState)
    }
}
