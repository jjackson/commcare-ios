package org.commcare.xml

import org.commcare.suite.model.ListQueryData
import org.commcare.suite.model.QueryData
import org.commcare.suite.model.ValueQueryData
import org.javarosa.model.xform.XPathReference
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Parser for parsing `<data>` elements
 */
class QueryDataParser(parser: PlatformXmlParser) : CommCareElementParser<QueryData>(parser) {

    companion object {
        @JvmStatic
        @Throws(InvalidStructureException::class)
        fun buildQueryData(
            key: String?, ref: String, exclude: String?, nodeset: String?
        ): QueryData {
            var excludeExpr: XPathExpression? = null
            if (exclude != null) {
                excludeExpr = parseXpath(exclude)
            }

            if (nodeset != null) {
                return ListQueryData(
                    key,
                    XPathReference.getPathExpr(nodeset).getReference(),
                    excludeExpr,
                    XPathReference.getPathExpr(ref)
                )
            }
            return ValueQueryData(key, parseXpath(ref), excludeExpr)
        }

        @Throws(InvalidStructureException::class)
        private fun parseXpath(ref: String): XPathExpression {
            try {
                return XPathParseTool.parseXPath(ref)!!
            } catch (e: XPathSyntaxException) {
                val errorMessage = "'ref' value is not a valid xpath expression: $ref"
                throw InvalidStructureException(errorMessage)
            }
        }
    }

    @Throws(InvalidStructureException::class, PlatformXmlParserException::class, PlatformIOException::class)
    override fun parse(): QueryData {
        checkNode("data")

        val key = parser.getAttributeValue(null, "key")
        val ref = parser.getAttributeValue(null, "ref")
        val nodeset = parser.getAttributeValue(null, "nodeset")
        val exclude = parser.getAttributeValue(null, "exclude")

        if (nextTagInBlock("data")) {
            throw InvalidStructureException("<data> block does not support nested elements", parser)
        }

        if (ref == null) {
            throw InvalidStructureException("<data> block must define a 'ref' attribute", parser)
        }

        return buildQueryData(key, ref, exclude, nodeset)
    }
}
