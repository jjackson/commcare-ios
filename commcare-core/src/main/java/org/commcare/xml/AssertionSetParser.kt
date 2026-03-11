package org.commcare.xml

import org.commcare.suite.model.AssertionSet
import org.commcare.suite.model.Text
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * @author ctsims
 */
class AssertionSetParser(parser: PlatformXmlParser) : ElementParser<AssertionSet>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): AssertionSet {
        this.checkNode("assertions")

        val tests = ArrayList<String>()
        val messages = ArrayList<Text>()

        while (nextTagInBlock("assertions")) {
            if (parser.getName() == "assert") {
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
                tests.add(test)
                messages.add(message)
            } else {
                throw InvalidStructureException("Unknown test : ${parser.getName()}", parser)
            }
        }
        return AssertionSet(tests, messages)
    }
}
