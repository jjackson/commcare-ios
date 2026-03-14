package org.commcare.xml

import com.google.common.collect.ImmutableList
import org.commcare.suite.model.FormEntry
import org.commcare.suite.model.PostRequest
import org.commcare.suite.model.QueryData
import org.commcare.suite.model.RemoteRequestEntry
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.test_utils.ReflectionUtils
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.junit.Assert.*
import org.junit.Test
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.*

/**
 * Tests for [EntryParser] when parsing `<remote-request>` elements.
 */
class EntryParserTest {

    @Test
    @Throws(IOException::class, UnfullfilledRequirementsException::class,
            InvalidStructureException::class, XmlPullParserException::class, IllegalAccessException::class)
    fun testParseRemoteRequest() {
        val xml = ("<remote-request>\n"
                + "  <post url=\"https://www.fake.com/claim_patient/\">\n"
                + "    <data key=\"case_id\" ref=\"instance('session')/session/case_id\"/>\n"
                + "    <data key=\"case_id_list\""
                + "         nodeset=\"instance('selected-cases')/session-data/value\""
                + "         exclude=\"count(instance('casedb')/casedb/case[@case_id = current()/.]) = 1\""
                + "         ref=\".\"/>"
                + "  </post>\n"
                + "  <command id=\"search\">\n"
                + "    <display>\n"
                + "      <text>Search</text>\n"
                + "    </display>\n"
                + "  </command>\n"
                + "</remote-request>")
        val post = getRemoteRequestPost(xml)
        @Suppress("UNCHECKED_CAST")
        val params = ReflectionUtils.getField(post, "params") as List<QueryData>
        assertEquals(2, params.size)
        assertEquals("case_id", params[0].getKey())
        assertEquals("case_id_list", params[1].getKey())

        val instances = TestInstances.getInstances()
        instances[TestInstances.CASEDB] = TestInstances.buildCaseDb(ImmutableList.of("123", "456", "789"))
        val evalContext = EvaluationContext(null, instances)

        var evaluatedParams = post.getEvaluatedParams(evalContext, false)
        assertEquals(listOf("bang"), evaluatedParams["case_id"])
        assertFalse(evaluatedParams.containsKey("case_id_list"))

        evaluatedParams = post.getEvaluatedParams(evalContext, true)
        assertEquals(listOf("bang"), evaluatedParams["case_id"])
        assertTrue(evaluatedParams.containsKey("case_id_list"))
    }

    @Test
    @Throws(IOException::class, UnfullfilledRequirementsException::class,
            InvalidStructureException::class, XmlPullParserException::class)
    fun testParseEntry() {
        val xml = ("<entry>\n"
                + "  <form>http://openrosa.org/formdesigner/2f9</form>\n"
                + "  <command id=\"search\">\n"
                + "    <display>\n"
                + "      <text>Search</text>\n"
                + "    </display>\n"
                + "  </command>\n"
                + "</entry>")
        val parser = ParserTestUtils.buildParser(xml, EntryParser::buildEntryParser)
        val entry = parser.parse() as FormEntry
        assertEquals(entry.getXFormNamespace(), "http://openrosa.org/formdesigner/2f9")
        assertNull(entry.getPostRequest())
    }

    @Test
    @Throws(IOException::class, UnfullfilledRequirementsException::class,
            InvalidStructureException::class, XmlPullParserException::class)
    fun testParseEntryWithPost() {
        val xml = ("<entry>\n"
                + "  <form>http://openrosa.org/formdesigner/2f9</form>\n"
                + "  <post url=\"https://www.fake.com/claim_patient/\">\n"
                + "    <data key=\"case_id\" ref=\"instance('session')/session/case_id\"/>\n"
                + "  </post>\n"
                + "  <command id=\"search\">\n"
                + "    <display>\n"
                + "      <text>Search</text>\n"
                + "    </display>\n"
                + "  </command>\n"
                + "</entry>")
        val parser = ParserTestUtils.buildParser(xml, EntryParser::buildEntryParser)
        val entry = parser.parse() as FormEntry
        assertEquals(entry.getXFormNamespace(), "http://openrosa.org/formdesigner/2f9")
        assertEquals(entry.getPostRequest()!!.getUrl().toString(), "https://www.fake.com/claim_patient/")
    }

    @Throws(UnfullfilledRequirementsException::class, InvalidStructureException::class,
            XmlPullParserException::class, IOException::class)
    private fun getRemoteRequestPost(xml: String): PostRequest {
        val parser = ParserTestUtils.buildParser(xml, EntryParser::buildRemoteSyncParser)
        val entry = parser.parse() as RemoteRequestEntry
        return entry.getPostRequest()!!
    }
}
