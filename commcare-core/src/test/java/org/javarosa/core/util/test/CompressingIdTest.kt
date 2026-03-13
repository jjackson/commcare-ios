package org.javarosa.core.util.test

import org.javarosa.core.util.CompressingIdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * @author Clayton Sims (csims@dimagi.com)
 */
class CompressingIdTest {

    companion object {
        private const val GROWTH = "HLJXYUWMNV"
        private val GB = GROWTH.length.toLong()
        private const val LEAD = "ACE3459KFPRT"
        private val LB = LEAD.length.toLong()
        private const val BODY = "ACDEFHJKLMNPQRTUVWXY3479"
        private val BB = BODY.length.toLong()
        private const val GROWTH_LIMITED = "123"
        private const val LEAD_LIMITED = "ABC"
        private const val BODY_LIMITED = "ABC12345"
    }

    @Test
    fun basicGrowthTests() {
        assertEquals("AAA", CompressingIdGenerator.generateCompressedIdString(0L,
            GROWTH, LEAD, BODY, 2))

        assertEquals("T99", CompressingIdGenerator.generateCompressedIdString(
            LB * BB * BB - 1,
            GROWTH, LEAD, BODY, 2))

        assertEquals("LAAA", CompressingIdGenerator.generateCompressedIdString(
            LB * BB * BB,
            GROWTH, LEAD, BODY, 2))

        assertEquals("VT99", CompressingIdGenerator.generateCompressedIdString(
            GB * LB * BB * BB - 1,
            GROWTH, LEAD, BODY, 2))

        assertEquals("LHAAA", CompressingIdGenerator.generateCompressedIdString(
            GB * LB * BB * BB,
            GROWTH, LEAD, BODY, 2))

        assertEquals("VVT99", CompressingIdGenerator.generateCompressedIdString(
            GB * GB * LB * BB * BB - 1,
            GROWTH, LEAD, BODY, 2))
    }

    @Test
    fun noBodyTests() {
        assertEquals("A", CompressingIdGenerator.generateCompressedIdString(0L, GROWTH, LEAD, BODY, 0))
        assertEquals("T", CompressingIdGenerator.generateCompressedIdString(11L, GROWTH, LEAD, BODY, 0))
        assertEquals("LA", CompressingIdGenerator.generateCompressedIdString(12L, GROWTH, LEAD, BODY, 0))
        assertEquals("VT", CompressingIdGenerator.generateCompressedIdString(119L, GROWTH, LEAD, BODY, 0))
    }

    @Test
    fun overlapTests() {
        val generatedIds = HashSet<String>()
        for (i in 0L until 100L) {
            val iPart = CompressingIdGenerator.generateCompressedIdString(i, GROWTH_LIMITED, LEAD_LIMITED, BODY_LIMITED, 1)

            for (j in 0L until 100L) {
                val jPart = CompressingIdGenerator.generateCompressedIdString(j, GROWTH_LIMITED, LEAD_LIMITED, BODY_LIMITED, 1)

                for (k in 0L until 100L) {
                    val kPart = CompressingIdGenerator.generateCompressedIdString(k, GROWTH_LIMITED, LEAD_LIMITED, BODY_LIMITED, 1)

                    val joined = iPart + jPart + kPart
                    if (joined in generatedIds) {
                        fail("Duplicate ID [$joined]@[$i,$j,$k]")
                    }
                    generatedIds.add(joined)
                }
            }
        }
    }
}
