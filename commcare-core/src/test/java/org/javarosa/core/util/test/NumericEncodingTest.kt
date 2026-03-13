package org.javarosa.core.util.test

import org.javarosa.core.util.externalizable.ExtWrapIntEncoding
import org.javarosa.core.util.externalizable.ExtWrapIntEncodingSmall
import org.javarosa.core.util.externalizable.ExtWrapIntEncodingUniform
import org.junit.Test

class NumericEncodingTest {

    @Test
    fun testIntEncodingUniform() {
        val enc = ExtWrapIntEncodingUniform()

        testNumericEncoding(0, enc)
        testNumericEncoding(-1, enc)
        testNumericEncoding(1, enc)
        testNumericEncoding(-2, enc)

        for (i in 3..64) {
            val min = if (i < 64) -(0x01L shl (i - 1)) else Long.MIN_VALUE
            val max = if (i < 64) (0x01L shl (i - 1)) - 1 else Long.MAX_VALUE

            testNumericEncoding(max - 1, enc)
            testNumericEncoding(max, enc)
            if (i < 64) testNumericEncoding(max + 1, enc)
            testNumericEncoding(min + 1, enc)
            testNumericEncoding(min, enc)
            if (i < 64) testNumericEncoding(min - 1, enc)
        }
    }

    @Test
    fun testIntEncodingSmall() {
        val biases = intArrayOf(0, 1, 30, 128, 254)
        val smallTests = intArrayOf(0, 1, 126, 127, 128, 129, 253, 254, 255, 256, -1, -2, -127, -128, -129)
        val largeTests = intArrayOf(3750, -3750, 33947015, -33947015, Int.MAX_VALUE, Int.MAX_VALUE - 1, Int.MIN_VALUE, Int.MIN_VALUE + 1)

        for (i in -1 until biases.size) {
            val bias: Int
            if (i == -1) {
                bias = ExtWrapIntEncodingSmall.DEFAULT_BIAS
            } else {
                bias = biases[i]
                if (bias == ExtWrapIntEncodingSmall.DEFAULT_BIAS) continue
            }

            val enc: ExtWrapIntEncoding = ExtWrapIntEncodingSmall(bias)

            for (test in smallTests) {
                testNumericEncoding(test.toLong(), enc)
                if (bias != 0) testNumericEncoding((test - bias).toLong(), enc)
            }

            for (test in largeTests) {
                testNumericEncoding(test.toLong(), enc)
            }
        }
    }

    private fun testNumericEncoding(value: Long, encoding: ExtWrapIntEncoding) {
        ExternalizableTest.testExternalizable(encoding.clone(value), encoding, null, null)
    }
}
