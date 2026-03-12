package org.commcare.xml

import org.commcare.resources.model.Resource
import org.commcare.resources.model.Resource.Companion.LAZY_VAL_FALSE
import org.commcare.resources.model.ResourceLocation
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

class ResourceParser(
    parser: PlatformXmlParser,
    val maximumAuthority: Int
) : ElementParser<Resource>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): Resource {
        checkNode("resource")

        val id = parser.getAttributeValue(null, "id")
        val version = parseInt(parser.getAttributeValue(null, "version"))

        val descriptor = parser.getAttributeValue(null, "descriptor")
        val lazy = parser.getAttributeValue(null, "lazy")

        val locations = ArrayList<ResourceLocation>()

        while (nextTagInBlock("resource")) {
            //New Location
            val sAuthority = parser.getAttributeValue(null, "authority")!!
            val location = parser.nextText()
            var authority = Resource.RESOURCE_AUTHORITY_REMOTE
            if (sAuthority.lowercase() == "local") {
                authority = Resource.RESOURCE_AUTHORITY_LOCAL
            } else if (sAuthority.lowercase() == "remote") {
                authority = Resource.RESOURCE_AUTHORITY_REMOTE
            }
            //Don't use any authorities which are outside of the scope of the maximum
            if (authority >= maximumAuthority) {
                locations.add(ResourceLocation(authority, location))
            }
        }

        return Resource(version, id!!, locations, descriptor, lazy ?: LAZY_VAL_FALSE)
    }
}
