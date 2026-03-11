package org.javarosa.xml.util

import org.javarosa.xml.PlatformXmlParser

/**
 * Invalid Structure Exceptions are thrown when an invalid
 * definition is found while parsing XML defining CommCare Models.
 *
 * @author ctsims
 */
open class InvalidStructureException : Exception {
    /**
     * @param message A Message associated with the error.
     * @param parser  The parser in the position at which the error was detected.
     */
    constructor(message: String, parser: PlatformXmlParser) :
        super("Invalid XML Structure(${parser.getPositionDescription()}): $message")

    /**
     * @param message A Message associated with the error.
     * @param parser  The parser in the position at which the error was detected.
     * @param file    The file being parsed
     */
    constructor(message: String, file: String, parser: PlatformXmlParser) :
        super("Invalid XML Structure in document $file(${parser.getPositionDescription()}): $message")

    constructor(message: String) : super(message)

    companion object {
        @JvmStatic
        fun readableInvalidStructureException(
            message: String,
            parser: PlatformXmlParser
        ): InvalidStructureException {
            val humanReadableMessage = message + buildParserMessage(parser)
            return InvalidStructureException(humanReadableMessage)
        }

        private fun buildParserMessage(parser: PlatformXmlParser): String {
            val prefix = parser.getPrefix()
            return if (prefix != null) {
                ". Source: <$prefix:${parser.getName()}> tag in namespace: ${parser.getNamespace()}"
            } else {
                ". Source: <${parser.getName()}>"
            }
        }
    }
}
