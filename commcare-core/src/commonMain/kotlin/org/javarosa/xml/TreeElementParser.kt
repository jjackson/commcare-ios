package org.javarosa.xml

import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmField

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
                    // Skip empty-after-trim text. Setting an empty value
                    // marks the element as non-childable (isChildable checks
                    // `value == null`), which breaks the very next addChild
                    // call with "Can't add children to node that has data
                    // value!". This happens when parsers produce a stray
                    // whitespace TEXT event between child elements that
                    // `nextNonWhitespace` didn't coalesce — see Wave 5a
                    // bug chain, commcare-ios issue.
                    val text = parser.getText()?.trim() ?: ""
                    if (text.isNotEmpty()) {
                        element.setValue(UncastData(text))
                    }
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
