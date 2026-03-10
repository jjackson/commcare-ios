package org.commcare.xml

import org.commcare.suite.model.AssertionSet
import org.commcare.suite.model.Text
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.parser.XPathSyntaxException
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Vector

/**
 * @author ctsims
 */
class AssertionSetParser(parser: KXmlParser) : ElementParser<AssertionSet>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, XmlPullParserException::class)
    override fun parse(): AssertionSet {
        this.checkNode("assertions")

        val tests = Vector<String>()
        val messages = Vector<Text>()

        while (nextTagInBlock("assertions")) {
            if (parser.name == "assert") {
                val test = parser.getAttributeValue(null, "test")
                    ?: throw InvalidStructureException("<assert> element must have a test attribute!", parser)
                try {
                    XPathParseTool.parseXPath(test)
                } catch (e: XPathSyntaxException) {
                    throw InvalidStructureException("Invalid assertion test : $test\n${e.message}", parser)
                }
                parser.nextTag()
                checkNode("text")
                val message = TextParser(parser).parse()
                tests.addElement(test)
                messages.addElement(message)
            } else {
                throw InvalidStructureException("Unknown test : ${parser.name}", parser)
            }
        }
        return AssertionSet(tests, messages)
    }
}
