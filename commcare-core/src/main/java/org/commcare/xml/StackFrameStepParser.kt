package org.commcare.xml

import org.commcare.session.SessionFrame
import org.commcare.suite.model.QueryData
import org.commcare.suite.model.StackFrameStep
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.PlatformMalformedUrlException
import org.javarosa.core.util.PlatformUrl

/**
 * @author ctsims
 */
internal class StackFrameStepParser(parser: PlatformXmlParser) : ElementParser<StackFrameStep>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): StackFrameStep {
        val operation = parser.getName()
        val value = parser.getAttributeValue(null, "value")
        val datumId = parser.getAttributeValue(null, "id")

        return when (operation) {
            "datum" -> parseValue(SessionFrame.STATE_UNKNOWN, datumId)
            "rewind" -> parseValue(SessionFrame.STATE_REWIND, null)
            "mark" -> parseValue(SessionFrame.STATE_MARK, null)
            "command" -> parseValue(SessionFrame.STATE_COMMAND_ID, null)
            "query" -> parseQuery()
            "jump" -> parseJump()
            "instance-datum" -> parseValue(SessionFrame.STATE_MULTIPLE_DATUM_VAL, datumId)
            else -> throw InvalidStructureException(
                "<$operation> is not a valid stack frame element!", this.parser
            )
        }
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun parseQuery(): StackFrameStep {
        val queryId = parser.getAttributeValue(null, "id")
        val url = parser.getAttributeValue(null, "value")
        try {
            PlatformUrl(url)
        } catch (e: PlatformMalformedUrlException) {
            val errorMsg = "<query> element has invalid 'value' attribute ($url)."
            throw InvalidStructureException(errorMsg, parser)
        }

        val step = StackFrameStep(SessionFrame.STATE_QUERY_REQUEST, queryId, url)
        while (nextTagInBlock("query")) {
            val tagName = parser.getName()
            if ("data" == tagName) {
                val queryData = QueryDataParser(parser).parse()
                step.addExtra(queryData.getKey(), queryData)
            }
        }
        return step
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun parseJump(): StackFrameStep {
        val id = parser.getAttributeValue(null, "id")
        val step = StackFrameStep(SessionFrame.STATE_SMART_LINK, id, null)
        val textParser = TextParser(parser)
        nextTag("url")
        nextTag("text")
        step.addExtra("url", textParser.parse())
        return step
    }

    @Throws(PlatformXmlParserException::class, PlatformIOException::class, InvalidStructureException::class)
    private fun parseValue(type: String, datumId: String?): StackFrameStep {
        //TODO: ... require this to have a value!!!! It's not processing this properly
        var value = parser.getAttributeValue(null, "value")
        val valueIsXpath: Boolean
        if (value == null) {
            //must have a child
            value = parser.nextText()
            //Can we get here, or would this have caused an exception?
            if (value == null) {
                throw InvalidStructureException(
                    "ArrayDeque frame element must define a value expression or have a direct value",
                    parser
                )
            } else {
                value = value.trim()
            }
            valueIsXpath = false
        } else {
            //parse out the xpath value to double check for errors
            valueIsXpath = true
        }
        try {
            return StackFrameStep(type, datumId, value, valueIsXpath)
        } catch (e: XPathSyntaxException) {
            throw InvalidStructureException(
                "Invalid expression for stack frame step definition: $value.\n${e.message}",
                parser
            )
        }
    }
}
