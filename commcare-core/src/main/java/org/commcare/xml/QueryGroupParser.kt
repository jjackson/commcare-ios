package org.commcare.xml

import org.commcare.suite.model.DisplayUnit
import org.commcare.suite.model.QueryGroup
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import org.javarosa.core.util.externalizable.PlatformIOException

class QueryGroupParser(parser: KXmlParser) : CommCareElementParser<QueryGroup>(parser) {

    companion object {
        const val NAME_GROUP: String = "group"
        private const val ATTR_KEY = "key"
        private const val NAME_DISPLAY = "display"
    }

    @Throws(
        InvalidStructureException::class, PlatformIOException::class,
        XmlPullParserException::class, UnfullfilledRequirementsException::class
    )
    override fun parse(): QueryGroup {
        checkNode(NAME_GROUP)

        val key = parser.getAttributeValue(null, ATTR_KEY)
        var display: DisplayUnit? = null

        while (nextTagInBlock(NAME_GROUP)) {
            if (NAME_DISPLAY.equals(parser.name, ignoreCase = true)) {
                display = parseDisplayBlock()
            } else {
                throw InvalidStructureException(
                    "Unrecognised node ${parser.name}in validation for group $key"
                )
            }
        }

        if (key == null) {
            throw InvalidStructureException("<group> block must define a 'key' attribute", parser)
        }
        if (display == null) {
            throw InvalidStructureException("<group> block must define a <display> element", parser)
        }

        return QueryGroup(key, display)
    }
}
