package org.commcare.xml

import org.commcare.suite.model.Callout
import org.commcare.suite.model.DetailField
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.kxml2.io.KXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Hashtable
import java.util.Vector

/**
 * Parser used in DetailParser to parse the defintions of callouts used in
 * case select and detail views.
 *
 * @author wspride
 */
class CalloutParser(parser: KXmlParser) : ElementParser<Callout>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): Callout {
        val actionName = parser.getAttributeValue(null, "action")
        val image = parser.getAttributeValue(null, "image")
        val displayName = parser.getAttributeValue(null, "name")
        val type = parser.getAttributeValue(null, "type")
        val isAutoLaunching = "true" == parser.getAttributeValue(null, "auto_launch")

        val extras = Hashtable<String, String>()
        val responses = Vector<String>()
        var responseDetailField: DetailField? = null

        while (nextTagInBlock("lookup")) {
            val tagName = parser.name
            if ("extra" == tagName) {
                extras[parser.getAttributeValue(null, "key")] = parser.getAttributeValue(null, "value")
            } else if ("response" == tagName) {
                responses.addElement(parser.getAttributeValue(null, "key"))
            } else if ("field" == tagName) {
                responseDetailField = DetailFieldParser(parser, null, "'lookup callout detail field'").parse()
            }
        }
        return Callout(actionName, image, displayName, extras, responses, responseDetailField, type, isAutoLaunching)
    }
}
