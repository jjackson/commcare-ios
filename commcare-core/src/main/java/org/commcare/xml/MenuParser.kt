package org.commcare.xml

import org.commcare.suite.model.AssertionSet
import org.commcare.suite.model.DisplayUnit
import org.commcare.suite.model.Menu
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Hashtable
import java.util.Vector

/**
 * @author ctsims
 */
class MenuParser(parser: KXmlParser) : CommCareElementParser<Menu>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, XmlPullParserException::class)
    override fun parse(): Menu {
        checkNode("menu")

        val id = parser.getAttributeValue(null, "id")
        var root = parser.getAttributeValue(null, "root")
        root = root ?: "root"

        val instances = Hashtable<String, DataInstance<*>>()

        val relevant = parser.getAttributeValue(null, "relevant")
        var relevantExpression: XPathExpression? = null
        if (relevant != null) {
            try {
                relevantExpression = XPathParseTool.parseXPath(relevant)
            } catch (e: XPathSyntaxException) {
                e.printStackTrace()
                throw InvalidStructureException("Bad module filtering expression {$relevant}", parser)
            }
        }
        var assertions: AssertionSet? = null

        val style = parser.getAttributeValue(null, "style")

        getNextTagInBlock("menu")

        var display: DisplayUnit? = null
        if (parser.name == "text") {
            display = DisplayUnit(TextParser(parser).parse())
        } else if (parser.name == "display") {
            display = parseDisplayBlock()
            //check that we have a commandText;
            if (display.getText() == null)
                throw InvalidStructureException("Expected Menu Text in Display block", parser)
        } else {
            throw InvalidStructureException("Expected either <text> or <display> in menu", parser)
        }

        val commandIds = Vector<String>()
        val relevantExprs = Vector<String?>()
        while (nextTagInBlock("menu")) {
            val tagName = parser.name
            if (tagName == "command") {
                commandIds.addElement(parser.getAttributeValue(null, "id"))
                val relevantExpr = parser.getAttributeValue(null, "relevant")
                if (relevantExpr == null) {
                    relevantExprs.addElement(null)
                } else {
                    try {
                        //Safety checking
                        XPathParseTool.parseXPath(relevantExpr)
                        relevantExprs.addElement(relevantExpr)
                    } catch (e: XPathSyntaxException) {
                        e.printStackTrace()
                        throw InvalidStructureException("Bad XPath Expression {$relevantExpr}", parser)
                    }
                }
            } else if (tagName.lowercase() == "instance") {
                ParseInstance.parseInstance(instances, parser)
            } else if (tagName == "assertions") {
                try {
                    assertions = AssertionSetParser(parser).parse()
                } catch (e: InvalidStructureException) {
                    e.printStackTrace()
                    throw InvalidStructureException(e.message, parser)
                }
            }
        }

        val expressions = arrayOfNulls<String>(relevantExprs.size)
        relevantExprs.copyInto(expressions)

        return Menu(
            id, root, relevant, relevantExpression, display, commandIds, expressions,
            style, assertions, instances
        )
    }
}
