package org.javarosa.core.io.test

import org.javarosa.core.io.BufferedInputStream
import org.javarosa.core.util.ArrayUtilities
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random

class BufferedInputStreamTests {

    companion object {
        private val sizesToTest = intArrayOf(15, 64, 500, 1280, 2047, 2048, 2049, 5000, 10000, 23000)
        private lateinit var arraysToTest: Array<ByteArray>

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val r = Random()
            arraysToTest = Array(sizesToTest.size) { i ->
                ByteArray(sizesToTest[i]).also { r.nextBytes(it) }
            }
        }
    }

    @Test
    fun testBuffered() {
        val testBuffer = ByteArray(256)

        for (bytes in arraysToTest) {
            try {
                val bis = BufferedInputStream(ByteArrayInputStream(bytes))
                val baos = ByteArrayOutputStream()

                while (true) {
                    val read = bis.read(testBuffer)
                    if (read == -1) {
                        break
                    } else {
                        baos.write(testBuffer, 0, read)
                    }
                }

                if (!ArrayUtilities.arraysEqual(bytes, baos.toByteArray())) {
                    fail("Bulk BufferedInputStream read failed at size ${bytes.size}")
                }
            } catch (e: Exception) {
                fail("Exception while testing bulk read for ${bytes.size} size: ${e.message}")
            }
        }
    }

    @Test
    fun testIndividual() {
        for (bytes in arraysToTest) {
            try {
                val bis = BufferedInputStream(ByteArrayInputStream(bytes))
                var position = 0

                while (true) {
                    val read = bis.read()
                    if (read == -1) {
                        break
                    } else {
                        if (bytes[position] != read.toByte()) {
                            fail("one-by-one BIS read failed at size ${bytes.size} at position $position")
                        }
                    }
                    position++
                }
                if (position != bytes.size) {
                    fail("one-by-one BIS read failed to read full array of size ${bytes.size} only read $position")
                }
            } catch (e: Exception) {
                fail("Exception while testing buffered read for ${bytes.size} size: ${e.message}")
            }
        }
    }
}
