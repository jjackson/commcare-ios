package org.commcare.xml

import org.commcare.suite.model.Action
import org.commcare.suite.model.Callout
import org.commcare.suite.model.Detail
import org.commcare.suite.model.DetailField
import org.commcare.suite.model.DetailGroup
import org.commcare.suite.model.DisplayUnit
import org.commcare.suite.model.Global
import org.commcare.suite.model.Text
import org.javarosa.core.util.OrderedHashtable
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * @author ctsims
 */
open class DetailParser(parser: PlatformXmlParser) : CommCareElementParser<Detail>(parser) {

    companion object {
        private const val NAME_NO_ITEMS_TEXT = "no_items_text"
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): Detail {
        checkNode("detail")

        val id = parser.getAttributeValue(null, "id")
        val nodeset = parser.getAttributeValue(null, "nodeset")
        val fitAcross = parser.getAttributeValue(null, "fit-across")
        val useUniformUnits = parser.getAttributeValue(null, "uniform-units")
        val forceLandscapeView = parser.getAttributeValue(null, "force-landscape")
        val printTemplatePath = parser.getAttributeValue(null, "print-template")
        val relevancy = parser.getAttributeValue(null, "relevant")
        val cacheEnabled = java.lang.Boolean.parseBoolean(parser.getAttributeValue(null, "cache_enabled"))
        val lazyLoading = java.lang.Boolean.parseBoolean(parser.getAttributeValue(null, "lazy_loading"))

        // First fetch the title
        getNextTagInBlock("detail")
        // inside title, should be a text node or a display node as the child
        checkNode("title")
        getNextTagInBlock("title")
        val title: DisplayUnit

        if ("text" == parser.getName()!!.lowercase()) {
            title = DisplayUnit(TextParser(parser).parse())
        } else {
            title = parseDisplayBlock()
        }

        var global: Global? = null
        var callout: Callout? = null
        val actions = ArrayList<Action>()

        //Now get the headers and templates.
        var noItemsText: Text? = null
        var selectText: Text? = null
        val subdetails = ArrayList<Detail>()
        val fields = ArrayList<DetailField>()
        val variables = OrderedHashtable<String, String>()
        var focusFunction: String? = null
        var detailGroup: DetailGroup? = null

        while (nextTagInBlock("detail")) {
            if (GlobalParser.NAME_GLOBAL == parser.getName()!!.lowercase()) {
                checkNode(GlobalParser.NAME_GLOBAL)
                global = GlobalParser(parser).parse()
                parser.nextTag()
            }
            if ("lookup" == parser.getName()!!.lowercase()) {
                checkNode("lookup")
                callout = CalloutParser(parser).parse()
                parser.nextTag()
            }
            if (NAME_NO_ITEMS_TEXT == parser.getName()!!.lowercase()) {
                checkNode("no_items_text")
                getNextTagInBlock("no_items_text")
                if ("text" == parser.getName()!!.lowercase()) {
                    noItemsText = TextParser(parser).parse()
                }
                continue
            }
            if ("select_text" == parser.getName()!!.lowercase()) {
                checkNode("select_text")
                getNextTagInBlock("select_text")
                if ("text" == parser.getName()!!.lowercase()) {
                    selectText = TextParser(parser).parse()
                }
                continue
            }
            if ("variables" == parser.getName()!!.lowercase()) {
                while (nextTagInBlock("variables")) {
                    val function = parser.getAttributeValue(null, "function")
                        ?: throw InvalidStructureException(
                            "No function in variable declaration for variable ${parser.getName()}", parser
                        )
                    try {
                        XPathParseTool.parseXPath(function)
                    } catch (e: XPathSyntaxException) {
                        e.printStackTrace()
                        throw InvalidStructureException(
                            "Invalid XPath function $function. ${e.message}", parser
                        )
                    }
                    variables[parser.getName()!!] = function
                }
                continue
            }
            if ("focus" == parser.getName()!!.lowercase()) {
                focusFunction = parser.getAttributeValue(null, "function")
                if (focusFunction == null) {
                    throw InvalidStructureException(
                        "No function in focus declaration ${parser.getName()}", parser
                    )
                }
                try {
                    XPathParseTool.parseXPath(focusFunction)
                } catch (e: XPathSyntaxException) {
                    e.printStackTrace()
                    throw InvalidStructureException(
                        "Invalid XPath function $focusFunction. ${e.message}", parser
                    )
                }
                continue
            }
            if (ActionParser.NAME_ACTION.equals(parser.getName(), ignoreCase = true)) {
                actions.add(ActionParser(parser).parse())
                continue
            }
            if (DetailGroupParser.NAME_GROUP.equals(parser.getName(), ignoreCase = true)) {
                detailGroup = DetailGroupParser(parser).parse()
                continue
            }
            if (parser.getName() == "detail") {
                subdetails.add(getDetailParser().parse())
            } else {
                val detailField = DetailFieldParser(parser, getGraphParser(), id).parse()
                fields.add(detailField)
            }
        }

        return Detail(
            id, title, noItemsText, nodeset, subdetails, fields, variables, actions, callout,
            fitAcross, useUniformUnits, forceLandscapeView, focusFunction, printTemplatePath,
            relevancy, global, detailGroup, lazyLoading, cacheEnabled, selectText
        )
    }

    protected open fun getDetailParser(): DetailParser {
        return DetailParser(parser)
    }

    protected open fun getGraphParser(): GraphParser {
        return GraphParser(parser)
    }
}
