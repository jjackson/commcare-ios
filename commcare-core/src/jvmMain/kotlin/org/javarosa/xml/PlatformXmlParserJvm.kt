package org.javarosa.xml

import org.kxml2.io.KXmlParser
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * JVM implementation of PlatformXmlParser wrapping kxml2's KXmlParser.
 */
class JvmXmlParser : PlatformXmlParser {
    val kxmlParser: KXmlParser

    constructor(data: ByteArray, encoding: String) {
        kxmlParser = KXmlParser()
        kxmlParser.setInput(ByteArrayInputStream(data), encoding)
        kxmlParser.setFeature(KXmlParser.FEATURE_PROCESS_NAMESPACES, true)
    }

    /**
     * Wrap an existing KXmlParser. Used by ElementParser.instantiateParser
     * and other JVM code that creates parsers from InputStreams.
     */
    constructor(parser: KXmlParser) {
        this.kxmlParser = parser
    }

    override fun next(): Int = mapEventType(kxmlParser.next())
    override val eventType: Int get() = mapEventType(kxmlParser.eventType)
    override val name: String? get() = kxmlParser.name
    override val namespace: String? get() = kxmlParser.namespace
    override val text: String? get() = kxmlParser.text
    override val depth: Int get() = kxmlParser.depth
    override val attributeCount: Int get() = kxmlParser.attributeCount
    override val prefix: String? get() = kxmlParser.prefix
    override val positionDescription: String get() = kxmlParser.positionDescription

    override fun isWhitespace(): Boolean = kxmlParser.isWhitespace
    override fun getAttributeValue(namespace: String?, name: String): String? =
        kxmlParser.getAttributeValue(namespace, name)
    override fun getAttributeName(index: Int): String = kxmlParser.getAttributeName(index)
    override fun getAttributeNamespace(index: Int): String = kxmlParser.getAttributeNamespace(index)
    override fun getAttributePrefix(index: Int): String? = kxmlParser.getAttributePrefix(index)
    override fun getAttributeValue(index: Int): String = kxmlParser.getAttributeValue(index)
    override fun getNamespace(prefix: String?): String = kxmlParser.getNamespace(prefix)

    /**
     * Delegates to KXmlParser.nextTag() for JVM compatibility.
     */
    override fun nextTag(): Int = mapEventType(kxmlParser.nextTag())

    /**
     * Delegates to KXmlParser.nextText() for JVM compatibility.
     */
    override fun nextText(): String = kxmlParser.nextText()

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

/**
 * Create a PlatformXmlParser from an InputStream (JVM-only).
 * Configures namespace processing and advances past START_DOCUMENT.
 */
fun createXmlParser(stream: InputStream, encoding: String = "UTF-8"): PlatformXmlParser {
    val parser = KXmlParser()
    parser.setInput(stream, encoding)
    parser.setFeature(KXmlParser.FEATURE_PROCESS_NAMESPACES, true)
    parser.next()
    return JvmXmlParser(parser)
}

actual fun createXmlParser(data: ByteArray, encoding: String): PlatformXmlParser =
    JvmXmlParser(data, encoding)
