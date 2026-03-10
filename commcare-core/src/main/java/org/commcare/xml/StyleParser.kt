package org.commcare.xml

import org.commcare.suite.model.DetailField.Builder
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.kxml2.io.KXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Parser used by the DetailParser class to parse the style attributes of a
 * GridEntityView entry
 *
 * @author wspride
 */
class StyleParser(
    val builder: Builder,
    parser: KXmlParser
) : ElementParser<Int>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): Int {
        val fontSize = parser.getAttributeValue(null, "font-size")
        builder.setFontSize(fontSize)

        val horzAlign = parser.getAttributeValue(null, "horz-align")
        builder.setHorizontalAlign(horzAlign)

        val vertAlign = parser.getAttributeValue(null, "vert-align")
        builder.setVerticalAlign(vertAlign)
        //exit style block

        val cssID = parser.getAttributeValue(null, "css-id")
        builder.setCssID(cssID)

        val showBorder = parser.getAttributeValue(null, "show-border")
        builder.setShowBorder(java.lang.Boolean.parseBoolean(showBorder))

        val showShading = parser.getAttributeValue(null, "show-shading")
        builder.setShowShading(java.lang.Boolean.parseBoolean(showShading))

        parser.nextTag()

        return 1
    }
}
