package org.javarosa.xform.parse

import org.javarosa.core.util.Interner

/**
 * Class factory for creating an XFormParser.
 *
 * @author mitchellsundt@gmail.com / csims@dimagi.com
 */
open class XFormParserFactory {
    private val stringCache: Interner<String>?

    constructor() {
        stringCache = null
    }

    constructor(stringCache: Interner<String>?) {
        this.stringCache = stringCache
    }

    open fun getXFormParser(data: ByteArray): XFormParser {
        val parser = XFormParser(data)

        if (stringCache != null) {
            parser.setStringCache(stringCache)
        }

        return parser
    }
}
