package org.javarosa.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Cross-platform tests for PlatformXmlParser via createXmlParser().
 * These run on both JVM and iOS to verify XML parsing compatibility.
 */
class XmlParserTest {

    @Test
    fun testSimpleElement() {
        val xml = "<root>hello</root>"
        val parser = createXmlParser(xml.encodeToByteArray())

        assertEquals(PlatformXmlParser.START_DOCUMENT, parser.getEventType())

        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("root", parser.getName())

        assertEquals(PlatformXmlParser.TEXT, parser.next())
        assertEquals("hello", parser.getText())

        assertEquals(PlatformXmlParser.END_TAG, parser.next())
        assertEquals("root", parser.getName())

        assertEquals(PlatformXmlParser.END_DOCUMENT, parser.next())
    }

    @Test
    fun testAttributes() {
        val xml = """<item id="123" type="case">text</item>"""
        val parser = createXmlParser(xml.encodeToByteArray())

        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("item", parser.getName())
        assertEquals(2, parser.getAttributeCount())
        assertEquals("123", parser.getAttributeValue(null, "id"))
        assertEquals("case", parser.getAttributeValue(null, "type"))
    }

    @Test
    fun testNestedElements() {
        val xml = """
            <data>
                <case case_id="abc">
                    <create>
                        <case_name>Test</case_name>
                    </create>
                </case>
            </data>
        """.trimIndent()
        val parser = createXmlParser(xml.encodeToByteArray())

        // Navigate to case_name
        var foundCaseName = false
        var caseNameText: String? = null
        while (parser.next() != PlatformXmlParser.END_DOCUMENT) {
            if (parser.getEventType() == PlatformXmlParser.START_TAG && parser.getName() == "case_name") {
                parser.next() // TEXT
                caseNameText = parser.getText()
                foundCaseName = true
                break
            }
        }

        assertEquals(true, foundCaseName)
        assertEquals("Test", caseNameText)
    }

    @Test
    fun testSelfClosingElement() {
        val xml = """<data><empty/><next>ok</next></data>"""
        val parser = createXmlParser(xml.encodeToByteArray())

        assertEquals(PlatformXmlParser.START_TAG, parser.next()) // data
        assertEquals("data", parser.getName())

        assertEquals(PlatformXmlParser.START_TAG, parser.next()) // empty
        assertEquals("empty", parser.getName())

        assertEquals(PlatformXmlParser.END_TAG, parser.next()) // /empty
        assertEquals("empty", parser.getName())

        assertEquals(PlatformXmlParser.START_TAG, parser.next()) // next
        assertEquals("next", parser.getName())

        assertEquals(PlatformXmlParser.TEXT, parser.next())
        assertEquals("ok", parser.getText())
    }

    @Test
    fun testNamespaces() {
        val xml = """<h:html xmlns:h="http://www.w3.org/1999/xhtml"><h:body>text</h:body></h:html>"""
        val parser = createXmlParser(xml.encodeToByteArray())

        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("html", parser.getName())
        assertEquals("http://www.w3.org/1999/xhtml", parser.getNamespace())

        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("body", parser.getName())
        assertEquals("http://www.w3.org/1999/xhtml", parser.getNamespace())
    }

    @Test
    fun testXmlEntities() {
        val xml = "<data>1 &lt; 2 &amp; 3 &gt; 0</data>"
        val parser = createXmlParser(xml.encodeToByteArray())

        parser.next() // START_TAG
        parser.next() // TEXT
        assertEquals("1 < 2 & 3 > 0", parser.getText())
    }

    @Test
    fun testCDATA() {
        val xml = "<data><![CDATA[<not>xml</not>]]></data>"
        val parser = createXmlParser(xml.encodeToByteArray())

        parser.next() // START_TAG
        parser.next() // TEXT (CDATA content)
        assertEquals("<not>xml</not>", parser.getText())
    }

    @Test
    fun testXFormStructure() {
        val xml = """<?xml version="1.0"?>
            <h:html xmlns:h="http://www.w3.org/1999/xhtml"
                    xmlns="http://www.w3.org/2002/xforms">
                <h:head>
                    <h:title>Test Form</h:title>
                    <model>
                        <instance>
                            <data xmlns:jrm="http://dev.commcarehq.org/jr/xforms"
                                  xmlns="http://openrosa.org/formdesigner/test">
                                <name/>
                                <age/>
                            </data>
                        </instance>
                    </model>
                </h:head>
                <h:body>
                    <input ref="/data/name">
                        <label>Name</label>
                    </input>
                </h:body>
            </h:html>"""
        val parser = createXmlParser(xml.encodeToByteArray())

        // Verify we can walk the entire XForm without errors
        val elementNames = mutableListOf<String>()
        while (parser.next() != PlatformXmlParser.END_DOCUMENT) {
            if (parser.getEventType() == PlatformXmlParser.START_TAG) {
                elementNames.add(parser.getName()!!)
            }
        }

        // Key XForm elements should all be found
        assertTrue(elementNames.contains("html"), "Missing html element")
        assertTrue(elementNames.contains("head"), "Missing head element")
        assertTrue(elementNames.contains("title"), "Missing title element")
        assertTrue(elementNames.contains("model"), "Missing model element")
        assertTrue(elementNames.contains("instance"), "Missing instance element")
        assertTrue(elementNames.contains("data"), "Missing data element")
        assertTrue(elementNames.contains("body"), "Missing body element")
        assertTrue(elementNames.contains("input"), "Missing input element")
        assertTrue(elementNames.contains("label"), "Missing label element")
    }

    @Test
    fun testNestedStructure() {
        val xml = "<a><b><c/></b></a>"
        val parser = createXmlParser(xml.encodeToByteArray())

        // Verify correct event sequence regardless of depth reporting details
        val events = mutableListOf<Pair<Int, String?>>()
        while (parser.next() != PlatformXmlParser.END_DOCUMENT) {
            events.add(Pair(parser.getEventType(), parser.getName()))
        }

        assertEquals(PlatformXmlParser.START_TAG, events[0].first)
        assertEquals("a", events[0].second)
        assertEquals(PlatformXmlParser.START_TAG, events[1].first)
        assertEquals("b", events[1].second)
        assertEquals(PlatformXmlParser.START_TAG, events[2].first)
        assertEquals("c", events[2].second)
        assertEquals(PlatformXmlParser.END_TAG, events[3].first)
        assertEquals("c", events[3].second)
        assertEquals(PlatformXmlParser.END_TAG, events[4].first)
        assertEquals("b", events[4].second)
        assertEquals(PlatformXmlParser.END_TAG, events[5].first)
        assertEquals("a", events[5].second)
    }

    private fun assertTrue(condition: Boolean, message: String) {
        assertEquals(true, condition, message)
    }
}
