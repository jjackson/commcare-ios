package org.javarosa.xml

/**
 * Cross-platform XML pull parser interface mirroring XmlPullParser/KXmlParser API.
 * On JVM, implemented by wrapping kxml2's KXmlParser.
 * On iOS, implemented with a pure Kotlin state-machine parser.
 */
interface PlatformXmlParser {
    fun next(): Int
    fun getEventType(): Int
    fun getName(): String?
    fun getNamespace(): String?
    fun getText(): String?
    fun isWhitespace(): Boolean
    fun getDepth(): Int
    fun getAttributeValue(namespace: String?, name: String): String?
    fun getAttributeCount(): Int
    fun getAttributeName(index: Int): String
    fun getAttributeNamespace(index: Int): String
    fun getAttributePrefix(index: Int): String?
    fun getAttributeValue(index: Int): String
    fun getNamespace(prefix: String?): String
    fun getPrefix(): String?
    fun getPositionDescription(): String
    fun nextText(): String
    fun nextTag(): Int

    /**
     * Returns the number of namespace declarations at the current depth.
     * Only valid when positioned on a START_TAG.
     */
    fun getNamespaceCount(): Int

    /**
     * Returns the prefix of the namespace declaration at [index].
     * Returns null for the default namespace.
     */
    fun getNamespacePrefix(index: Int): String?

    /**
     * Returns the URI of the namespace declaration at [index].
     */
    fun getNamespaceUri(index: Int): String?

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
