package org.javarosa.xml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests that PlatformXmlParser and PlatformXmlSerializer correctly
 * parse and produce XML, with round-trip verification.
 */
public class PlatformXmlRoundTripTest {

    @Test
    public void testParseSimpleElement() {
        String xml = "<root><child>text</child></root>";
        PlatformXmlParser parser = PlatformXmlParserJvmKt.createXmlParser(
                xml.getBytes(), "UTF-8");

        assertEquals(PlatformXmlParser.START_DOCUMENT, parser.getEventType());
        assertEquals(PlatformXmlParser.START_TAG, parser.next());
        assertEquals("root", parser.getName());
        assertEquals(PlatformXmlParser.START_TAG, parser.next());
        assertEquals("child", parser.getName());
        assertEquals(PlatformXmlParser.TEXT, parser.next());
        assertEquals("text", parser.getText());
        assertEquals(PlatformXmlParser.END_TAG, parser.next());
        assertEquals("child", parser.getName());
        assertEquals(PlatformXmlParser.END_TAG, parser.next());
        assertEquals("root", parser.getName());
        assertEquals(PlatformXmlParser.END_DOCUMENT, parser.next());
    }

    @Test
    public void testParseAttributes() {
        String xml = "<item id=\"123\" type=\"test\" />";
        PlatformXmlParser parser = PlatformXmlParserJvmKt.createXmlParser(
                xml.getBytes(), "UTF-8");

        assertEquals(PlatformXmlParser.START_TAG, parser.next());
        assertEquals("item", parser.getName());
        assertEquals(2, parser.getAttributeCount());
        assertEquals("123", parser.getAttributeValue(null, "id"));
        assertEquals("test", parser.getAttributeValue(null, "type"));

        // Self-closing tag produces END_TAG
        assertEquals(PlatformXmlParser.END_TAG, parser.next());
        assertEquals("item", parser.getName());
    }

    @Test
    public void testParseNamespaces() {
        String xml = "<root xmlns=\"http://default.ns\" xmlns:cc=\"http://commcare.org\">"
                + "<cc:item cc:id=\"1\">value</cc:item></root>";
        PlatformXmlParser parser = PlatformXmlParserJvmKt.createXmlParser(
                xml.getBytes(), "UTF-8");

        assertEquals(PlatformXmlParser.START_TAG, parser.next());
        assertEquals("root", parser.getName());
        assertEquals("http://default.ns", parser.getNamespace());

        assertEquals(PlatformXmlParser.START_TAG, parser.next());
        assertEquals("item", parser.getName());
        assertEquals("http://commcare.org", parser.getNamespace());
        assertEquals("1", parser.getAttributeValue("http://commcare.org", "id"));

        assertEquals(PlatformXmlParser.TEXT, parser.next());
        assertEquals("value", parser.getText());

        assertEquals(PlatformXmlParser.END_TAG, parser.next());
        assertEquals("item", parser.getName());
        assertEquals(PlatformXmlParser.END_TAG, parser.next());
        assertEquals("root", parser.getName());
    }

    @Test
    public void testParseDepthTracking() {
        String xml = "<a><b><c/></b></a>";
        PlatformXmlParser parser = PlatformXmlParserJvmKt.createXmlParser(
                xml.getBytes(), "UTF-8");

        assertEquals(0, parser.getDepth());
        parser.next(); // START_TAG a
        assertEquals(1, parser.getDepth());
        parser.next(); // START_TAG b
        assertEquals(2, parser.getDepth());
        parser.next(); // START_TAG c (self-closing)
        assertEquals(3, parser.getDepth());
        parser.next(); // END_TAG c — kxml2 reports END_TAG at same depth as START_TAG
        assertEquals(3, parser.getDepth());
        parser.next(); // END_TAG b
        assertEquals(2, parser.getDepth());
        parser.next(); // END_TAG a
        assertEquals(1, parser.getDepth());
    }

    @Test
    public void testParseEntityReferences() {
        String xml = "<root attr=\"a&amp;b\">&lt;hello&gt; &amp; &quot;world&quot;</root>";
        PlatformXmlParser parser = PlatformXmlParserJvmKt.createXmlParser(
                xml.getBytes(), "UTF-8");

        parser.next(); // START_TAG
        assertEquals("a&b", parser.getAttributeValue(null, "attr"));
        parser.next(); // TEXT
        assertEquals("<hello> & \"world\"", parser.getText());
    }

    @Test
    public void testParseWhitespace() {
        String xml = "<root>   \n  </root>";
        PlatformXmlParser parser = PlatformXmlParserJvmKt.createXmlParser(
                xml.getBytes(), "UTF-8");

        parser.next(); // START_TAG
        parser.next(); // TEXT
        assertEquals(PlatformXmlParser.TEXT, parser.getEventType());
        assertTrue(parser.isWhitespace());
    }

    @Test
    public void testParseCDATA() {
        String xml = "<root><![CDATA[<not>xml</not>]]></root>";
        PlatformXmlParser parser = PlatformXmlParserJvmKt.createXmlParser(
                xml.getBytes(), "UTF-8");

        parser.next(); // START_TAG root
        parser.next(); // TEXT (CDATA content)
        assertEquals(PlatformXmlParser.TEXT, parser.getEventType());
        assertEquals("<not>xml</not>", parser.getText());
    }

    @Test
    public void testParseCommCareStyleXml() {
        // Simplified CommCare-style XML with nested elements and namespaces
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<data xmlns:jr=\"http://openrosa.org/javarosa\" xmlns=\"http://commcare.org\">"
                + "  <case case_id=\"abc-123\" user_id=\"user1\">"
                + "    <create>"
                + "      <case_type>pregnancy</case_type>"
                + "      <case_name>Test Case</case_name>"
                + "    </create>"
                + "  </case>"
                + "</data>";
        PlatformXmlParser parser = PlatformXmlParserJvmKt.createXmlParser(
                xml.getBytes(), "UTF-8");

        // Skip to data element
        assertEquals(PlatformXmlParser.START_TAG, parser.next());
        assertEquals("data", parser.getName());
        assertEquals("http://commcare.org", parser.getNamespace());

        // Navigate to case element (skip whitespace)
        int event = parser.next();
        while (event == PlatformXmlParser.TEXT && parser.isWhitespace()) {
            event = parser.next();
        }
        assertEquals(PlatformXmlParser.START_TAG, event);
        assertEquals("case", parser.getName());
        assertEquals("abc-123", parser.getAttributeValue(null, "case_id"));
        assertEquals("user1", parser.getAttributeValue(null, "user_id"));
    }

    @Test
    public void testSerializerBasic() {
        PlatformXmlSerializer serializer = PlatformXmlSerializerJvmKt.createXmlSerializer();
        serializer.startDocument("UTF-8", null);
        serializer.startTag(null, "root");
        serializer.attribute(null, "id", "1");
        serializer.startTag(null, "child");
        serializer.text("hello");
        serializer.endTag(null, "child");
        serializer.endTag(null, "root");
        serializer.endDocument();

        byte[] output = serializer.toByteArray();
        String xml = new String(output);
        assertTrue(xml.contains("<root"));
        assertTrue(xml.contains("id=\"1\""));
        assertTrue(xml.contains("<child>hello</child>"));
    }

    @Test
    public void testSerializerEscaping() {
        PlatformXmlSerializer serializer = PlatformXmlSerializerJvmKt.createXmlSerializer();
        serializer.startTag(null, "item");
        serializer.attribute(null, "val", "a&b");
        serializer.text("<special> & \"chars\"");
        serializer.endTag(null, "item");

        byte[] output = serializer.toByteArray();
        String xml = new String(output);
        assertTrue(xml.contains("&amp;"));
        assertTrue(xml.contains("&lt;"));
    }

    @Test
    public void testRoundTrip() {
        // Serialize
        PlatformXmlSerializer serializer = PlatformXmlSerializerJvmKt.createXmlSerializer();
        serializer.startTag(null, "root");
        serializer.startTag(null, "item");
        serializer.attribute(null, "id", "42");
        serializer.text("content");
        serializer.endTag(null, "item");
        serializer.startTag(null, "empty");
        serializer.endTag(null, "empty");
        serializer.endTag(null, "root");

        byte[] xml = serializer.toByteArray();

        // Parse back
        PlatformXmlParser parser = PlatformXmlParserJvmKt.createXmlParser(xml, "UTF-8");
        assertEquals(PlatformXmlParser.START_TAG, parser.next());
        assertEquals("root", parser.getName());
        assertEquals(PlatformXmlParser.START_TAG, parser.next());
        assertEquals("item", parser.getName());
        assertEquals("42", parser.getAttributeValue(null, "id"));
        assertEquals(PlatformXmlParser.TEXT, parser.next());
        assertEquals("content", parser.getText());
        assertEquals(PlatformXmlParser.END_TAG, parser.next());
        assertEquals("item", parser.getName());
        assertEquals(PlatformXmlParser.START_TAG, parser.next());
        assertEquals("empty", parser.getName());
        assertEquals(PlatformXmlParser.END_TAG, parser.next());
        assertEquals("empty", parser.getName());
        assertEquals(PlatformXmlParser.END_TAG, parser.next());
        assertEquals("root", parser.getName());
    }

    @Test
    public void testAttributeByIndex() {
        String xml = "<item a=\"1\" b=\"2\" c=\"3\" />";
        PlatformXmlParser parser = PlatformXmlParserJvmKt.createXmlParser(
                xml.getBytes(), "UTF-8");

        parser.next(); // START_TAG
        assertEquals(3, parser.getAttributeCount());
        // Verify all attributes are accessible by index
        boolean foundA = false, foundB = false, foundC = false;
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String name = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            if ("a".equals(name) && "1".equals(value)) foundA = true;
            if ("b".equals(name) && "2".equals(value)) foundB = true;
            if ("c".equals(name) && "3".equals(value)) foundC = true;
        }
        assertTrue(foundA);
        assertTrue(foundB);
        assertTrue(foundC);
    }

    @Test
    public void testParseComment() {
        String xml = "<root><!-- comment --><child/></root>";
        PlatformXmlParser parser = PlatformXmlParserJvmKt.createXmlParser(
                xml.getBytes(), "UTF-8");

        parser.next(); // START_TAG root
        parser.next(); // START_TAG child (comment skipped)
        assertEquals(PlatformXmlParser.START_TAG, parser.getEventType());
        assertEquals("child", parser.getName());
    }
}
