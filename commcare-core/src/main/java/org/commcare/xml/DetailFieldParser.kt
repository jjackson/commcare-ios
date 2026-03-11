package org.commcare.xml

import org.commcare.suite.model.DetailField
import org.commcare.suite.model.DetailTemplate
import org.commcare.suite.model.EndpointAction
import org.commcare.suite.model.Text
import org.javarosa.core.model.Constants
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Parser for <field> elements of a suite's detail definitions.
 * Contains text templates, as well as layout and sorting options
 */
class DetailFieldParser(
    parser: PlatformXmlParser,
    private val graphParser: GraphParser?,
    private val id: String?
) : CommCareElementParser<DetailField>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): DetailField {
        checkNode("field")
        val builder = DetailField.Builder()
        val cacheEnabled = java.lang.Boolean.parseBoolean(parser.getAttributeValue(null, "cache_enabled"))
        builder.setCacheEnabled(cacheEnabled)
        val lazyLoading = java.lang.Boolean.parseBoolean(parser.getAttributeValue(null, "lazy_loading"))
        builder.setLazyLoading(lazyLoading)

        val sortDefault = parser.getAttributeValue(null, "sort")
        if (sortDefault != null && sortDefault == "default") {
            builder.setSortOrder(1)
        }
        val relevancy = parser.getAttributeValue(null, "relevant")
        if (relevancy != null) {
            try {
                XPathParseTool.parseXPath(relevancy)
                builder.setRelevancy(relevancy)
            } catch (e: XPathSyntaxException) {
                e.printStackTrace()
                throw InvalidStructureException("Bad XPath Expression {$relevancy}", parser)
            }
        }
        val printId = parser.getAttributeValue(null, "print-id")
        if (printId != null) {
            builder.setPrintIdentifier(printId)
        }

        if (nextTagInBlock("field")) {
            parseStyle(builder)
            checkNode("header")

            builder.setHeaderWidthHint(parser.getAttributeValue(null, "width"))

            val form = parser.getAttributeValue(null, "form")
            builder.setHeaderForm(form ?: "")

            parser.nextTag()
            checkNode("text")
            val header = TextParser(parser).parse()
            builder.setHeader(header)
        } else {
            throw InvalidStructureException("Not enough field entries", parser)
        }
        if (nextTagInBlock("field")) {
            parseTemplate(builder)
        } else {
            throw InvalidStructureException("detail <field> with no <template>!", parser)
        }
        while (nextTagInBlock("field")) {
            //sort details
            checkNode(arrayOf("sort", "background", "endpoint_action", "alt_text"))

            val name = parser.getName()!!.lowercase()

            if (name == "sort") {
                parseSort(builder)
            } else if (name == "background") {
                // background tag in fields is deprecated
                skipBlock("background")
            } else if (name == "endpoint_action") {
                parseEndpointAction(builder)
            } else if (name == "alt_text") {
                parser.nextTag()
                checkNode("text")
                val altText = TextParser(parser).parse()
                builder.setAltText(altText)
            }
        }
        return builder.build()
    }

    @Throws(InvalidStructureException::class)
    private fun parseEndpointAction(builder: DetailField.Builder) {
        val id = parser.getAttributeValue(null, "endpoint_id")
            ?: throw InvalidStructureException(
                "No endpoint_id defined for endpoint_action for detail field ",
                parser
            )
        val background = parser.getAttributeValue(null, "background")
        val isBackground = "true" == background
        builder.setEndpointAction(EndpointAction(id, isBackground))
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun parseStyle(builder: DetailField.Builder) {
        //style
        if (parser.getName()!!.lowercase() == "style") {
            val styleParser = StyleParser(builder, parser)
            styleParser.parse()
            //Header
            val gridParser = GridParser(builder, parser)
            gridParser.parse()

            //exit style block
            parser.nextTag()
            parser.nextTag()
        }
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun parseTemplate(builder: DetailField.Builder) {
        //Template
        checkNode("template")

        builder.setTemplateWidthHint(parser.getAttributeValue(null, "width"))

        var form = parser.getAttributeValue(null, "form")
        if (form == null) {
            form = ""
        }
        builder.setTemplateForm(form)

        parser.nextTag()
        val template: DetailTemplate
        if (form == "graph") {
            template = graphParser!!.parse()
        } else if (form == "callout") {
            template = CalloutParser(parser).parse()
        } else {
            checkNode("text")
            try {
                template = TextParser(parser).parse()
            } catch (ise: InvalidStructureException) {
                throw InvalidStructureException(
                    "Error in suite detail with id $id : ${ise.message}", parser
                )
            }
        }
        builder.setTemplate(template)
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun parseSort(builder: DetailField.Builder) {
        val order = parser.getAttributeValue(null, "order")
        if (order != null && "" != order) {
            try {
                builder.setSortOrder(Integer.parseInt(order))
            } catch (nfe: NumberFormatException) {
                //see above comment
            }
        }
        val direction = parser.getAttributeValue(null, "direction")
        if ("ascending" == direction) {
            builder.setSortDirection(DetailField.DIRECTION_ASCENDING)
        } else if ("descending" == direction) {
            builder.setSortDirection(DetailField.DIRECTION_DESCENDING)
        }

        //See if there's a sort type
        val type = parser.getAttributeValue(null, "type")
        if ("int" == type) {
            builder.setSortType(Constants.DATATYPE_INTEGER)
        } else if ("double" == type) {
            builder.setSortType(Constants.DATATYPE_DECIMAL)
        } else if ("string" == type) {
            builder.setSortType(Constants.DATATYPE_TEXT)
        }

        parseBlanksPreference(builder, direction)

        //See if this has a text value for the sort
        if (nextTagInBlock("sort")) {
            //Make sure the internal element _is_ a text
            checkNode("text")

            //Get it if so
            val sort = TextParser(parser).parse()
            builder.setSort(sort)
        }
    }

    private fun parseBlanksPreference(builder: DetailField.Builder, direction: String?) {
        val blanksPreference = parser.getAttributeValue(null, "blanks")
        if ("last" == blanksPreference) {
            builder.setShowBlanksLast(true)
        } else if ("first" == blanksPreference) {
            builder.setShowBlanksLast(false)
        } else {
            builder.setShowBlanksLast("ascending" != direction)
        }
    }
}
