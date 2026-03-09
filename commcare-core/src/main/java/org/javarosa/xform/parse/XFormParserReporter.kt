package org.javarosa.xform.parse

import java.io.PrintStream

/**
 * A Parser Reporter is provided to the XFormParser to receive
 * warnings and errors from the parser.
 *
 * @author ctsims
 */
open class XFormParserReporter @JvmOverloads constructor(
    private val errorStream: PrintStream = System.err
) {

    companion object {
        const val TYPE_UNKNOWN_MARKUP: String = "markup"
        const val TYPE_INVALID_STRUCTURE: String = "invalid-structure"
        const val TYPE_ERROR_PRONE: String = "dangerous"
        const val TYPE_TECHNICAL: String = "technical"
        internal const val TYPE_ERROR: String = "error"
    }

    open fun warning(type: String, message: String, xmlLocation: String?) {
        errorStream.println("XForm Parse Warning: " + message + (if (xmlLocation == null) "" else xmlLocation))
    }

    open fun error(message: String) {
        errorStream.println("XForm Parse Error: " + message)
    }
}
