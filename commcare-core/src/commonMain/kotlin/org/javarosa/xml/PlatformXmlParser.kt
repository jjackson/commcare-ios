package org.javarosa.xml

/**
 * Cross-platform XML pull parser interface mirroring XmlPullParser/KXmlParser API.
 * On JVM, implemented by wrapping kxml2's KXmlParser.
 * On iOS, implemented with a pure Kotlin state-machine parser.
 *
 * Simple getters are declared as Kotlin properties so that both
 * property syntax (parser.name) and getter syntax (parser.getName())
 * work seamlessly from Kotlin and Java callers.
 */
interface PlatformXmlParser {
    fun next(): Int
    val eventType: Int
    val name: String?
    val namespace: String?
    val text: String?
    val isWhitespace: Boolean
    val depth: Int
    val attributeCount: Int
    val positionDescription: String
    val prefix: String?

    fun getAttributeValue(namespace: String?, name: String): String?
    fun getAttributeName(index: Int): String
    fun getAttributeNamespace(index: Int): String
    fun getAttributePrefix(index: Int): String?
    fun getAttributeValue(index: Int): String
    fun getNamespace(prefix: String?): String

    /**
     * Returns the text content of the current element.
     * Advances parser past the end tag.
     */
    fun nextText(): String

    /**
     * Advances to the next start or end tag, skipping whitespace text events.
     * Returns the event type (START_TAG or END_TAG).
     */
    fun nextTag(): Int

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
