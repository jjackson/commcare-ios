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
}

private fun ByteArray.toHexString(): String =
    joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
