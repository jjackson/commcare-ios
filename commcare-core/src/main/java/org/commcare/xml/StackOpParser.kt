package org.commcare.xml

import org.commcare.suite.model.StackFrameStep
import org.commcare.suite.model.StackOperation
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * @author ctsims
 */
class StackOpParser(parser: PlatformXmlParser) : ElementParser<StackOperation>(parser) {

    companion object {
        const val NAME_STACK: String = "stack"
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): StackOperation {
        val operation = parser.name

        val ifConditional = parser.getAttributeValue(null, "if")

        try {
            return when (operation) {
                "create" -> StackOperation.buildCreateFrame(ifConditional, getChildren(operation))
                "push" -> StackOperation.buildPushFrame(ifConditional, getChildren(operation))
                "clear" -> {
                    if (nextTagInBlock("clear")) {
                        //This means there are children of the clear, no good!
                        throw InvalidStructureException(
                            "The <clear> operation does not support children", this.parser
                        )
                    }
                    StackOperation.buildClearFrame(ifConditional)
                }
                else -> throw InvalidStructureException(
                    "<$operation> is not a valid stack operation!", this.parser
                )
            }
        } catch (e: XPathSyntaxException) {
            throw InvalidStructureException(
                "Invalid condition expression for $operation operation: $ifConditional.\n${e.message}",
                parser
            )
        }
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun getChildren(operation: String): ArrayList<StackFrameStep> {
        val elements = ArrayList<StackFrameStep>()
        val sfep = StackFrameStepParser(parser)
        while (nextTagInBlock(operation)) {
            elements.add(sfep.parse())
        }
        return elements
    }
}
