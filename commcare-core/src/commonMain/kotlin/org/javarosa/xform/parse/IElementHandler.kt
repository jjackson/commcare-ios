package org.javarosa.xform.parse

import org.javarosa.xml.dom.XmlElement

/**
 * An IElementHandler is responsible for handling the parsing of a particular
 * XForms node.
 *
 * @author Drew Roos
 */
fun interface IElementHandler {
    fun handle(p: XFormParser, e: XmlElement, parent: Any)
}
