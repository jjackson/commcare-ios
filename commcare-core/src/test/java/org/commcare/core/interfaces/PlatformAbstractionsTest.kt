package org.commcare.core.interfaces

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for platform abstraction layer (Wave 4).
 */
class PlatformAbstractionsTest {

    // --- URL Tests ---

    @Test
    fun testUrlParsing() {
        val url = PlatformUrl("https://www.example.com:8080/path/to/resource?key=value")
        assertEquals("https", url.scheme)
        assertEquals("www.example.com", url.host)
        assertEquals(8080, url.port)
        assertEquals("/path/to/resource", url.path)
        assertEquals("key=value", url.query)
    }

    @Test
    fun testUrlParsingNoPort() {
        val url = PlatformUrl("http://example.com/path")
        assertEquals("http", url.scheme)
        assertEquals("example.com", url.host)
        assertEquals(-1, url.port)
        assertEquals("/path", url.path)
        assertNull(url.query)
    }

    @Test
    fun testUrlToString() {
        val original = "https://commcare.example.com/api/v1/cases"
        val url = PlatformUrl(original)
        assertEquals(original, url.toString())
    }

    @Test
    fun testIsValidUrl() {
        assertTrue(isValidUrl("https://example.com"))
        assertTrue(isValidUrl("http://localhost:8080/path?q=1"))
        assertFalse(isValidUrl("not a url"))
        assertFalse(isValidUrl(""))
    }

    // --- Crypto Tests ---

    @Test
    fun testSha256() {
        val hash = PlatformCrypto.sha256("hello".toByteArray())
        assertNotNull(hash)
        assertEquals(32, hash.size) // SHA-256 is 32 bytes
    }

    @Test
    fun testSha256Deterministic() {
        val hash1 = PlatformCrypto.sha256("test".toByteArray())
        val hash2 = PlatformCrypto.sha256("test".toByteArray())
        assertArrayEquals(hash1, hash2)
    }

    @Test
    fun testMd5() {
        val hash = PlatformCrypto.md5("hello".toByteArray())
        assertNotNull(hash)
        assertEquals(16, hash.size) // MD5 is 16 bytes
    }

    @Test
    fun testRandomBytes() {
        val bytes = PlatformCrypto.randomBytes(32)
        assertNotNull(bytes)
        assertEquals(32, bytes.size)
    }

    @Test
    fun testAesRoundTrip() {
        val key = PlatformCrypto.generateAesKey(256)
        val plaintext = "Hello, CommCare!".toByteArray()
        val encrypted = PlatformCrypto.aesEncrypt(plaintext, key)
        val decrypted = PlatformCrypto.aesDecrypt(encrypted, key)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testGenerateAesKey() {
        val key128 = PlatformCrypto.generateAesKey(128)
        assertEquals(16, key128.size)
        val key256 = PlatformCrypto.generateAesKey(256)
        assertEquals(32, key256.size)
    }

    // --- File I/O Tests ---

    @Test
    @Throws(Exception::class)
    fun testFileRoundTrip() {
        val path = PlatformFiles.createTempFile("test", ".dat")
        try {
            val data = "test file content".toByteArray()
            PlatformFiles.writeBytes(path, data)
            assertTrue(PlatformFiles.exists(path))
            val read = PlatformFiles.readBytes(path)
            assertArrayEquals(data, read)
            assertEquals(data.size.toLong(), PlatformFiles.fileSize(path))
            assertFalse(PlatformFiles.isDirectory(path))
        } finally {
            PlatformFiles.delete(path)
            assertFalse(PlatformFiles.exists(path))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testListDir() {
        val tempFile = PlatformFiles.createTempFile("listdir", ".tmp")
        try {
            val f = File(tempFile)
            val dir = f.parent
            assertTrue(PlatformFiles.isDirectory(dir))
            assertTrue(PlatformFiles.listDir(dir).isNotEmpty())
        } finally {
            PlatformFiles.delete(tempFile)
        }
    }
}
