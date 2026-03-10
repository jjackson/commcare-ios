package org.commcare.xml

import org.commcare.suite.model.DetailField.Builder
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class MarkupParser(
    val builder: Builder,
    parser: KXmlParser
) : ElementParser<Int>(parser) {

    @Throws(InvalidStructureException::class, IOException::class, XmlPullParserException::class)
    override fun parse(): Int {
        parser.nextTag()

        checkNode("css")
        val id = parser.getAttributeValue(null, "id")
        builder.setCssID(id)

        //exit grid block
        parser.nextTag()

        return 1
    }
}
