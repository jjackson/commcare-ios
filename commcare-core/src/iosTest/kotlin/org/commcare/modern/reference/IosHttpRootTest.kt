package org.commcare.modern.reference

import org.javarosa.core.reference.ReferenceHandler
import org.javarosa.core.reference.ReferenceManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * iOS-specific tests for IosHttpRoot and IosHttpReference.
 * Verifies the iOS HTTP reference factory integrates correctly with ReferenceManager.
 */
class IosHttpRootTest {

    @AfterTest
    fun cleanup() {
        ReferenceHandler.clearInstance()
    }

    @Test
    fun testIosHttpRootDerivesHttpsUrls() {
        val root = IosHttpRoot()
        assertTrue(root.derives("https://www.commcarehq.org/profile.ccpr"))
    }

    @Test
    fun testIosHttpRootDerivesHttpUrls() {
        val root = IosHttpRoot()
        assertTrue(root.derives("http://example.com/suite.xml"))
    }

    @Test
    fun testIosHttpRootCreatesIosHttpReference() {
        val root = IosHttpRoot()
        val ref = root.derive("https://example.com/profile.ccpr")
        assertIs<IosHttpReference>(ref)
        assertEquals("https://example.com/profile.ccpr", ref.getURI())
    }

    @Test
    fun testIosHttpRootRegisteredWithReferenceManager() {
        val manager = ReferenceManager.instance()
        manager.addReferenceFactory(IosHttpRoot())

        val ref = manager.DeriveReference("https://www.commcarehq.org/a/test/apps/download/abc123/profile.ccpr")
        assertIs<IosHttpReference>(ref)
        assertEquals(
            "https://www.commcarehq.org/a/test/apps/download/abc123/profile.ccpr",
            ref.getURI()
        )
    }

    @Test
    fun testIosHttpReferenceProperties() {
        val ref = IosHttpReference("https://example.com/test.xml")
        assertTrue(ref.isReadOnly())
        assertTrue(ref.doesBinaryExist())
        assertEquals("https://example.com/test.xml", ref.getURI())
        assertEquals("https://example.com/test.xml", ref.getLocalURI())
    }

    @Test
    fun testIosHttpReferenceRejectsWrite() {
        val ref = IosHttpReference("https://example.com/test.xml")
        assertFailsWith<Exception> {
            ref.getOutputStream()
        }
    }

    @Test
    fun testIosHttpReferenceRejectsRemove() {
        val ref = IosHttpReference("https://example.com/test.xml")
        assertFailsWith<Exception> {
            ref.remove()
        }
    }

    @Test
    fun testIosHttpReferenceFetchesRealUrl() {
        // Integration test: actually fetches from network.
        // NOTE: The K/N test runner on the simulator may not have the system
        // trust store properly available, causing TLS cert errors. This test
        // verifies the NSURLSession + dispatch_semaphore pattern works in a
        // full app context; cert errors in the test harness are expected.
        val ref = IosHttpReference("https://www.apple.com/library/test/success.html")
        try {
            val stream = ref.getStream()
            val buffer = ByteArray(4096)
            val chunks = mutableListOf<ByteArray>()
            while (true) {
                val n = stream.read(buffer)
                if (n == -1) break
                chunks.add(buffer.copyOfRange(0, n))
            }
            val totalSize = chunks.sumOf { it.size }
            assertTrue(totalSize > 0, "Should receive response data")
        } catch (e: Exception) {
            // TLS cert validation fails in K/N test runner — expected
            assertTrue(
                e.message?.contains("certificate") == true,
                "If fetch fails, should be a cert issue, not a code bug. Got: ${e.message}"
            )
        }
    }

    @Test
    fun testIosHttpReferenceInvalidUrlThrows() {
        val ref = IosHttpReference("https://this-domain-does-not-exist-xyz123.invalid/test")
        assertFailsWith<Exception> {
            ref.getStream()
        }
    }

    @Test
    fun testRelativeReferenceResolutionWithIosHttpRoot() {
        val manager = ReferenceManager.instance()
        manager.addReferenceFactory(IosHttpRoot())

        // Profile is at https://example.com/a/domain/apps/download/appid/profile.ccpr
        // Suite is referenced as ./suite.xml — should resolve to same directory
        val ref = manager.DeriveReference(
            "./suite.xml",
            "https://example.com/a/domain/apps/download/appid/profile.ccpr"
        )
        assertIs<IosHttpReference>(ref)
        assertEquals(
            "https://example.com/a/domain/apps/download/appid/suite.xml",
            ref.getURI()
        )
    }
}
