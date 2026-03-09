package org.javarosa.xform.parse

import org.javarosa.core.util.Interner
import java.io.Reader

/**
 * Class factory for creating an XFormParser.
 *
 * This factory allows you to provide a custom string cache
 * to be used during parsing, which should be helpful
 * in conserving memories in environments where there might be
 * multiple parsed forms in memory at the same time.
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

    open fun getXFormParser(reader: Reader): XFormParser {
        val parser = XFormParser(reader)

        if (stringCache != null) {
            parser.setStringCache(stringCache)
        }

        return parser
    }
}
