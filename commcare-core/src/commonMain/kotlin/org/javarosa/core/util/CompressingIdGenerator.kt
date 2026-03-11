package org.javarosa.core.util

import kotlin.jvm.JvmStatic

import kotlin.math.floor
import kotlin.math.log
import kotlin.math.pow

/**
 * Created by ctsims on 3/24/2017.
 */
object CompressingIdGenerator {

    /**
     * Compresses the provided input number into a string.
     *
     * Requires three defined symbol spaces to transform the input string, Growth, Lead, and Body.
     *
     * The growth and lead symbol spaces should be mutually exclusive.
     *
     * Multiple strings generated using the same symbol spaces can be concatenated together in
     * such a way that they will always be unique within their inputs
     *
     * The resulting ID will be of the form
     *
     * [G]*LBBBB
     *
     * Where G is a dynamic number of digits from the "Growth" symbol space, L is a single digit
     * from the "Lead" symbol space, and B is a fixed number of digits from the "Body" symbol
     * space.
     *
     * Note that there is *always* exactly one "Lead" digit. It is acceptable for the count of
     * "Body" digits to be 0.
     *
     * @param input a number to be encoded by the scheme provided. Must be a positive integer
     * @param growthSymbols symbol space for growth digits
     * @param leadSymbols symbol space for the lead digit
     * @param bodySymbols symbol space for body digits
     * @param bodyDigitCount the fixed number of "Body" digits that will be used to encode the
     *                       input value
     */
    @JvmStatic
    fun generateCompressedIdString(
        input: Long,
        growthSymbols: String,
        leadSymbols: String,
        bodySymbols: String,
        bodyDigitCount: Int
    ): String {
        if (growthSymbols.isEmpty() || leadSymbols.isEmpty()) {
            throw IllegalArgumentException(
                "Invalid Symbol Space for ID Compression, growth and lead set must both" +
                        " contain at least one symbol" +
                        "\nG[$growthSymbols] | L[$leadSymbols] | B[$bodySymbols]"
            )
        }

        for (c in growthSymbols.toCharArray()) {
            if (leadSymbols.indexOf(c) != -1) {
                throw IllegalArgumentException(
                    "Illegal growth/lead symbol space. The character $c was found in both spaces."
                )
            }
        }

        val leadDigitBase = leadSymbols.length
        val growthDigitBase = growthSymbols.length
        val bodyDigitBase = bodySymbols.length

        val maxSizeOfFixedLengthPortion =
            (bodyDigitBase.toDouble().pow(bodyDigitCount).toLong()) * leadDigitBase

        var growthDigitCount = 0

        if (input >= maxSizeOfFixedLengthPortion) {
            val remainingToEncode = input.toDouble() / maxSizeOfFixedLengthPortion

            growthDigitCount += floor(
                log(remainingToEncode, growthDigitBase.toDouble())
            ).toInt() + 1
        }

        val digitBases = IntArray(growthDigitCount + 1 + bodyDigitCount)
        var digit = 0
        for (i in 0 until growthDigitCount) {
            digitBases[i] = growthDigitBase
            digit++
        }

        digitBases[digit] = leadDigitBase
        digit++
        for (i in 0 until bodyDigitCount) {
            digitBases[digit + i] = bodyDigitBase
        }

        val divisors = LongArray(digitBases.size)
        divisors[divisors.size - 1] = 1
        for (i in divisors.size - 2 downTo 0) {
            divisors[i] = divisors[i + 1] * digitBases[i + 1]
        }

        var remainder = input

        val count = IntArray(digitBases.size)
        for (i in digitBases.indices) {
            count[i] = floor(remainder.toDouble() / divisors[i]).toInt()
            remainder %= divisors[i]
        }
        if (remainder != 0L) {
            throw RuntimeException("Invalid ID Generation! Number was not fully encoded")
        }

        val outputGenerator = CharArray(growthDigitCount + 1 + bodyDigitCount)

        digit = 0
        for (i in 0 until growthDigitCount) {
            outputGenerator[i] = growthSymbols[count[i]]
            digit++
        }
        outputGenerator[digit] = leadSymbols[count[digit]]
        digit++
        for (i in 0 until bodyDigitCount) {
            outputGenerator[digit + i] = bodySymbols[count[digit + i]]
        }

        return outputGenerator.concatToString()
    }
}
