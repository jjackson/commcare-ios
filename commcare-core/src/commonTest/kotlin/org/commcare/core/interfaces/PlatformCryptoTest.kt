package org.commcare.core.interfaces

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PlatformCryptoTest {

    @Test
    fun testSha256KnownValue() {
        val hash = PlatformCrypto.sha256("hello".encodeToByteArray())
        assertEquals(32, hash.size)
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            hash.toHexString()
        )
    }

    @Test
    fun testSha256EmptyInput() {
        val hash = PlatformCrypto.sha256(ByteArray(0))
        assertEquals(32, hash.size)
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hash.toHexString()
        )
    }

    @Test
    fun testMd5KnownValue() {
        val hash = PlatformCrypto.md5("hello".encodeToByteArray())
        assertEquals(16, hash.size)
        assertEquals(
            "5d41402abc4b2a76b9719d911017c592",
            hash.toHexString()
        )
    }

    @Test
    fun testMd5EmptyInput() {
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
        assertNotEquals(a.toList(), b.toList())
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
    fun testAesEncryptDecryptRoundTrip() {
        val key = PlatformCrypto.generateAesKey(256)
        val plaintext = "Hello, CommCare!".encodeToByteArray()
        val encrypted = PlatformCrypto.aesEncrypt(plaintext, key)
        val decrypted = PlatformCrypto.aesDecrypt(encrypted, key)
        assertEquals(plaintext.toList(), decrypted.toList())
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
        val plaintext = ByteArray(1024) { (it % 256).toByte() }
        val encrypted = PlatformCrypto.aesEncrypt(plaintext, key)
        val decrypted = PlatformCrypto.aesDecrypt(encrypted, key)
        assertEquals(plaintext.toList(), decrypted.toList())
    }

    @Test
    fun testAesDifferentEncryptionsProduceDifferentOutput() {
        val key = PlatformCrypto.generateAesKey(256)
        val plaintext = "same input".encodeToByteArray()
        val enc1 = PlatformCrypto.aesEncrypt(plaintext, key)
        val enc2 = PlatformCrypto.aesEncrypt(plaintext, key)
        // Different IVs produce different ciphertexts
        assertNotEquals(enc1.toList(), enc2.toList())
    }

    @Test
    fun testPbkdf2KnownOutputDeterministic() {
        val password = "mypassword"
        val salt = "mysalt".encodeToByteArray()
        val key1 = PlatformCrypto.pbkdf2(password, salt, 1000, 32)
        val key2 = PlatformCrypto.pbkdf2(password, salt, 1000, 32)
        assertEquals(32, key1.size)
        assertEquals(key1.toList(), key2.toList(), "PBKDF2 should be deterministic")
    }

    @Test
    fun testPbkdf2DifferentPasswordsDifferentKeys() {
        val salt = "mysalt".encodeToByteArray()
        val key1 = PlatformCrypto.pbkdf2("password1", salt, 1000, 32)
        val key2 = PlatformCrypto.pbkdf2("password2", salt, 1000, 32)
        assertNotEquals(key1.toList(), key2.toList())
    }

    @Test
    fun testPbkdf2DifferentSaltsDifferentKeys() {
        val password = "mypassword"
        val key1 = PlatformCrypto.pbkdf2(password, "salt1".encodeToByteArray(), 1000, 32)
        val key2 = PlatformCrypto.pbkdf2(password, "salt2".encodeToByteArray(), 1000, 32)
        assertNotEquals(key1.toList(), key2.toList())
    }

    @Test
    fun testPbkdf2VariousKeyLengths() {
        val password = "test"
        val salt = "salt".encodeToByteArray()
        assertEquals(16, PlatformCrypto.pbkdf2(password, salt, 1000, 16).size)
        assertEquals(32, PlatformCrypto.pbkdf2(password, salt, 1000, 32).size)
        assertEquals(64, PlatformCrypto.pbkdf2(password, salt, 1000, 64).size)
    }
}

private fun ByteArray.toHexString(): String =
    joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
