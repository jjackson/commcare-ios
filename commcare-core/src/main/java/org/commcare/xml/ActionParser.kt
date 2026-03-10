package org.commcare.xml

import org.commcare.suite.model.Action
import org.commcare.suite.model.DisplayUnit
import org.commcare.suite.model.StackOperation
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.Vector

/**
 * Parses case list actions, which when triggered manipulate the session stack
 *
 * @author ctsims
 */
class ActionParser(parser: KXmlParser) : CommCareElementParser<Action>(parser) {

    companion object {
        const val NAME_ACTION: String = "action"
    }

    @Throws(InvalidStructureException::class, IOException::class, XmlPullParserException::class)
    override fun parse(): Action {
        this.checkNode(NAME_ACTION)

        val iconForActionBarPlacement = parser.getAttributeValue(null, "action-bar-icon")

        var display: DisplayUnit? = null
        val stackOps = Vector<StackOperation>()

        val relevantExpr = parseRelevancyExpr()

        val autoLaunch = parser.getAttributeValue(null, "auto_launch")

        val derivedAutoLaunchExpression = deriveAutoLaunchExpression(autoLaunch)
        var autoLaunchExpr: XPathExpression? = null

        try {
            if (derivedAutoLaunchExpression != null) {
                autoLaunchExpr = XPathParseTool.parseXPath(derivedAutoLaunchExpression)
            }
        } catch (e: XPathSyntaxException) {
            val messageBase = "'autoLaunch' doesn't contain a valid xpath expression: "
            throw InvalidStructureException(messageBase + autoLaunch, parser)
        }

        val redoLast = "true" == parser.getAttributeValue(null, "redo_last")

        while (nextTagInBlock(NAME_ACTION)) {
            if (parser.name == "display") {
                display = parseDisplayBlock()
            } else if (parser.name == "stack") {
                val sop = StackOpParser(parser)
                while (this.nextTagInBlock(StackOpParser.NAME_STACK)) {
                    stackOps.addElement(sop.parse())
                }
            }
        }

        if (display == null) {
            throw InvalidStructureException("<action> block must define a <display> element", parser)
        }
        return Action(display, stackOps, relevantExpr, iconForActionBarPlacement, autoLaunchExpr, redoLast)
    }

    private fun deriveAutoLaunchExpression(autoLaunch: String?): String? {
        return if ("true".equals(autoLaunch, ignoreCase = true)) {
            "true()"
        } else if ("false".equals(autoLaunch, ignoreCase = true)) {
            "false()"
        } else {
            autoLaunch
        }
    }

    @Throws(InvalidStructureException::class)
    private fun parseRelevancyExpr(): XPathExpression? {
        val relevantExprString = parser.getAttributeValue(null, "relevant")
        return if (relevantExprString != null) {
            try {
                XPathParseTool.parseXPath(relevantExprString)
            } catch (e: XPathSyntaxException) {
                val messageBase = "'relevant' doesn't contain a valid xpath expression: "
                throw InvalidStructureException(messageBase + relevantExprString, parser)
            }
        } else {
            null
        }
    }
}
