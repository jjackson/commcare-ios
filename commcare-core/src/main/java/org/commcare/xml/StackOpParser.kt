package org.commcare.xml

import org.commcare.suite.model.StackFrameStep
import org.commcare.suite.model.StackOperation
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xpath.parser.XPathSyntaxException
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Vector

/**
 * @author ctsims
 */
class StackOpParser(parser: KXmlParser) : ElementParser<StackOperation>(parser) {

    companion object {
        const val NAME_STACK: String = "stack"
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, XmlPullParserException::class)
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

    @Throws(InvalidStructureException::class, PlatformIOException::class, XmlPullParserException::class)
    private fun getChildren(operation: String): Vector<StackFrameStep> {
        val elements = Vector<StackFrameStep>()
        val sfep = StackFrameStepParser(parser)
        while (nextTagInBlock(operation)) {
            elements.addElement(sfep.parse())
        }
        return elements
    }
}
