package org.commcare.xml

import org.commcare.suite.model.Text
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xpath.parser.XPathSyntaxException
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Hashtable
import java.util.Vector

class TextParser(parser: KXmlParser) : ElementParser<Text>(parser) {

    @Throws(InvalidStructureException::class, PlatformIOException::class, XmlPullParserException::class)
    override fun parse(): Text {
        val texts = Vector<Text>()

        checkNode("text")
        val entryLevel = parser.depth
        try {
            parser.next()
        } catch (e: XmlPullParserException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: PlatformIOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }

        while (parser.depth > entryLevel || parser.eventType == KXmlParser.TEXT) {
            val t = parseBody()
            if (t != null) {
                texts.addElement(t)
            }
        }
        return if (texts.size == 1) {
            texts.elementAt(0)
        } else {
            Text.CompositeText(texts)
        }
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, XmlPullParserException::class)
    internal fun parseBody(): Text? {
        //TODO: Should prevent compositing text and xpath/locales
        val texts = Vector<Text>()

        var eventType = parser.eventType
        var text = ""
        do {
            if (eventType == KXmlParser.START_TAG) {
                //If we were parsing text, commit that up first.
                if (text.trim() != "") {
                    val t = Text.PlainText(text)
                    texts.addElement(t)
                    text = ""
                }

                //now parse out the next tag.
                if (parser.name.lowercase() == "xpath") {
                    val xpathText = parseXPath()
                    texts.addElement(xpathText)
                } else if (parser.name.lowercase() == "locale") {
                    val localeText = parseLocale()
                    texts.addElement(localeText)
                }
            } else if (eventType == KXmlParser.TEXT) {
                text += parser.text.trim()
            }

            //We shouldn't really ever get here as far as things are currently set up
            eventType = parser.next()
            //How do we get out of here? Depth?
        } while (eventType != KXmlParser.END_TAG)

        if (text.trim() != "") {
            val t = Text.PlainText(text)
            texts.addElement(t)
        }
        if (texts.size == 0) {
            return null
        }

        return if (texts.size == 1) {
            texts.elementAt(0)
        } else {
            Text.CompositeText(texts)
        }
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, XmlPullParserException::class)
    private fun parseLocale(): Text {
        checkNode("locale")
        val id = parser.getAttributeValue(null, "id")

        val arguments = Hashtable<String, Text>()

        if (id == null) {
            //Get ID Node, throw exception if there isn't a tag
            getNextTagInBlock("locale")
            checkNode("id")
            val idText = TextParser(parser).parseBody()

            arguments["id"] = idText
        }

        var count = 0
        while (nextTagInBlock("locale")) {
            checkNode("argument")
            val argumentText = TextParser(parser).parseBody()

            arguments[count.toString()] = argumentText
            count++
        }
        return if (id == null) {
            Text.LocaleText(arguments)
        } else {
            Text.LocaleText(id, arguments)
        }
    }

    @Throws(InvalidStructureException::class, PlatformIOException::class, XmlPullParserException::class)
    private fun parseXPath(): Text {
        checkNode("xpath")
        val function = parser.getAttributeValue(null, "function")
        val arguments = Hashtable<String, Text>()

        //Now get all of the variables which might be used
        while (nextTagInBlock("xpath")) {
            checkNode("variable")
            val name = parser.getAttributeValue(null, "name")
            val variableText = TextParser(parser).parseBody()
            arguments[name] = variableText
        }
        try {
            return Text.XPathText(function, arguments)
        } catch (e: XPathSyntaxException) {
            e.printStackTrace()
            throw InvalidStructureException(
                "Invalid XPath Expression : $function. Parse error: ${e.message}", parser
            )
        }
    }
}
