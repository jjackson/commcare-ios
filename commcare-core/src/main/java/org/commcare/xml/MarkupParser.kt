package org.commcare.xml

import org.commcare.suite.model.DetailField.Builder
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.xml.PlatformXmlParser

class MarkupParser(
    val builder: Builder,
    parser: PlatformXmlParser
) : ElementParser<Int>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
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
