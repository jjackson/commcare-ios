package org.javarosa.xform.parse

import org.javarosa.core.model.QuestionDataExtension
import org.javarosa.xml.dom.XmlElement

/**
 * A parser for some additional piece of information included with a question in an xform that
 * is not handled by the question type itself.
 *
 * @author amstone
 */
abstract class QuestionExtensionParser {

    private var _elementName: String? = null

    fun getElementName(): String? = _elementName

    fun setElementName(elementName: String?) {
        this._elementName = elementName
    }

    fun canParse(e: XmlElement): Boolean {
        return e.name == _elementName
    }

    abstract fun parse(e: XmlElement): QuestionDataExtension?

    abstract fun getUsedAttributes(): Array<String>
}
