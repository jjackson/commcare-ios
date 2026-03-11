package org.commcare.core.interfaces

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Cross-platform tests for PlatformCrypto.
 * These run on both JVM and iOS to verify crypto operations work correctly.
 */
class PlatformCryptoTest {

    @Test
    fun testSha256ProducesCorrectLength() {
        val hash = PlatformCrypto.sha256("hello".encodeToByteArray())
        assertEquals(32, hash.size)
    }

    @Test
    fun testSha256Deterministic() {
        val data = "CommCare test data".encodeToByteArray()
        val hash1 = PlatformCrypto.sha256(data)
        val hash2 = PlatformCrypto.sha256(data)
        assertEquals(hash1.toList(), hash2.toList())
    }

    @Test
    fun testSha256DifferentInputsDifferentOutputs() {
        val hash1 = PlatformCrypto.sha256("hello".encodeToByteArray())
        val hash2 = PlatformCrypto.sha256("world".encodeToByteArray())
        assertNotEquals(hash1.toList(), hash2.toList())
    }

    @Test
    fun testMd5ProducesCorrectLength() {
        val hash = PlatformCrypto.md5("hello".encodeToByteArray())
        assertEquals(16, hash.size)
    }

    @Test
    fun testMd5Deterministic() {
        val data = "CommCare test data".encodeToByteArray()
        val hash1 = PlatformCrypto.md5(data)
        val hash2 = PlatformCrypto.md5(data)
        assertEquals(hash1.toList(), hash2.toList())
    }

    @Test
    fun testRandomBytesCorrectLength() {
        val bytes16 = PlatformCrypto.randomBytes(16)
        assertEquals(16, bytes16.size)
        val bytes32 = PlatformCrypto.randomBytes(32)
        assertEquals(32, bytes32.size)
    }

    @Test
    fun testRandomBytesNotAllZeros() {
        val bytes = PlatformCrypto.randomBytes(32)
        // Extremely unlikely to be all zeros from a good RNG
        assertTrue(bytes.any { it != 0.toByte() })
    }

    @Test
    fun testGenerateAesKey256() {
        val key = PlatformCrypto.generateAesKey(256)
        assertEquals(32, key.size)
    }

    @Test
    fun testGenerateAesKey128() {
        val key = PlatformCrypto.generateAesKey(128)
        assertEquals(16, key.size)
    }

    @Test
    fun testAesEncryptDecryptRoundTrip() {
        val key = PlatformCrypto.generateAesKey(256)
        val plaintext = "Hello CommCare!".encodeToByteArray()

        val encrypted = PlatformCrypto.aesEncrypt(plaintext, key)
        val decrypted = PlatformCrypto.aesDecrypt(encrypted, key)

        assertEquals(plaintext.toList(), decrypted.toList())
    }

    @Test
    fun testAesEncryptProducesDifferentCiphertexts() {
        val key = PlatformCrypto.generateAesKey(256)
        val plaintext = "Hello CommCare!".encodeToByteArray()

        val encrypted1 = PlatformCrypto.aesEncrypt(plaintext, key)
        val encrypted2 = PlatformCrypto.aesEncrypt(plaintext, key)

        // Different IVs should produce different ciphertexts
        assertNotEquals(encrypted1.toList(), encrypted2.toList())
    }

    @Test
    fun testAesEncryptDecryptEmptyData() {
        val key = PlatformCrypto.generateAesKey(256)
        val plaintext = ByteArray(0)

        val encrypted = PlatformCrypto.aesEncrypt(plaintext, key)
        val decrypted = PlatformCrypto.aesDecrypt(encrypted, key)

        assertEquals(plaintext.toList(), decrypted.toList())
    }

    @Test
    fun testAesEncryptDecryptLargeData() {
        val key = PlatformCrypto.generateAesKey(256)
        val plaintext = ByteArray(10000) { (it % 256).toByte() }

        val encrypted = PlatformCrypto.aesEncrypt(plaintext, key)
        val decrypted = PlatformCrypto.aesDecrypt(encrypted, key)

        assertEquals(plaintext.toList(), decrypted.toList())
    }
}
