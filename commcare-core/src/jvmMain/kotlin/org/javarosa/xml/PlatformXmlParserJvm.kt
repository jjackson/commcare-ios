package org.javarosa.xml

import org.kxml2.io.KXmlParser
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * JVM implementation of PlatformXmlParser wrapping kxml2's KXmlParser.
 */
class JvmXmlParser : PlatformXmlParser {
    private val parser: KXmlParser

    constructor(data: ByteArray, encoding: String) {
        parser = KXmlParser()
        parser.setInput(ByteArrayInputStream(data), encoding)
        parser.setFeature(KXmlParser.FEATURE_PROCESS_NAMESPACES, true)
    }

    /**
     * Wrap an existing KXmlParser. Used for migration from direct KXmlParser usage.
     */
    constructor(existingParser: KXmlParser) {
        parser = existingParser
    }

    /**
     * Create from an InputStream, matching ElementParser.instantiateParser behavior.
     */
    constructor(stream: InputStream, encoding: String = "UTF-8") {
        parser = KXmlParser()
        parser.setInput(stream, encoding)
        parser.setFeature(KXmlParser.FEATURE_PROCESS_NAMESPACES, true)
    }

    override fun next(): Int = mapEventType(parser.next())
    override fun getEventType(): Int = mapEventType(parser.eventType)
    override fun getName(): String? = parser.name
    override fun getNamespace(): String? = parser.namespace
    override fun getText(): String? = parser.text
    override fun isWhitespace(): Boolean = parser.isWhitespace
    override fun getDepth(): Int = parser.depth
    override fun getAttributeValue(namespace: String?, name: String): String? =
        parser.getAttributeValue(namespace, name)
    override fun getAttributeCount(): Int = parser.attributeCount
    override fun getAttributeName(index: Int): String = parser.getAttributeName(index)
    override fun getAttributeNamespace(index: Int): String = parser.getAttributeNamespace(index)
    override fun getAttributePrefix(index: Int): String? = parser.getAttributePrefix(index)
    override fun getAttributeValue(index: Int): String = parser.getAttributeValue(index)
    override fun getNamespace(prefix: String?): String = parser.getNamespace(prefix)
    override fun getPrefix(): String? = parser.prefix
    override fun getPositionDescription(): String = parser.positionDescription
    override fun nextText(): String = parser.nextText()
    override fun nextTag(): Int = mapEventType(parser.nextTag())

    private fun mapEventType(kxmlType: Int): Int {
        return when (kxmlType) {
            KXmlParser.START_DOCUMENT -> PlatformXmlParser.START_DOCUMENT
            KXmlParser.END_DOCUMENT -> PlatformXmlParser.END_DOCUMENT
            KXmlParser.START_TAG -> PlatformXmlParser.START_TAG
            KXmlParser.END_TAG -> PlatformXmlParser.END_TAG
            KXmlParser.TEXT -> PlatformXmlParser.TEXT
            else -> kxmlType
        }
    }
}

actual fun createXmlParser(data: ByteArray, encoding: String): PlatformXmlParser =
    JvmXmlParser(data, encoding)

/**
 * JVM-only factory: create PlatformXmlParser from an InputStream.
 * Used by ElementParser and other code that reads from streams.
 */
fun createXmlParser(stream: InputStream, encoding: String = "UTF-8"): PlatformXmlParser =
    JvmXmlParser(stream, encoding)

/**
 * JVM-only factory: wrap an existing KXmlParser as a PlatformXmlParser.
 * Used for migration from direct KXmlParser usage.
 */
fun wrapKXmlParser(parser: KXmlParser): PlatformXmlParser =
    JvmXmlParser(parser)
