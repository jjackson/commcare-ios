package org.commcare.xml

import org.commcare.suite.model.DisplayUnit
import org.commcare.suite.model.GeoOverlay
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.kxml2.io.KXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Parses the defintion for a [GeoOverlay] element
 */
internal class GeoOverlayParser(parser: KXmlParser) : ElementParser<GeoOverlay>(parser) {

    companion object {
        @JvmField
        val NAME_GEO_OVERLAY: String = "geo-overlay"
        private const val NAME_COORDINATES = "coordinates"
        private const val NAME_LABEL = "label"
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): GeoOverlay {
        var title: DisplayUnit? = null
        var coordinates: DisplayUnit? = null
        while (nextTagInBlock(NAME_GEO_OVERLAY)) {
            val tagName = parser.name.lowercase()
            if (NAME_COORDINATES.contentEquals(tagName)) {
                nextTagInBlock(NAME_COORDINATES)
                coordinates = DisplayUnit(TextParser(parser).parse())
            } else if (NAME_LABEL.contentEquals(tagName)) {
                nextTagInBlock(NAME_LABEL)
                title = DisplayUnit(TextParser(parser).parse())
            }
        }
        return GeoOverlay(title, coordinates)
    }
}
