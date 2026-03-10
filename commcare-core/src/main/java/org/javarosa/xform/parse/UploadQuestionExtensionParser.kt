package org.javarosa.xform.parse

import org.javarosa.core.model.QuestionDataExtension
import org.javarosa.core.model.UploadQuestionExtension
import org.javarosa.xml.dom.XmlElement

/**
 * An additional parser for the "upload" question type, which can be used to parse any additional
 * attributes included in an upload question.
 *
 * @author amstone
 */
class UploadQuestionExtensionParser : QuestionExtensionParser() {

    init {
        setElementName("upload")
    }

    override fun parse(elt: XmlElement): QuestionDataExtension? {
        var s = elt.getAttributeValue(XFormParser.NAMESPACE_JAVAROSA,
                "imageDimensionScaledMax")
        if (s != null) {
            if (s.endsWith("px")) {
                s = s.substring(0, s.length - 2)
            }
            try {
                val maxDimens = s.toInt()
                return UploadQuestionExtension(maxDimens)
            } catch (e: NumberFormatException) {
                throw XFormParseException("Invalid input for image max dimension: $s")
            }
        }
        return null
    }

    override fun getUsedAttributes(): Array<String> {
        return arrayOf("imageDimensionScaledMax")
    }
}
