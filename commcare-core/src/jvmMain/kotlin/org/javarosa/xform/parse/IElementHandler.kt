package org.javarosa.xform.parse

import org.kxml2.kdom.Element

/**
 * An IElementHandler is responsible for handling the parsing of a particular
 * XForms node.
 *
 * @author Drew Roos
 */
fun interface IElementHandler {
    fun handle(p: XFormParser, e: Element, parent: Any)
}
