package org.javarosa.xml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that PlatformXmlParser and PlatformXmlSerializer correctly
 * parse and produce XML, with round-trip verification.
 */
class PlatformXmlRoundTripTest {

    @Test
    fun testParseSimpleElement() {
        val xml = "<root><child>text</child></root>"
        val parser = createXmlParser(xml.toByteArray(), "UTF-8")

        assertEquals(PlatformXmlParser.START_DOCUMENT, parser.getEventType())
        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("root", parser.getName())
        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("child", parser.getName())
        assertEquals(PlatformXmlParser.TEXT, parser.next())
        assertEquals("text", parser.getText())
        assertEquals(PlatformXmlParser.END_TAG, parser.next())
        assertEquals("child", parser.getName())
        assertEquals(PlatformXmlParser.END_TAG, parser.next())
        assertEquals("root", parser.getName())
        assertEquals(PlatformXmlParser.END_DOCUMENT, parser.next())
    }

    @Test
    fun testParseAttributes() {
        val xml = "<item id=\"123\" type=\"test\" />"
        val parser = createXmlParser(xml.toByteArray(), "UTF-8")

        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("item", parser.getName())
        assertEquals(2, parser.getAttributeCount())
        assertEquals("123", parser.getAttributeValue(null, "id"))
        assertEquals("test", parser.getAttributeValue(null, "type"))

        // Self-closing tag produces END_TAG
        assertEquals(PlatformXmlParser.END_TAG, parser.next())
        assertEquals("item", parser.getName())
    }

    @Test
    fun testParseNamespaces() {
        val xml = "<root xmlns=\"http://default.ns\" xmlns:cc=\"http://commcare.org\">" +
            "<cc:item cc:id=\"1\">value</cc:item></root>"
        val parser = createXmlParser(xml.toByteArray(), "UTF-8")

        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("root", parser.getName())
        assertEquals("http://default.ns", parser.getNamespace())

        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("item", parser.getName())
        assertEquals("http://commcare.org", parser.getNamespace())
        assertEquals("1", parser.getAttributeValue("http://commcare.org", "id"))

        assertEquals(PlatformXmlParser.TEXT, parser.next())
        assertEquals("value", parser.getText())

        assertEquals(PlatformXmlParser.END_TAG, parser.next())
        assertEquals("item", parser.getName())
        assertEquals(PlatformXmlParser.END_TAG, parser.next())
        assertEquals("root", parser.getName())
    }

    @Test
    fun testParseDepthTracking() {
        val xml = "<a><b><c/></b></a>"
        val parser = createXmlParser(xml.toByteArray(), "UTF-8")

        assertEquals(0, parser.getDepth())
        parser.next() // START_TAG a
        assertEquals(1, parser.getDepth())
        parser.next() // START_TAG b
        assertEquals(2, parser.getDepth())
        parser.next() // START_TAG c (self-closing)
        assertEquals(3, parser.getDepth())
        parser.next() // END_TAG c -- kxml2 reports END_TAG at same depth as START_TAG
        assertEquals(3, parser.getDepth())
        parser.next() // END_TAG b
        assertEquals(2, parser.getDepth())
        parser.next() // END_TAG a
        assertEquals(1, parser.getDepth())
    }

    @Test
    fun testParseEntityReferences() {
        val xml = "<root attr=\"a&amp;b\">&lt;hello&gt; &amp; &quot;world&quot;</root>"
        val parser = createXmlParser(xml.toByteArray(), "UTF-8")

        parser.next() // START_TAG
        assertEquals("a&b", parser.getAttributeValue(null, "attr"))
        parser.next() // TEXT
        assertEquals("<hello> & \"world\"", parser.getText())
    }

    @Test
    fun testParseWhitespace() {
        val xml = "<root>   \n  </root>"
        val parser = createXmlParser(xml.toByteArray(), "UTF-8")

        parser.next() // START_TAG
        parser.next() // TEXT
        assertEquals(PlatformXmlParser.TEXT, parser.getEventType())
        assertTrue(parser.isWhitespace())
    }

    @Test
    fun testParseCDATA() {
        val xml = "<root><![CDATA[<not>xml</not>]]></root>"
        val parser = createXmlParser(xml.toByteArray(), "UTF-8")

        parser.next() // START_TAG root
        parser.next() // TEXT (CDATA content)
        assertEquals(PlatformXmlParser.TEXT, parser.getEventType())
        assertEquals("<not>xml</not>", parser.getText())
    }

    @Test
    fun testParseCommCareStyleXml() {
        // Simplified CommCare-style XML with nested elements and namespaces
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<data xmlns:jr=\"http://openrosa.org/javarosa\" xmlns=\"http://commcare.org\">" +
            "  <case case_id=\"abc-123\" user_id=\"user1\">" +
            "    <create>" +
            "      <case_type>pregnancy</case_type>" +
            "      <case_name>Test Case</case_name>" +
            "    </create>" +
            "  </case>" +
            "</data>"
        val parser = createXmlParser(xml.toByteArray(), "UTF-8")

        // Skip to data element
        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("data", parser.getName())
        assertEquals("http://commcare.org", parser.getNamespace())

        // Navigate to case element (skip whitespace)
        var event = parser.next()
        while (event == PlatformXmlParser.TEXT && parser.isWhitespace()) {
            event = parser.next()
        }
        assertEquals(PlatformXmlParser.START_TAG, event)
        assertEquals("case", parser.getName())
        assertEquals("abc-123", parser.getAttributeValue(null, "case_id"))
        assertEquals("user1", parser.getAttributeValue(null, "user_id"))
    }

    @Test
    fun testSerializerBasic() {
        val serializer = createXmlSerializer()
        serializer.startDocument("UTF-8", null)
        serializer.startTag(null, "root")
        serializer.attribute(null, "id", "1")
        serializer.startTag(null, "child")
        serializer.text("hello")
        serializer.endTag(null, "child")
        serializer.endTag(null, "root")
        serializer.endDocument()

        val output = serializer.toByteArray()
        val xml = String(output)
        assertTrue(xml.contains("<root"))
        assertTrue(xml.contains("id=\"1\""))
        assertTrue(xml.contains("<child>hello</child>"))
    }

    @Test
    fun testSerializerEscaping() {
        val serializer = createXmlSerializer()
        serializer.startTag(null, "item")
        serializer.attribute(null, "val", "a&b")
        serializer.text("<special> & \"chars\"")
        serializer.endTag(null, "item")

        val output = serializer.toByteArray()
        val xml = String(output)
        assertTrue(xml.contains("&amp;"))
        assertTrue(xml.contains("&lt;"))
    }

    @Test
    fun testRoundTrip() {
        // Serialize
        val serializer = createXmlSerializer()
        serializer.startTag(null, "root")
        serializer.startTag(null, "item")
        serializer.attribute(null, "id", "42")
        serializer.text("content")
        serializer.endTag(null, "item")
        serializer.startTag(null, "empty")
        serializer.endTag(null, "empty")
        serializer.endTag(null, "root")

        val xml = serializer.toByteArray()

        // Parse back
        val parser = createXmlParser(xml, "UTF-8")
        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("root", parser.getName())
        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("item", parser.getName())
        assertEquals("42", parser.getAttributeValue(null, "id"))
        assertEquals(PlatformXmlParser.TEXT, parser.next())
        assertEquals("content", parser.getText())
        assertEquals(PlatformXmlParser.END_TAG, parser.next())
        assertEquals("item", parser.getName())
        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("empty", parser.getName())
        assertEquals(PlatformXmlParser.END_TAG, parser.next())
        assertEquals("empty", parser.getName())
        assertEquals(PlatformXmlParser.END_TAG, parser.next())
        assertEquals("root", parser.getName())
    }

    @Test
    fun testAttributeByIndex() {
        val xml = "<item a=\"1\" b=\"2\" c=\"3\" />"
        val parser = createXmlParser(xml.toByteArray(), "UTF-8")

        parser.next() // START_TAG
        assertEquals(3, parser.getAttributeCount())
        // Verify all attributes are accessible by index
        var foundA = false
        var foundB = false
        var foundC = false
        for (i in 0 until parser.getAttributeCount()) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i)
            if (name == "a" && value == "1") foundA = true
            if (name == "b" && value == "2") foundB = true
            if (name == "c" && value == "3") foundC = true
        }
        assertTrue(foundA)
        assertTrue(foundB)
        assertTrue(foundC)
    }

    @Test
    fun testParseComment() {
        val xml = "<root><!-- comment --><child/></root>"
        val parser = createXmlParser(xml.toByteArray(), "UTF-8")

        parser.next() // START_TAG root
        parser.next() // START_TAG child (comment skipped)
        assertEquals(PlatformXmlParser.START_TAG, parser.getEventType())
        assertEquals("child", parser.getName())
    }
}
