package org.commcare.xml

import org.commcare.suite.model.DetailGroup
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException
import org.kxml2.io.KXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

class DetailGroupParser(parser: KXmlParser) : CommCareElementParser<DetailGroup>(parser) {

    companion object {
        const val NAME_GROUP: String = "group"
        const val ATTRIBUTE_NAME_FUNCTION: String = "function"
        const val ATTRIBUTE_NAME_HEADER_ROWS: String = "header-rows"
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): DetailGroup {
        checkNode(NAME_GROUP)
        val functionStr = parser.getAttributeValue(null, ATTRIBUTE_NAME_FUNCTION)
        val function: XPathExpression
        if (functionStr == null) {
            throw InvalidStructureException(
                "No function in detail group declaration ${parser.name}",
                parser
            )
        }
        try {
            function = XPathParseTool.parseXPath(functionStr)!!
        } catch (e: XPathSyntaxException) {
            e.printStackTrace()
            throw InvalidStructureException(
                "Invalid XPath function $functionStr. ${e.message}",
                parser
            )
        }
        var headerRowsStr = parser.getAttributeValue(null, ATTRIBUTE_NAME_HEADER_ROWS)
        if (headerRowsStr == null) {
            headerRowsStr = "1"
        }
        val headerRows: Int
        try {
            headerRows = Integer.parseInt(headerRowsStr)
        } catch (e: NumberFormatException) {
            throw InvalidStructureException(
                "non integer value for header-rows $headerRowsStr. ${e.message}", parser
            )
        }
        return DetailGroup(function, headerRows)
    }
}
