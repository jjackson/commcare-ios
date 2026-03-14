package org.commcare.xml

import org.commcare.suite.model.RemoteQueryDatum
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

/**
 * Low level tests for session parsing
 */
class SessionDatumParserTest {

    @Test
    @Throws(UnfullfilledRequirementsException::class, InvalidStructureException::class,
            XmlPullParserException::class, IOException::class)
    fun testSessionDatumParser() {
        val query = ("<query url=\"https://www.fake.com/patient_search/\" storage-instance=\"patients\">"
                + "<data key=\"device_id\" ref=\"instance('session')/session/context/deviceid\"/>"
                + "</query>")
        val parser = ParserTestUtils.buildParser(query, SessionDatumParser::class.java)
        val datum = parser.parse() as RemoteQueryDatum

        val hiddenQueryValues = datum.getHiddenQueryValues()!!
        assertEquals(1, hiddenQueryValues.size)
        val queryData = hiddenQueryValues[0]
        assertEquals("device_id", queryData.getKey())
    }

    @Test
    @Throws(UnfullfilledRequirementsException::class, InvalidStructureException::class,
            XmlPullParserException::class, IOException::class)
    fun testSessionDatumParser__withTitle() {
        val query = ("<query url=\"https://www.fake.com/patient_search/\" storage-instance=\"patients\">"
                + "<title>"
                + "<text>"
                + "<locale id=\"locale_id\"/>"
                + "</text>"
                + "</title>"
                + "<data key=\"device_id\" ref=\"instance('session')/session/context/deviceid\"/>"
                + "</query>")
        val parser = ParserTestUtils.buildParser(query, SessionDatumParser::class.java)
        val datum = parser.parse() as RemoteQueryDatum
        val title = datum.getTitleText()!!.getArgument()
        val hiddenQueryValues = datum.getHiddenQueryValues()!!

        assertEquals(1, hiddenQueryValues.size)
        val queryData = hiddenQueryValues[0]
        assertEquals("device_id", queryData.getKey())
        assertEquals("locale_id", title)
    }
}
