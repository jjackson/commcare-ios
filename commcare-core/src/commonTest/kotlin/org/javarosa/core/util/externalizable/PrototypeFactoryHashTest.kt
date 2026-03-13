package org.javarosa.core.util.externalizable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cross-platform test verifying PrototypeFactory hash computation
 * produces identical results on JVM and iOS.
 */
class PrototypeFactoryHashTest {

    @Test
    fun hashSizeIs32() {
        assertEquals(32, PrototypeFactory.getClassHashSize())
    }

    @Test
    fun hashByNameProducesExpectedBytes() {
        val className = "org.javarosa.core.model.data.UncastData"
        val hash = PrototypeFactory.getClassHashByName(className)

        assertEquals(32, hash.size)

        // Hash should be first 32 bytes of reversed class name encoded as UTF-8
        val reversed = StringBuilder(className).reverse().toString()
        val expectedBytes = reversed.encodeToByteArray()
        for (i in 0 until minOf(32, expectedBytes.size)) {
            assertEquals(expectedBytes[i], hash[i], "Byte mismatch at index $i")
        }
        // Remaining bytes should be zero-padded
        for (i in expectedBytes.size until 32) {
            assertEquals(0, hash[i].toInt(), "Expected zero padding at index $i")
        }
    }

    @Test
    fun hashByNameDeterministic() {
        val className = "org.javarosa.core.model.data.UncastData"
        val hash1 = PrototypeFactory.getClassHashByName(className)
        val hash2 = PrototypeFactory.getClassHashByName(className)
        assertTrue(PrototypeFactory.compareHash(hash1, hash2))
    }

    @Test
    fun differentClassesDifferentHashes() {
        val hash1 = PrototypeFactory.getClassHashByName("org.javarosa.core.model.data.UncastData")
        val hash2 = PrototypeFactory.getClassHashByName("org.javarosa.core.model.data.StringData")
        assertFalse(PrototypeFactory.compareHash(hash1, hash2))
    }

    @Test
    fun compareHashWorks() {
        val a = ByteArray(32) { it.toByte() }
        val b = ByteArray(32) { it.toByte() }
        val c = ByteArray(32) { (it + 1).toByte() }

        assertTrue(PrototypeFactory.compareHash(a, b))
        assertFalse(PrototypeFactory.compareHash(a, c))
    }

    @Test
    fun compareHashDifferentSizeReturnsFalse() {
        val a = ByteArray(32)
        val b = ByteArray(16)
        assertFalse(PrototypeFactory.compareHash(a, b))
    }

    @Test
    fun wrapperTagIsAllFF() {
        val tag = PrototypeFactory.getWrapperTag()
        assertEquals(32, tag.size)
        for (i in tag.indices) {
            assertEquals(0xff.toByte(), tag[i], "Wrapper tag byte $i should be 0xFF")
        }
    }

    @Test
    fun wrapperTagNotEqualToAnyClassHash() {
        val tag = PrototypeFactory.getWrapperTag()
        val hash = PrototypeFactory.getClassHashByName("org.javarosa.core.model.data.UncastData")
        assertFalse(PrototypeFactory.compareHash(tag, hash))
    }

    @Test
    fun shortClassNamePadsWithZeros() {
        val className = "A"
        val hash = PrototypeFactory.getClassHashByName(className)
        assertEquals(32, hash.size)
        // "A" reversed is "A", UTF-8 is [65]
        assertEquals(65, hash[0].toInt())
        for (i in 1 until 32) {
            assertEquals(0, hash[i].toInt(), "Expected zero padding at index $i")
        }
    }
}
