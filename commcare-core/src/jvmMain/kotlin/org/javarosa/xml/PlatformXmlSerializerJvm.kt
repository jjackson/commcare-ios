package org.javarosa.xml

import org.kxml2.io.KXmlSerializer
import java.io.ByteArrayOutputStream

/**
 * JVM implementation of PlatformXmlSerializer wrapping kxml2's KXmlSerializer.
 */
class JvmXmlSerializer : PlatformXmlSerializer {
    private val baos = ByteArrayOutputStream()
    private val serializer = KXmlSerializer()

    init {
        serializer.setOutput(baos, "UTF-8")
    }

    override fun startDocument(encoding: String?, standalone: Boolean?) {
        serializer.startDocument(encoding, standalone)
    }

    override fun endDocument() {
        serializer.endDocument()
    }

    override fun startTag(namespace: String?, name: String): PlatformXmlSerializer {
        serializer.startTag(namespace, name)
        return this
    }

    override fun endTag(namespace: String?, name: String): PlatformXmlSerializer {
        serializer.endTag(namespace, name)
        return this
    }

    override fun attribute(namespace: String?, name: String, value: String): PlatformXmlSerializer {
        serializer.attribute(namespace, name, value)
        return this
    }

    override fun text(text: String): PlatformXmlSerializer {
        serializer.text(text)
        return this
    }

    override fun flush() {
        serializer.flush()
    }

    override fun toByteArray(): ByteArray {
        serializer.flush()
        return baos.toByteArray()
    }
}

actual fun createXmlSerializer(): PlatformXmlSerializer = JvmXmlSerializer()
