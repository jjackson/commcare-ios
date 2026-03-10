package org.commcare.xml

import org.commcare.resources.model.Resource
import org.commcare.resources.model.Resource.Companion.LAZY_VAL_FALSE
import org.commcare.resources.model.ResourceLocation
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Vector

class ResourceParser(
    parser: KXmlParser,
    val maximumAuthority: Int
) : ElementParser<Resource>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, XmlPullParserException::class)
    override fun parse(): Resource {
        checkNode("resource")

        val id = parser.getAttributeValue(null, "id")
        val version = parseInt(parser.getAttributeValue(null, "version"))

        val descriptor = parser.getAttributeValue(null, "descriptor")
        val lazy = parser.getAttributeValue(null, "lazy")

        val locations = Vector<ResourceLocation>()

        while (nextTagInBlock("resource")) {
            //New Location
            val sAuthority = parser.getAttributeValue(null, "authority")
            val location = parser.nextText()
            var authority = Resource.RESOURCE_AUTHORITY_REMOTE
            if (sAuthority.lowercase() == "local") {
                authority = Resource.RESOURCE_AUTHORITY_LOCAL
            } else if (sAuthority.lowercase() == "remote") {
                authority = Resource.RESOURCE_AUTHORITY_REMOTE
            }
            //Don't use any authorities which are outside of the scope of the maximum
            if (authority >= maximumAuthority) {
                locations.addElement(ResourceLocation(authority, location))
            }
        }

        return Resource(version, id, locations, descriptor, lazy ?: LAZY_VAL_FALSE)
    }
}
