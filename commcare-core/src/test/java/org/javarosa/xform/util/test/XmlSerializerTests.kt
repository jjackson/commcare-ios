package org.javarosa.xform.util.test

import org.javarosa.core.model.instance.utils.InstanceUtils
import org.javarosa.core.services.transport.payload.ByteArrayPayload
import org.javarosa.core.services.transport.payload.IDataPayload
import org.javarosa.model.xform.XFormSerializingVisitor
import org.javarosa.xml.util.InvalidStructureException
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory

/**
 * @author $|-|!v@M
 */
class XmlSerializerTests {

    companion object {
        private const val formPath = "/test_nonbmpchar.xml"
    }

    @Test
    fun testParseXmlWithNonBMPCharacters() {
        try {
            val model = InstanceUtils.loadFormInstance(formPath)
            val payload = XFormSerializingVisitor().createSerializedPayload(model)
            assertTrue(payload is ByteArrayPayload)
            assertTrue(payload.getPayloadType() == IDataPayload.PAYLOAD_TYPE_XML)
            val inputStream = payload.getPayloadStream() as ByteArrayInputStream
            try {
                val dbFactory = DocumentBuilderFactory.newInstance()
                val dBuilder = dbFactory.newDocumentBuilder()
                dBuilder.parse(inputStream)
            } catch (e: Exception) {
                e.printStackTrace()
                fail("Xml parsing failed with exception: " + e.message)
            }
        } catch (e: IOException) {
            fail("Unable to load form at $formPath")
        } catch (e: InvalidStructureException) {
            fail("Form at $formPath has an invalid structure.")
        }
    }
}
