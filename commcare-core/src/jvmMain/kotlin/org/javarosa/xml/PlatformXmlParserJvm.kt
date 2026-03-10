package org.javarosa.xml

import org.kxml2.io.KXmlParser
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * JVM implementation of PlatformXmlParser wrapping kxml2's KXmlParser.
 */
class JvmXmlParser private constructor(private val parser: KXmlParser) : PlatformXmlParser {

    constructor(data: ByteArray, encoding: String) : this(KXmlParser()) {
        parser.setInput(ByteArrayInputStream(data), encoding)
        parser.setFeature(KXmlParser.FEATURE_PROCESS_NAMESPACES, true)
    }

    override fun next(): Int = mapEventType(parser.next())
    override val eventType: Int get() = mapEventType(parser.eventType)
    override val name: String? get() = parser.name
    override val namespace: String? get() = parser.namespace
    override val text: String? get() = parser.text
    override val isWhitespace: Boolean get() = parser.isWhitespace
    override val depth: Int get() = parser.depth
    override val attributeCount: Int get() = parser.attributeCount
    override val positionDescription: String get() = parser.positionDescription
    override val prefix: String? get() = parser.prefix

    override fun getAttributeValue(namespace: String?, name: String): String? =
        parser.getAttributeValue(namespace, name)
    override fun getAttributeName(index: Int): String = parser.getAttributeName(index)
    override fun getAttributeNamespace(index: Int): String = parser.getAttributeNamespace(index)
    override fun getAttributePrefix(index: Int): String? = parser.getAttributePrefix(index)
    override fun getAttributeValue(index: Int): String = parser.getAttributeValue(index)
    override fun getNamespace(prefix: String?): String = parser.getNamespace(prefix)
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

    companion object {
        /**
         * Wrap an existing KXmlParser instance as a PlatformXmlParser.
         * The KXmlParser should already be configured (setInput, setFeature called).
         */
        @JvmStatic
        fun wrap(parser: KXmlParser): JvmXmlParser = JvmXmlParser(parser)

        /**
         * Create a PlatformXmlParser from an InputStream.
         * Configures namespace processing and advances past START_DOCUMENT.
         */
        @JvmStatic
        fun fromStream(stream: InputStream): PlatformXmlParser {
            val parser = KXmlParser()
            parser.setInput(stream, "UTF-8")
            parser.setFeature(KXmlParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.next() // advance past START_DOCUMENT to first tag
            return JvmXmlParser(parser)
        }
    }
}

actual fun createXmlParser(data: ByteArray, encoding: String): PlatformXmlParser =
    JvmXmlParser(data, encoding)
