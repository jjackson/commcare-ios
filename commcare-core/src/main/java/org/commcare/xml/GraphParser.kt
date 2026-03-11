package org.commcare.xml

import org.commcare.core.graph.suite.Annotation
import org.commcare.core.graph.suite.BubbleSeries
import org.commcare.core.graph.suite.Configurable
import org.commcare.core.graph.suite.Graph
import org.commcare.core.graph.suite.XYSeries
import org.commcare.core.graph.util.GraphUtil
import org.commcare.suite.model.DetailTemplate
import org.commcare.suite.model.Text
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.parser.XPathSyntaxException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Created by jschweers on 1/28/2016.
 */
open class GraphParser(parser: PlatformXmlParser) : ElementParser<DetailTemplate>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    override fun parse(): Graph {
        val graph = Graph()
        val type = parser.getAttributeValue(null, "type")
            ?: throw InvalidStructureException(
                "Expected attribute @type for element <${parser.name}>", parser
            )
        graph.setType(type)

        val entryLevel = parser.depth
        do {
            // <graph> contains an optional <configuration>, 0 to many <series>,
            // and 0 to many <annotation>, in any order.
            parser.nextTag()
            if (parser.name == "configuration") {
                parseConfiguration(graph)
            }
            if (parser.name == "series") {
                graph.addSeries(parseSeries(type))
            }
            if (parser.name == "annotation") {
                parseAnnotation(graph)
            }
        } while (parser.depth > entryLevel)

        return graph
    }

    /*
     * Helper for parse; handles a single annotation, which must contain an x
     * (which contains a single <text>), y (also contains a single <text>),
     * and then another <text> for the annotation's actual text.
     */
    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun parseAnnotation(graph: Graph) {
        checkNode("annotation")

        val textParser = TextParser(parser)

        nextStartTag()
        checkNode("x")
        nextStartTag()
        val x = textParser.parse()

        nextStartTag()
        checkNode("y")
        nextStartTag()
        val y = textParser.parse()

        nextStartTag()
        val text = textParser.parse()

        parser.nextTag()

        graph.addAnnotation(Annotation(x, y, text))
    }

    /*
     * Helper for parse; handles a configuration element, which is a set of <text> elements, each with an id.
     */
    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun parseConfiguration(data: Configurable) {
        checkNode("configuration")

        val textParser = TextParser(parser)
        do {
            parser.nextTag()
            if (parser.name == "text") {
                val id = parser.getAttributeValue(null, "id")
                val t = textParser.parse()
                data.setConfiguration(id!!, t)
            }
        } while (parser.eventType != PlatformXmlParser.END_TAG || parser.name != "configuration")
    }

    /*
     * Helper for parse; handles a single series, which is an optional <configuration> followed by an <x>, a <y>,
     * and, if this graph is a bubble graph, a <radius>.
     */
    @Throws(InvalidStructureException::class, PlatformIOException::class, PlatformXmlParserException::class)
    private fun parseSeries(type: String): XYSeries {
        checkNode("series")
        val nodeSet = parser.getAttributeValue(null, "nodeset")
        val series = if (type == GraphUtil.TYPE_BUBBLE) BubbleSeries(nodeSet) else XYSeries(nodeSet)

        nextStartTag()
        if (parser.name == "configuration") {
            parseConfiguration(series)
            nextStartTag()
        }

        checkNode("x")
        series.setX(parseFunction("x"))

        nextStartTag()
        series.setY(parseFunction("y"))

        if (type == GraphUtil.TYPE_BUBBLE) {
            nextStartTag()
            checkNode("radius")
            (series as BubbleSeries).setRadius(parseFunction("radius"))
        }

        while (parser.eventType != PlatformXmlParser.END_TAG || parser.name != "series") {
            parser.nextTag()
        }

        return series
    }

    /**
     * Get an XPath function from a node and attempt to parse it.
     *
     * @param name Node name, also used in any error message.
     * @return String representation of the XPath function.
     * @throws InvalidStructureException
     */
    @Throws(InvalidStructureException::class)
    private fun parseFunction(name: String): String {
        checkNode(name)
        val function = parser.getAttributeValue(null, "function")!!
        try {
            XPathParseTool.parseXPath(function)
        } catch (e: XPathSyntaxException) {
            throw InvalidStructureException(
                "Invalid $name function in graph: $function. ${e.message}", parser
            )
        }
        return function
    }

    /*
     * Move parser along until it hits a start tag.
     */
    @Throws(PlatformIOException::class, PlatformXmlParserException::class)
    private fun nextStartTag() {
        do {
            parser.nextTag()
        } while (parser.eventType != PlatformXmlParser.START_TAG)
    }
}
