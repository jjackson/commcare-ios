package org.javarosa.core.util.externalizable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cross-platform test verifying PrototypeFactory hash computation
 * produces identical results on JVM and iOS.
 *
 * The authoritative algorithm (matching CommCare Android wire format):
 * 1. Reverse the class name string
 * 2. Encode reversed string to UTF-8 bytes
 * 3. Take first 32 bytes (pad with 0x00 if shorter)
 *
 * These tests use golden values computed from that algorithm so both
 * platforms are validated against the same expected output.
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

    // --- Golden value tests: exact expected hashes for known class names ---
    // These verify cross-platform consistency by checking against pre-computed values.

    /**
     * Helper: compute the expected hash using the canonical algorithm.
     * Both platforms must match this.
     */
    private fun expectedHash(className: String): ByteArray {
        val reversed = StringBuilder(className).reverse().toString()
        val utf8 = reversed.encodeToByteArray()
        val hash = ByteArray(32)
        for (i in 0 until minOf(32, utf8.size)) {
            hash[i] = utf8[i]
        }
        return hash
    }

    @Test
    fun goldenHashTreeElement() {
        val className = "org.javarosa.core.model.instance.TreeElement"
        val hash = PrototypeFactory.getClassHashByName(className)
        val expected = expectedHash(className)
        assertEquals(32, hash.size)
        assertTrue(
            PrototypeFactory.compareHash(hash, expected),
            "TreeElement hash mismatch: expected ${expected.toList()}, got ${hash.toList()}"
        )
    }

    @Test
    fun goldenHashFormDef() {
        val className = "org.javarosa.core.model.FormDef"
        val hash = PrototypeFactory.getClassHashByName(className)
        val expected = expectedHash(className)
        assertEquals(32, hash.size)
        assertTrue(
            PrototypeFactory.compareHash(hash, expected),
            "FormDef hash mismatch: expected ${expected.toList()}, got ${hash.toList()}"
        )
    }

    @Test
    fun goldenHashLongClassName() {
        // This class name is longer than 32 bytes when reversed,
        // so the hash should be exactly the first 32 bytes (truncated).
        val className = "org.javarosa.core.model.instance.TreeElement"
        val reversed = StringBuilder(className).reverse().toString()
        val utf8 = reversed.encodeToByteArray()
        assertTrue(utf8.size > 32, "Test requires a class name longer than 32 UTF-8 bytes")

        val hash = PrototypeFactory.getClassHashByName(className)
        assertEquals(32, hash.size)
        // Verify truncation: byte at index 31 should match, but no byte 32
        assertEquals(utf8[31], hash[31])
    }

    @Test
    fun goldenHashExactly32Bytes() {
        // "abcdefghijklmnopqrstuvwxyz012345" is exactly 32 ASCII chars
        val className = "abcdefghijklmnopqrstuvwxyz012345"
        assertEquals(32, className.encodeToByteArray().size, "Test setup: class name should be 32 bytes")

        val hash = PrototypeFactory.getClassHashByName(className)
        val reversed = StringBuilder(className).reverse().toString()
        val expected = reversed.encodeToByteArray()
        for (i in 0 until 32) {
            assertEquals(expected[i], hash[i], "Byte mismatch at index $i")
        }
    }

    @Test
    fun goldenHashEmptyString() {
        val hash = PrototypeFactory.getClassHashByName("")
        assertEquals(32, hash.size)
        // Empty string reversed is empty, so all bytes should be zero
        for (i in 0 until 32) {
            assertEquals(0, hash[i].toInt(), "Expected zero at index $i for empty class name")
        }
    }

    @Test
    fun goldenHashMultipleKnownClasses() {
        // Verify several real CommCare class names all produce correct hashes
        val classNames = listOf(
            "org.javarosa.core.model.instance.TreeElement",
            "org.javarosa.core.model.FormDef",
            "org.javarosa.core.model.data.UncastData",
            "org.javarosa.core.model.data.StringData",
            "org.javarosa.core.model.data.IntegerData",
            "org.javarosa.core.model.data.DateData",
            "org.javarosa.core.model.data.SelectOneData",
            "org.javarosa.core.model.data.SelectMultiData",
            "org.commcare.cases.model.Case"
        )

        for (className in classNames) {
            val hash = PrototypeFactory.getClassHashByName(className)
            val expected = expectedHash(className)
            assertTrue(
                PrototypeFactory.compareHash(hash, expected),
                "Hash mismatch for $className"
            )
        }
    }

    @Test
    fun allKnownClassesHaveUniqueHashes() {
        val classNames = listOf(
            "org.javarosa.core.model.instance.TreeElement",
            "org.javarosa.core.model.FormDef",
            "org.javarosa.core.model.data.UncastData",
            "org.javarosa.core.model.data.StringData",
            "org.javarosa.core.model.data.IntegerData",
            "org.javarosa.core.model.data.DateData",
            "org.commcare.cases.model.Case"
        )

        val hashes = classNames.map { PrototypeFactory.getClassHashByName(it).toList() }
        val uniqueHashes = hashes.toSet()
        assertEquals(
            hashes.size, uniqueHashes.size,
            "Expected all class hashes to be unique"
        )
    }
}
