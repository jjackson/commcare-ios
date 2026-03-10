package org.commcare.xml

import org.javarosa.core.reference.RootTranslator
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.xml.PlatformXmlParser

/**
 * @author ctsims
 */
class RootParser(parser: PlatformXmlParser) : ElementParser<RootTranslator>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): RootTranslator {
        this.checkNode("root")

        val id = parser.getAttributeValue(null, "prefix")
        val readonly = parser.getAttributeValue(null, "readonly")

        //Get the child or error out if none exists
        getNextTagInBlock("root")

        val referenceType = parser.name!!.lowercase()
        val path = parser.getAttributeValue(null, "path")
        return when (referenceType) {
            "filesystem" -> RootTranslator("jr://$id/", "jr://file$path")
            "resource" -> RootTranslator("jr://$id/", "jr://resource$path")
            "absolute" -> RootTranslator("jr://$id/", path)
            else -> throw InvalidStructureException(
                "No available reference types to parse out reference root $referenceType", parser
            )
        }
    }
}
