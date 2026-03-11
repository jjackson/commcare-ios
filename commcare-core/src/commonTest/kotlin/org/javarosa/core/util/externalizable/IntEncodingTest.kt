package org.javarosa.core.util.externalizable

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-platform tests for ExtWrapIntEncoding serialization.
 * Verifies that Uniform and Small int encodings round-trip correctly
 * on both JVM and iOS.
 */
class IntEncodingTest {

    private fun roundTripUniform(value: Long): Long {
        val wrapper = ExtWrapIntEncodingUniform(value)
        val out = PlatformDataOutputStream()
        wrapper.writeExternal(out)
        out.close()

        val reader = ExtWrapIntEncodingUniform()
        val inp = PlatformDataInputStream(out.toByteArray())
        reader.readExternal(inp, PrototypeFactory())
        inp.close()
        return reader.`val` as Long
    }

    private fun roundTripSmall(value: Long, bias: Int = ExtWrapIntEncodingSmall.DEFAULT_BIAS): Long {
        val wrapper = ExtWrapIntEncodingSmall(value, bias)
        val out = PlatformDataOutputStream()
        wrapper.writeExternal(out)
        out.close()

        val reader = ExtWrapIntEncodingSmall(bias)
        val inp = PlatformDataInputStream(out.toByteArray())
        reader.readExternal(inp, PrototypeFactory())
        inp.close()
        return reader.`val` as Long
    }

    @Test
    fun testUniformZero() {
        assertEquals(0L, roundTripUniform(0L))
    }

    @Test
    fun testUniformPositive() {
        assertEquals(1L, roundTripUniform(1L))
        assertEquals(63L, roundTripUniform(63L))
        assertEquals(64L, roundTripUniform(64L))
        assertEquals(127L, roundTripUniform(127L))
        assertEquals(1000L, roundTripUniform(1000L))
        assertEquals(Int.MAX_VALUE.toLong(), roundTripUniform(Int.MAX_VALUE.toLong()))
    }

    @Test
    fun testUniformNegative() {
        assertEquals(-1L, roundTripUniform(-1L))
        assertEquals(-64L, roundTripUniform(-64L))
        assertEquals(-65L, roundTripUniform(-65L))
        assertEquals(-1000L, roundTripUniform(-1000L))
        assertEquals(Int.MIN_VALUE.toLong(), roundTripUniform(Int.MIN_VALUE.toLong()))
    }

    @Test
    fun testUniformLargeValues() {
        assertEquals(Long.MAX_VALUE, roundTripUniform(Long.MAX_VALUE))
        assertEquals(Long.MIN_VALUE, roundTripUniform(Long.MIN_VALUE))
    }

    @Test
    fun testSmallZero() {
        assertEquals(0L, roundTripSmall(0L))
    }

    @Test
    fun testSmallOneByte() {
        // With default bias=1, values in [-1, 253] fit in one byte
        assertEquals(-1L, roundTripSmall(-1L))
        assertEquals(0L, roundTripSmall(0L))
        assertEquals(100L, roundTripSmall(100L))
        assertEquals(253L, roundTripSmall(253L))
    }

    @Test
    fun testSmallOverflow() {
        // Values outside [-bias, 254-bias] use 5 bytes (marker + int)
        assertEquals(-2L, roundTripSmall(-2L))
        assertEquals(254L, roundTripSmall(254L))
        assertEquals(1000L, roundTripSmall(1000L))
        assertEquals(-1000L, roundTripSmall(-1000L))
    }

    @Test
    fun testSmallCustomBias() {
        assertEquals(0L, roundTripSmall(0L, 30))
        assertEquals(-30L, roundTripSmall(-30L, 30))
        assertEquals(224L, roundTripSmall(224L, 30))
    }

    @Test
    fun testNumericConversions() {
        assertEquals(42L, numericToLong(42))
        assertEquals(42L, numericToLong(42L))
        assertEquals(42L, numericToLong(42.toShort()))
        assertEquals(42L, numericToLong(42.toByte()))
        assertEquals(65L, numericToLong('A'))

        assertEquals(42, numericToInt(42L))
        assertEquals(42.toShort(), numericToShort(42L))
        assertEquals(42.toByte(), numericToByte(42L))
    }
}
