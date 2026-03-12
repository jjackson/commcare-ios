package org.commcare.xml

import org.commcare.suite.model.DetailField.Builder
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.xml.util.UnfullfilledRequirementsException

/**
 * Parser used in DetailParser to parse the Grid attributes for a GridEntityView
 *
 * @author wspride
 */
class GridParser(
    val builder: Builder,
    parser: PlatformXmlParser
) : ElementParser<Int>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class, UnfullfilledRequirementsException::class)
    override fun parse(): Int {
        checkNode("grid")
        val gridx = parser.getAttributeValue(null, "grid-x")
        builder.setGridX(gridx!!.toInt())

        val gridy = parser.getAttributeValue(null, "grid-y")
        builder.setGridY(gridy!!.toInt())

        val gridw = parser.getAttributeValue(null, "grid-width")
        builder.setGridWidth(gridw!!.toInt())

        val gridh = parser.getAttributeValue(null, "grid-height")
        builder.setGridHeight(gridh!!.toInt())

        //exit grid block
        parser.nextTag()

        return 1
    }
}
