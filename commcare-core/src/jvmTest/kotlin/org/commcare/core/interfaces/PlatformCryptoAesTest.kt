package org.commcare.core.interfaces

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * AES tests — JVM only. iOS AES-GCM requires CryptoKit Swift bridge (future wave).
 */
class PlatformCryptoAesTest {

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
    fun testAesDifferentEncryptionsProduceDifferentOutput() {
        val key = PlatformCrypto.generateAesKey(256)
        val plaintext = "same input".encodeToByteArray()
        val enc1 = PlatformCrypto.aesEncrypt(plaintext, key)
        val enc2 = PlatformCrypto.aesEncrypt(plaintext, key)
        // Different IVs should produce different ciphertexts
        assertNotEquals(enc1.toList(), enc2.toList())
    }
}
