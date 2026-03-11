package org.javarosa.xml

import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmField
import kotlin.jvm.Throws

/**
 * @author ctsims
 */
class TreeElementParser(
    parser: PlatformXmlParser,
    @JvmField val multiplicity: Int,
    @JvmField val instanceId: String?
) : ElementParser<TreeElement>(parser) {

    @Throws(
        InvalidStructureException::class,
        PlatformIOException::class,
        PlatformXmlParserException::class,
        UnfullfilledRequirementsException::class
    )
    override fun parse(): TreeElement {
        val depth = parser.getDepth()
        val element = TreeElement(parser.getName(), multiplicity)
        element.setInstanceName(instanceId)
        for (i in 0 until parser.getAttributeCount()) {
            element.setAttribute(
                parser.getAttributeNamespace(i),
                parser.getAttributeName(i),
                parser.getAttributeValue(i)
            )
        }

        val multiplicities = HashMap<String, Int>()

        // loop parses all siblings at a given depth
        while (parser.getDepth() >= depth) {
            when (this.nextNonWhitespace()) {
                PlatformXmlParser.START_TAG -> {
                    val name = parser.getName() ?: ""
                    val v = if (multiplicities.containsKey(name)) {
                        multiplicities[name]!! + 1
                    } else {
                        0
                    }
                    multiplicities[name] = v

                    val kid = TreeElementParser(parser, v, instanceId).parse()
                    element.addChild(kid)
                }
                PlatformXmlParser.END_TAG -> return element
                PlatformXmlParser.TEXT -> {
                    element.setValue(UncastData(parser.getText()?.trim() ?: ""))
                }
                else -> throw InvalidStructureException(
                    "Exception while trying to parse an XML Tree, got something other than tags and text",
                    parser
                )
            }
        }

        return element
    }
}
