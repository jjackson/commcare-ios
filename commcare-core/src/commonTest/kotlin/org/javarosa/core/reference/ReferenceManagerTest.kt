package org.javarosa.core.reference

import org.javarosa.core.io.PlatformInputStream
import org.javarosa.core.io.PlatformOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for ReferenceManager factory registration and URI derivation.
 * These run on both JVM and iOS to verify the reference resolution pipeline.
 */
class ReferenceManagerTest {

    @BeforeTest
    fun setup() {
        // Clear any state leaked from other test suites (e.g., jvmTest MockApp
        // registers JavaHttpRoot on the shared ReferenceHandler singleton).
        ReferenceHandler.clearInstance()
    }

    @AfterTest
    fun cleanup() {
        ReferenceHandler.clearInstance()
    }

    @Test
    fun testDeriveReferenceWithNoFactoriesThrows() {
        val manager = ReferenceManager.instance()
        assertFailsWith<InvalidReferenceException> {
            manager.DeriveReference("https://example.com/profile.ccpr")
        }
    }

    @Test
    fun testRegisterHttpFactoryAndDeriveHttpsReference() {
        val manager = ReferenceManager.instance()
        val factory = StubHttpRootFactory()
        manager.addReferenceFactory(factory)

        val ref = manager.DeriveReference("https://example.com/profile.ccpr")
        assertIs<StubReference>(ref)
        assertEquals("https://example.com/profile.ccpr", ref.getURI())
    }

    @Test
    fun testRegisterHttpFactoryAndDeriveHttpReference() {
        val manager = ReferenceManager.instance()
        val factory = StubHttpRootFactory()
        manager.addReferenceFactory(factory)

        val ref = manager.DeriveReference("http://example.com/suite.xml")
        assertIs<StubReference>(ref)
        assertEquals("http://example.com/suite.xml", ref.getURI())
    }

    @Test
    fun testDeriveReferenceForUnregisteredSchemeThrows() {
        val manager = ReferenceManager.instance()
        manager.addReferenceFactory(StubHttpRootFactory())

        assertFailsWith<InvalidReferenceException> {
            manager.DeriveReference("jr://file/myform.xml")
        }
    }

    @Test
    fun testRelativeReferenceResolution() {
        val manager = ReferenceManager.instance()
        manager.addReferenceFactory(StubHttpRootFactory())

        // Relative reference with context
        val ref = manager.DeriveReference(
            "./suite.xml",
            "https://example.com/a/domain/apps/download/appid/profile.ccpr"
        )
        assertIs<StubReference>(ref)
        assertEquals(
            "https://example.com/a/domain/apps/download/appid/suite.xml",
            ref.getURI()
        )
    }

    @Test
    fun testFactoryNotDuplicated() {
        val manager = ReferenceManager.instance()
        val factory = StubHttpRootFactory()
        manager.addReferenceFactory(factory)
        manager.addReferenceFactory(factory) // add again

        // Should still work fine — no duplicate error
        val ref = manager.DeriveReference("https://example.com/test")
        assertIs<StubReference>(ref)
    }
}

/**
 * Stub HTTP root factory for testing ReferenceManager without network.
 * Mirrors the structure of IosHttpRoot/JavaHttpRoot.
 */
private class StubHttpRootFactory : PrefixedRootFactory(arrayOf("http://", "https://")) {
    override fun factory(terminal: String, URI: String): Reference {
        return StubReference(URI)
    }
}

/**
 * Stub reference that records the URI without doing I/O.
 */
private class StubReference(private val uri: String) : Reference {
    override fun doesBinaryExist(): Boolean = true
    override fun getStream(): PlatformInputStream = throw UnsupportedOperationException("stub")
    override fun getOutputStream(): PlatformOutputStream = throw UnsupportedOperationException("stub")
    override fun getURI(): String = uri
    override fun isReadOnly(): Boolean = true
    override fun remove() {}
    override fun getLocalURI(): String = uri
}
