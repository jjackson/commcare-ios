package org.javarosa.xml

import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.services.Logger
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import java.io.IOException
import java.io.InputStream
import org.javarosa.core.model.utils.PlatformDate
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Element Parser is the core parsing element for XML files. Implementations
 * can be made for data types, encapsulating all of the parsing rules for that
 * type's XML definition.
 *
 * An Element parser should have a defined scope of a single tag and its
 * descendants in the document. The ElementParser should receive the parser
 * pointing to that opening tag, and return it on the closing tag.
 *
 * A number of helper methods are provided in the parser which are intended
 * to standardize the techniques used for validation and pull-parsing through
 * the XML Document.
 *
 * @author ctsims
 */
abstract class ElementParser<T>(@JvmField protected val parser: PlatformXmlParser) {

    private val level: Int = parser.getDepth()

    /**
     * Evaluates whether the current node is the appropriate name
     * and throws the proper exception if not.
     */
    @Throws(InvalidStructureException::class)
    protected fun checkNode(name: String) {
        checkNode(arrayOf(name))
    }

    /**
     * Evaluates whether the current node is of an appropriate name
     * and throws the proper exception if not.
     */
    @Throws(InvalidStructureException::class)
    protected fun checkNode(names: Array<String>) {
        var checksOut = false

        if (parser.getName() != null) {
            for (name in names) {
                if (isTagNamed(name)) {
                    checksOut = true
                }
            }
        }
        if (!checksOut) {
            var eventType = -1
            try {
                eventType = parser.getEventType()
            } catch (e: Exception) {
                // This event type is just here to help elaborate on the exception
                // so don't crash on it
            }

            val oneOf = if (names.size == 1) {
                "<${names[0]}> "
            } else {
                val sb = StringBuilder("one of [")
                for (name in names) {
                    sb.append("<$name> ")
                }
                sb.append("]")
                sb.toString()
            }

            val foundInstead = when (eventType) {
                PlatformXmlParser.END_TAG -> "Closing tag </${parser.getName()}>"
                PlatformXmlParser.START_TAG -> "Element <${parser.getName()}>"
                PlatformXmlParser.TEXT -> "Text \"${parser.getText()}\""
                else -> "Unknown"
            }

            throw InvalidStructureException("Expected $oneOf$foundInstead found instead", parser)
        }
    }

    /**
     * Retrieves the next tag in the XML document which is internal
     * to the tag identified by terminal. If there are no further tags
     * inside this tag, an invalid structure is detected and the proper
     * exception is thrown.
     */
    @Throws(InvalidStructureException::class, IOException::class, PlatformXmlParserException::class)
    protected fun getNextTagInBlock(terminal: String) {
        if (!nextTagInBlock(terminal)) {
            throw InvalidStructureException(
                "Expected another node inside of element <$terminal>.", parser
            )
        }
    }

    /**
     * Retrieves the next tag in the XML document which is internal
     * to the tag identified by terminal. If there are no further tags
     * inside this tag, false will be returned.
     */
    @Throws(InvalidStructureException::class, IOException::class, PlatformXmlParserException::class)
    protected open fun nextTagInBlock(terminal: String?): Boolean {
        var eventType = parser.next()
        while (eventType == PlatformXmlParser.TEXT && parser.isWhitespace()) {
            eventType = parser.next()
        }

        if (eventType == PlatformXmlParser.START_DOCUMENT) {
            // continue
        } else if (eventType == PlatformXmlParser.END_DOCUMENT) {
            return false
        } else if (eventType == PlatformXmlParser.START_TAG) {
            return true
        } else if (eventType == PlatformXmlParser.END_TAG) {
            if (isTagNamed(terminal)) {
                return false
            } else if (parser.getDepth() >= level) {
                return nextTagInBlock(terminal)
            } else {
                return false
            }
        } else if (eventType == PlatformXmlParser.TEXT) {
            return true
        }
        return true
    }

    /**
     * Retrieves the next tag in the XML document, assuming
     * that it is named the same as the provided parameter.
     */
    @Throws(InvalidStructureException::class, IOException::class, PlatformXmlParserException::class)
    protected fun nextTag(name: String) {
        val depth = parser.getDepth()
        if (nextTagInBlock(null)) {
            if (parser.getDepth() == depth || parser.getDepth() == depth + 1) {
                if (isTagNamed(name)) {
                    return
                }
                throw InvalidStructureException(
                    "Expected tag $name but got tag: ${parser.getName()}", parser
                )
            }
            throw InvalidStructureException(
                "Expected tag $name but reached end of block instead", parser
            )
        }
        throw InvalidStructureException("Expected tag $name but it wasn't found", parser)
    }

    /**
     * Retrieves the next tag in the XML document. If there are no further
     * tags in the document, false is returned.
     */
    @Throws(InvalidStructureException::class, IOException::class, PlatformXmlParserException::class)
    protected fun nextTagInBlock(): Boolean {
        return nextTagInBlock(null)
    }

    /**
     * Takes in a string which contains an integer value and returns the
     * integer which it represents.
     */
    @Throws(InvalidStructureException::class)
    protected fun parseInt(value: String?): Int {
        if (value == null) {
            throw InvalidStructureException.readableInvalidStructureException(
                "Expected an integer value, found null text instead", parser
            )
        }
        try {
            return Integer.parseInt(value)
        } catch (nfe: NumberFormatException) {
            throw InvalidStructureException.readableInvalidStructureException(
                "Expected an integer value, found \"$value\" instead", parser
            )
        }
    }

    /**
     * Takes a string which is either null or represents a date, and
     * returns a valid date, or null (if tolerated).
     */
    @Throws(InvalidStructureException::class)
    protected fun getDateAttribute(attributeName: String, nullOk: Boolean): PlatformDate? {
        val dateValue = parser.getAttributeValue(null, attributeName)
        if (dateValue == null && !nullOk) {
            throw InvalidStructureException(
                "Expected attribute @$attributeName in element <${parser.getName()}>", parser
            )
        }
        try {
            return parseDateTime(dateValue)
        } catch (e: Exception) {
            throw InvalidStructureException(
                "Invalid date $dateValue in attribute @$attributeName for element <${parser.getName()}>",
                parser
            )
        }
    }

    protected open fun parseDateTime(dateValue: String?): PlatformDate? {
        if (dateValue == null) return null
        return DateUtils.parseDateTime(dateValue)
    }

    /**
     * Parses the XML document at the current level, returning the datatype
     * described by the document.
     */
    @Throws(
        InvalidStructureException::class,
        IOException::class,
        PlatformXmlParserException::class,
        UnfullfilledRequirementsException::class
    )
    abstract fun parse(): T

    @Throws(PlatformXmlParserException::class, IOException::class)
    open fun skipBlock(tag: String) {
        while (parser.getEventType() != PlatformXmlParser.END_DOCUMENT) {
            val eventType = parser.next()
            if (eventType == PlatformXmlParser.END_DOCUMENT) {
                return
            } else if (eventType == PlatformXmlParser.END_TAG) {
                if (parser.getName() == tag) {
                    return
                }
            }
        }
    }

    @Throws(PlatformXmlParserException::class, IOException::class)
    protected fun nextNonWhitespace(): Int {
        var ret = parser.next()
        if (ret == PlatformXmlParser.TEXT && parser.isWhitespace()) {
            ret = parser.next()
        }
        return ret
    }

    /**
     * @return true if the passed in string matches the name
     * of the current tag (case insensitive).
     */
    fun isTagNamed(s: String?): Boolean {
        if (s == null || parser.getName() == null) {
            return false
        }
        return parser.getName()!!.lowercase() == s.lowercase()
    }

    companion object {
        /**
         * Prepares a parser that will be used by the element parser, configuring relevant
         * parameters and setting it to the appropriate point in the document.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun instantiateParser(stream: InputStream): PlatformXmlParser {
            try {
                val parser = createXmlParser(stream)
                // Point to the first available tag.
                parser.next()
                return parser
            } catch (e: PlatformXmlParserException) {
                Logger.exception("Element Parser", e)
                throw IOException(e.message)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                throw IOException(e.message)
            }
        }
    }
}
