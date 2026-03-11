package org.javarosa.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-platform tests for PlatformXmlSerializer namespace support.
 * These run on both JVM and iOS to verify namespace serialization compatibility.
 */
class XmlSerializerTest {

    @Test
    fun testBasicSerialization() {
        val serializer = createXmlSerializer()
        serializer.startTag(null, "root")
        serializer.attribute(null, "id", "1")
        serializer.startTag(null, "child")
        serializer.text("hello")
        serializer.endTag(null, "child")
        serializer.endTag(null, "root")

        val xml = serializer.toByteArray().decodeToString()
        assertContains(xml, "<root")
        assertContains(xml, "id=\"1\"")
        assertContains(xml, "<child>hello</child>")
    }

    @Test
    fun testPrefixedNamespace() {
        val serializer = createXmlSerializer()
        serializer.setPrefix("h", "http://www.w3.org/1999/xhtml")
        serializer.startTag("http://www.w3.org/1999/xhtml", "html")
        serializer.startTag("http://www.w3.org/1999/xhtml", "body")
        serializer.text("content")
        serializer.endTag("http://www.w3.org/1999/xhtml", "body")
        serializer.endTag("http://www.w3.org/1999/xhtml", "html")

        val xml = serializer.toByteArray().decodeToString()
        assertContains(xml, "h:html")
        assertContains(xml, "xmlns:h=\"http://www.w3.org/1999/xhtml\"")
        assertContains(xml, "h:body")
        assertContains(xml, "</h:body>")
        assertContains(xml, "</h:html>")
    }

    @Test
    fun testDefaultNamespace() {
        val serializer = createXmlSerializer()
        serializer.setPrefix("", "http://commcare.org")
        serializer.startTag("http://commcare.org", "data")
        serializer.startTag("http://commcare.org", "name")
        serializer.text("test")
        serializer.endTag("http://commcare.org", "name")
        serializer.endTag("http://commcare.org", "data")

        val xml = serializer.toByteArray().decodeToString()
        assertContains(xml, "xmlns=\"http://commcare.org\"")
        // Default namespace: elements should NOT have a prefix
        assertContains(xml, "<data")
        assertContains(xml, "<name>")
        assertContains(xml, "</name>")
        assertContains(xml, "</data>")
    }

    @Test
    fun testMultipleNamespaces() {
        val serializer = createXmlSerializer()
        serializer.setPrefix("h", "http://www.w3.org/1999/xhtml")
        serializer.setPrefix("", "http://www.w3.org/2002/xforms")
        serializer.startTag("http://www.w3.org/1999/xhtml", "html")
        serializer.startTag("http://www.w3.org/1999/xhtml", "head")
        serializer.startTag("http://www.w3.org/2002/xforms", "model")
        serializer.endTag("http://www.w3.org/2002/xforms", "model")
        serializer.endTag("http://www.w3.org/1999/xhtml", "head")
        serializer.endTag("http://www.w3.org/1999/xhtml", "html")

        val xml = serializer.toByteArray().decodeToString()
        assertContains(xml, "h:html")
        assertContains(xml, "xmlns:h=\"http://www.w3.org/1999/xhtml\"")
        assertContains(xml, "xmlns=\"http://www.w3.org/2002/xforms\"")
        assertContains(xml, "h:head")
        assertContains(xml, "<model")
        assertContains(xml, "</model>")
    }

    @Test
    fun testNamespacedAttribute() {
        val serializer = createXmlSerializer()
        serializer.setPrefix("jr", "http://openrosa.org/javarosa")
        serializer.startTag(null, "data")
        serializer.attribute("http://openrosa.org/javarosa", "template", "form1")
        serializer.endTag(null, "data")

        val xml = serializer.toByteArray().decodeToString()
        assertContains(xml, "jr:template=\"form1\"")
    }

    @Test
    fun testSelfClosingWithNamespace() {
        val serializer = createXmlSerializer()
        serializer.setPrefix("cc", "http://commcare.org")
        serializer.startTag("http://commcare.org", "data")
        serializer.startTag("http://commcare.org", "empty")
        serializer.endTag("http://commcare.org", "empty")
        serializer.endTag("http://commcare.org", "data")

        val xml = serializer.toByteArray().decodeToString()
        assertContains(xml, "cc:data")
        assertContains(xml, "cc:empty")
    }

    @Test
    fun testSerializeAndParseRoundTrip() {
        val serializer = createXmlSerializer()
        serializer.setPrefix("h", "http://www.w3.org/1999/xhtml")
        serializer.startTag("http://www.w3.org/1999/xhtml", "html")
        serializer.startTag("http://www.w3.org/1999/xhtml", "body")
        serializer.text("hello")
        serializer.endTag("http://www.w3.org/1999/xhtml", "body")
        serializer.endTag("http://www.w3.org/1999/xhtml", "html")

        val bytes = serializer.toByteArray()
        val parser = createXmlParser(bytes)

        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("html", parser.getName())
        assertEquals("http://www.w3.org/1999/xhtml", parser.getNamespace())

        assertEquals(PlatformXmlParser.START_TAG, parser.next())
        assertEquals("body", parser.getName())
        assertEquals("http://www.w3.org/1999/xhtml", parser.getNamespace())

        assertEquals(PlatformXmlParser.TEXT, parser.next())
        assertEquals("hello", parser.getText())
    }

    @Test
    fun testEscapingInNamespacedContent() {
        val serializer = createXmlSerializer()
        serializer.setPrefix("cc", "http://commcare.org")
        serializer.startTag("http://commcare.org", "data")
        serializer.attribute(null, "val", "a&b")
        serializer.text("<special>")
        serializer.endTag("http://commcare.org", "data")

        val xml = serializer.toByteArray().decodeToString()
        assertContains(xml, "cc:data")
        assertContains(xml, "val=\"a&amp;b\"")
        assertContains(xml, "&lt;special&gt;")
    }

    private fun assertContains(haystack: String, needle: String) {
        assertTrue(haystack.contains(needle), "Expected to find '$needle' in: $haystack")
    }
}
