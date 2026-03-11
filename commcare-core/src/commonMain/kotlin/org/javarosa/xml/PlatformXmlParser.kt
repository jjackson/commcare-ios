package org.javarosa.xml

/**
 * Cross-platform XML pull parser interface mirroring XmlPullParser/KXmlParser API.
 * On JVM, implemented by wrapping kxml2's KXmlParser.
 * On iOS, implemented with a pure Kotlin state-machine parser.
 *
 * Uses Kotlin properties for simple getters to enable property-syntax access
 * (e.g., parser.name, parser.depth) which matches KXmlParser's Java-to-Kotlin usage.
 */
interface PlatformXmlParser {
    fun next(): Int

    val eventType: Int
    val name: String?
    val namespace: String?
    val text: String?
    val depth: Int
    val attributeCount: Int
    val prefix: String?
    val positionDescription: String

    fun isWhitespace(): Boolean
    fun getAttributeValue(namespace: String?, name: String): String?
    fun getAttributeName(index: Int): String
    fun getAttributeNamespace(index: Int): String
    fun getAttributePrefix(index: Int): String?
    fun getAttributeValue(index: Int): String
    fun getNamespace(prefix: String?): String

    /**
     * Skip whitespace and advance to the next START_TAG or END_TAG.
     * Throws if the next non-whitespace event is not a tag.
     */
    fun nextTag(): Int {
        var event = next()
        if (event == TEXT && isWhitespace()) {
            event = next()
        }
        if (event != START_TAG && event != END_TAG) {
            throw PlatformXmlParserException("Expected START_TAG or END_TAG, got $event")
        }
        return event
    }

    /**
     * Read the text content of the current element.
     * Must be on START_TAG. Returns the text and leaves parser on END_TAG.
     */
    fun nextText(): String {
        if (eventType != START_TAG) {
            throw PlatformXmlParserException("Parser must be on START_TAG to read next text, was $eventType")
        }
        var event = next()
        val result: String
        if (event == TEXT) {
            result = text ?: ""
            event = next()
        } else {
            result = ""
        }
        if (event != END_TAG) {
            throw PlatformXmlParserException("Expected END_TAG after text, got $event")
        }
        return result
    }

    companion object {
        const val START_DOCUMENT = 0
        const val END_DOCUMENT = 1
        const val START_TAG = 2
        const val END_TAG = 3
        const val TEXT = 4

        const val FEATURE_PROCESS_NAMESPACES =
            "http://xmlpull.org/v1/doc/features.html#process-namespaces"
    }
}

/**
 * Factory function to create a platform-specific XML pull parser from raw bytes.
 * The parser is initialized with namespace processing enabled and positioned
 * at the first event (START_DOCUMENT).
 */
expect fun createXmlParser(data: ByteArray, encoding: String = "UTF-8"): PlatformXmlParser
