package org.commcare.util

import org.javarosa.core.io.StreamsUtil
import org.javarosa.engine.xml.XmlUtil
import org.junit.Assert
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream

/**
 * @author $|-|!v@M
 */
class XmlUtilTest {

    @Test
    fun testPrettifyXml() {
        javaClass.getResourceAsStream("/minified_xml.xml").use { inputStream ->
            val bytes = StreamsUtil.inputStreamToByteArray(inputStream)
            val actualOutput = XmlUtil.getPrettyXml(bytes)
            val expectedOutput = getPrettyXml()
            Assert.assertEquals(expectedOutput, actualOutput)
        }
    }

    fun getPrettyXml(): String {
        javaClass.getResourceAsStream("/pretty_printed_xml.xml").use { inputStream ->
            val bis = BufferedInputStream(inputStream)
            val buf = ByteArrayOutputStream()
            var result = bis.read()
            while (result != -1) {
                buf.write(result.toByte().toInt())
                result = bis.read()
            }
            return buf.toString("UTF-8")
        }
    }
}
