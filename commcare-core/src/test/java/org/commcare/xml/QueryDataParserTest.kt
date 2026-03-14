package org.commcare.xml

import org.commcare.suite.model.QueryData
import org.commcare.suite.model.QueryGroup
import org.commcare.suite.model.QueryPrompt
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.junit.Assert.*
import org.junit.Test
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.*

/**
 * Low level tests for query data parsing
 */
class QueryDataParserTest {

    @Test
    @Throws(InvalidStructureException::class, XmlPullParserException::class, IOException::class, UnfullfilledRequirementsException::class)
    fun testParseValueData() {
        val query = "<data key=\"device_id\" ref=\"instance('session')/session/case_id\"/>"
        val parser = ParserTestUtils.buildParser(query, QueryDataParser::class.java)
        val queryData = parser.parse()
        assertEquals("device_id", queryData.getKey())

        val evalContext = EvaluationContext(null, TestInstances.getInstances())
        assertEquals(listOf("bang"), queryData.getValues(evalContext))
    }

    @Test
    @Throws(InvalidStructureException::class, XmlPullParserException::class, UnfullfilledRequirementsException::class, IOException::class)
    fun testParseValueData_withExclude() {
        val query = ("<data key=\"device_id\" ref=\"instance('session')/session/case_id\""
                + "exclude=\"true()\"/>")
        val parser = ParserTestUtils.buildParser(query, QueryDataParser::class.java)
        val queryData = parser.parse()
        assertEquals("device_id", queryData.getKey())

        val evalContext = EvaluationContext(null, TestInstances.getInstances())
        assertEquals(emptyList<String>(), queryData.getValues(evalContext))
    }

    @Test
    @Throws(InvalidStructureException::class, XmlPullParserException::class, UnfullfilledRequirementsException::class, IOException::class)
    fun testParseValueData_withRequiredAttribute() {
        val query = ("<data key=\"device_id\" ref=\"instance('session')/session/case_id\""
                + "required=\"true()\"/>")
        val parser = ParserTestUtils.buildParser(query, QueryPromptParser::class.java)
        val queryData = parser.parse()

        val evalContext = EvaluationContext(null, TestInstances.getInstances())
        assertTrue(queryData.required!!.test!!.eval(evalContext) as Boolean)
        assertNull(queryData.required!!.message)
    }

    @Test
    @Throws(InvalidStructureException::class, XmlPullParserException::class, UnfullfilledRequirementsException::class, IOException::class)
    fun testParseValueData_withRequiredNode() {
        val query = ("<prompt key=\"name\">"
                + "          <required test=\"true()\">"
                + "            <text>This field can't be empty</text>"
                + "          </required>"
                + "</prompt>")
        val parser = ParserTestUtils.buildParser(query, QueryPromptParser::class.java)
        val queryData = parser.parse()
        val evalContext = EvaluationContext(null, TestInstances.getInstances())
        assertTrue(queryData.required!!.test!!.eval(evalContext) as Boolean)
        assertTrue(queryData.getRequiredMessage(evalContext).contentEquals("This field can't be empty"))
    }

    @Test(expected = InvalidStructureException::class)
    @Throws(InvalidStructureException::class, XmlPullParserException::class, UnfullfilledRequirementsException::class, IOException::class)
    fun testParseValueData_withRequiredAttributeAndNode() {
        val query = ("<prompt key=\"name\" required=\"true()\">"
                + "          <required test=\"true()\">"
                + "            <text>This field can't be empty</text>"
                + "          </required>"
                + "</prompt>")
        val parser = ParserTestUtils.buildParser(query, QueryPromptParser::class.java)
        parser.parse()
    }

    @Test
    @Throws(InvalidStructureException::class, XmlPullParserException::class, IOException::class, UnfullfilledRequirementsException::class)
    fun testParseListData() {
        val query = ("<data key=\"case_id_list\""
                + "nodeset=\"instance('selected-cases')/session-data/value\""
                + "exclude=\"count(instance('casedb')/casedb/case[@case_id = current()/.]) = 1\""
                + "ref=\".\"/>")
        val parser = ParserTestUtils.buildParser(query, QueryDataParser::class.java)
        val queryData = parser.parse()
        assertEquals("case_id_list", queryData.getKey())

        val instances = TestInstances.getInstances()
        val evalContext = EvaluationContext(null, instances)
        assertEquals(listOf("456", "789"), queryData.getValues(evalContext))
    }

    @Test
    @Throws(InvalidStructureException::class, XmlPullParserException::class, UnfullfilledRequirementsException::class, IOException::class)
    fun testParseListData_noExclude() {
        val query = ("<data key=\"case_id_list\""
                + "nodeset=\"instance('selected-cases')/session-data/value\""
                + "ref=\".\"/>")
        val parser = ParserTestUtils.buildParser(query, QueryDataParser::class.java)
        val queryData = parser.parse()
        assertEquals("case_id_list", queryData.getKey())

        val instances = TestInstances.getInstances()
        val evalContext = EvaluationContext(null, instances)
        assertEquals(listOf("123", "456", "789"), queryData.getValues(evalContext))
    }

    @Test
    @Throws(XmlPullParserException::class, IOException::class, UnfullfilledRequirementsException::class)
    fun testParseQueryData_noRef() {
        val query = "<data key=\"case_id_list\"></data>"
        val parser = ParserTestUtils.buildParser(query, QueryDataParser::class.java)
        try {
            parser.parse()
            fail("Expected InvalidStructureException")
        } catch (ignored: InvalidStructureException) {
        }
    }

    @Test
    @Throws(XmlPullParserException::class, IOException::class, UnfullfilledRequirementsException::class)
    fun testParseQueryData_badNesting() {
        val query = ("<data key=\"case_id_list\" ref=\"true()\">"
                + "<data key=\"device_id\" ref=\"instance('session')/session/case_id\"/>"
                + "</data>")
        val parser = ParserTestUtils.buildParser(query, QueryDataParser::class.java)
        try {
            parser.parse()
            fail("Expected InvalidStructureException")
        } catch (ignored: InvalidStructureException) {
        }
    }

    @Test
    @Throws(InvalidStructureException::class, XmlPullParserException::class, UnfullfilledRequirementsException::class, IOException::class)
    fun testParseValueData_withGroupKeyAttribute() {
        val query = "<prompt key=\"name\" group_key=\"group_header_1\"></prompt>"
        val parser = ParserTestUtils.buildParser(query, QueryPromptParser::class.java)
        val queryData = parser.parse()
        assertEquals("group_header_1", queryData.groupKey)
    }

    @Test
    @Throws(InvalidStructureException::class, XmlPullParserException::class, UnfullfilledRequirementsException::class, IOException::class)
    fun testParseValueData_withGroup() {
        val query = ("<group key=\"group_header_0\">"
                + "<display><text><locale id=\"search_property.m0.group_header_0\"/></text></display>"
                + "</group>")
        val parser = ParserTestUtils.buildParser(query, QueryGroupParser::class.java)
        val queryData = parser.parse()
        assertEquals("group_header_0", queryData.getKey())
    }
}
