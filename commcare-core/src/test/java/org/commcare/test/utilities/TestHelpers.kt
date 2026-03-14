package org.commcare.test.utilities

import org.javarosa.core.io.StreamsUtil
import org.junit.Assert
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Helper methods for test evaluation
 *
 * Created by ctsims on 8/14/2015.
 */
class TestHelpers {
    companion object {
        /**
         * Test that the provided input stream contains the same value as the provided string
         *
         * Fails as a junit assertion if the two do not match.
         */
        @JvmStatic
        @Throws(Exception::class)
        fun assertStreamContentsEqual(input: InputStream, expected: String) {
            val baos = ByteArrayOutputStream()
            StreamsUtil.writeFromInputToOutput(input, baos)
            val result = String(baos.toByteArray())
            Assert.assertEquals(expected, result)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun getResourceAsString(resourceName: String): String {
            val baos = ByteArrayOutputStream()
            StreamsUtil.writeFromInputToOutput(TestHelpers::class.java.getResourceAsStream(resourceName), baos)
            return String(baos.toByteArray())
        }
    }
}
