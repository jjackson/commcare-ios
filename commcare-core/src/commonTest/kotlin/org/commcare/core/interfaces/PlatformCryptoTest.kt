package org.commcare.core.interfaces

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlatformCryptoTest {

    @Test
    fun testSha256KnownValue() {
        // SHA-256 of "hello" = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        val hash = PlatformCrypto.sha256("hello".encodeToByteArray())
        assertEquals(32, hash.size)
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            hash.toHexString()
        )
    }

    @Test
    fun testSha256EmptyInput() {
        // SHA-256 of "" = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val hash = PlatformCrypto.sha256(ByteArray(0))
        assertEquals(32, hash.size)
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hash.toHexString()
        )
    }

    @Test
    fun testMd5KnownValue() {
        // MD5 of "hello" = 5d41402abc4b2a76b9719d911017c592
        val hash = PlatformCrypto.md5("hello".encodeToByteArray())
        assertEquals(16, hash.size)
        assertEquals(
            "5d41402abc4b2a76b9719d911017c592",
            hash.toHexString()
        )
    }

    @Test
    fun testMd5EmptyInput() {
        // MD5 of "" = d41d8cd98f00b204e9800998ecf8427e
        val hash = PlatformCrypto.md5(ByteArray(0))
        assertEquals(16, hash.size)
        assertEquals(
            "d41d8cd98f00b204e9800998ecf8427e",
            hash.toHexString()
        )
    }

    @Test
    fun testRandomBytesLength() {
        val bytes16 = PlatformCrypto.randomBytes(16)
        assertEquals(16, bytes16.size)

        val bytes32 = PlatformCrypto.randomBytes(32)
        assertEquals(32, bytes32.size)

        val bytes0 = PlatformCrypto.randomBytes(0)
        assertEquals(0, bytes0.size)
    }

    @Test
    fun testRandomBytesAreRandom() {
        val a = PlatformCrypto.randomBytes(32)
        val b = PlatformCrypto.randomBytes(32)
        // Extremely unlikely to be equal
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun testAesEncryptDecryptRoundTrip() {
        val key = PlatformCrypto.generateAesKey(256)
        val plaintext = "Hello, CommCare!".encodeToByteArray()
        val encrypted = PlatformCrypto.aesEncrypt(plaintext, key)

        // Encrypted should be: 12 (IV) + plaintext.size + 16 (tag)
        assertEquals(12 + plaintext.size + 16, encrypted.size)

        val decrypted = PlatformCrypto.aesDecrypt(encrypted, key)
        assertEquals(plaintext.toList(), decrypted.toList())
    }

    @Test
    fun testAesEncryptDecryptLargeData() {
        val key = PlatformCrypto.generateAesKey(256)
        val plaintext = ByteArray(1024) { (it % 256).toByte() }
        val encrypted = PlatformCrypto.aesEncrypt(plaintext, key)
        val decrypted = PlatformCrypto.aesDecrypt(encrypted, key)
        assertEquals(plaintext.toList(), decrypted.toList())
    }

    @Test
    fun testAesKeyGeneration() {
        val key128 = PlatformCrypto.generateAesKey(128)
        assertEquals(16, key128.size)

        val key192 = PlatformCrypto.generateAesKey(192)
        assertEquals(24, key192.size)

        val key256 = PlatformCrypto.generateAesKey(256)
        assertEquals(32, key256.size)
    }

    @Test
    fun testAesDifferentEncryptionsProduceDifferentOutput() {
        val key = PlatformCrypto.generateAesKey(256)
        val plaintext = "same input".encodeToByteArray()
        val enc1 = PlatformCrypto.aesEncrypt(plaintext, key)
        val enc2 = PlatformCrypto.aesEncrypt(plaintext, key)
        // Different IVs should produce different ciphertexts
        assertNotEquals(enc1.toList(), enc2.toList())
    }
}

private fun ByteArray.toHexString(): String =
    joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
