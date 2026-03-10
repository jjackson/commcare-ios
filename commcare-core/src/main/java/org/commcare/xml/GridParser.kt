package org.commcare.xml

import org.commcare.suite.model.DetailField.Builder
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

/**
 * Parser used in DetailParser to parse the Grid attributes for a GridEntityView
 *
 * @author wspride
 */
class GridParser(
    val builder: Builder,
    parser: KXmlParser
) : ElementParser<Int>(parser) {

    @Throws(InvalidStructureException::class, IOException::class, XmlPullParserException::class)
    override fun parse(): Int {
        checkNode("grid")
        val gridx = parser.getAttributeValue(null, "grid-x")
        builder.setGridX(Integer.parseInt(gridx))

        val gridy = parser.getAttributeValue(null, "grid-y")
        builder.setGridY(Integer.parseInt(gridy))

        val gridw = parser.getAttributeValue(null, "grid-width")
        builder.setGridWidth(Integer.parseInt(gridw))

        val gridh = parser.getAttributeValue(null, "grid-height")
        builder.setGridHeight(Integer.parseInt(gridh))

        //exit grid block
        parser.nextTag()

        return 1
    }
}
