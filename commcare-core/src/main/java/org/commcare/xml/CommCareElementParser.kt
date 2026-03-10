package org.commcare.xml

import org.commcare.suite.model.DisplayUnit
import org.commcare.suite.model.Text
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

/**
 * Element parser extended with parsing function(s) that create CommCare
 * specific model objects.
 *
 * @author Phillip Mates
 */
abstract class CommCareElementParser<T>(parser: KXmlParser) : ElementParser<T>(parser) {

    /**
     * Build a DisplayUnit object by parsing the contents of a display tag.
     */
    @Throws(InvalidStructureException::class, IOException::class, XmlPullParserException::class)
    fun parseDisplayBlock(): DisplayUnit {
        var imageValue: Text? = null
        var audioValue: Text? = null
        var displayText: Text? = null
        var badgeText: Text? = null
        var hintText: Text? = null

        while (nextTagInBlock("display")) {
            if (parser.name == "text") {
                val attributeValue = parser.getAttributeValue(null, "form")
                if ("image" == attributeValue) {
                    imageValue = TextParser(parser).parse()
                } else if ("audio" == attributeValue) {
                    audioValue = TextParser(parser).parse()
                } else if ("badge" == attributeValue) {
                    badgeText = TextParser(parser).parse()
                } else {
                    displayText = TextParser(parser).parse()
                }
            } else if ("media" == parser.name) {
                val imagePath = parser.getAttributeValue(null, "image")
                if (imagePath != null) {
                    imageValue = Text.PlainText(imagePath)
                }

                val audioPath = parser.getAttributeValue(null, "audio")
                if (audioPath != null) {
                    audioValue = Text.PlainText(audioPath)
                }
                //only ends up grabbing the last entries with
                //each attribute, but we can only use one of each anyway.
            } else if ("hint" == parser.name) {
                while (nextTagInBlock("hint")) {
                    hintText = TextParser(parser).parse()
                }
            }
        }

        return DisplayUnit(displayText, imageValue, audioValue, badgeText, hintText)
    }
}
