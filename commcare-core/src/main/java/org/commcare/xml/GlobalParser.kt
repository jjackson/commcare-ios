package org.commcare.xml

import org.commcare.suite.model.GeoOverlay
import org.commcare.suite.model.Global
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.kxml2.io.KXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Vector

/**
 * Parser used in DetailParser to parse the defintion of Global element used in case-select and case-detail views
 */
internal class GlobalParser(parser: KXmlParser) : ElementParser<Global>(parser) {

    companion object {
        @JvmField
        val NAME_GLOBAL: String = "global"
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): Global {
        val geoOverlays = Vector<GeoOverlay>()
        while (nextTagInBlock(NAME_GLOBAL)) {
            if (GeoOverlayParser.NAME_GEO_OVERLAY == parser.name.lowercase()) {
                val geoOverlay = GeoOverlayParser(parser).parse()
                geoOverlays.add(geoOverlay)
            }
        }
        return Global(geoOverlays)
    }
}
