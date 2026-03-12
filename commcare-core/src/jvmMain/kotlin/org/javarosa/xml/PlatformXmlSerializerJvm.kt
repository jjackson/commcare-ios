package org.javarosa.xml

import org.javarosa.core.io.PlatformOutputStream
import org.kxml2.io.KXmlSerializer
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * JVM implementation of PlatformXmlSerializer wrapping kxml2's KXmlSerializer.
 */
class JvmXmlSerializer : PlatformXmlSerializer {
    private val baos: ByteArrayOutputStream?
    private val serializer = KXmlSerializer()

    constructor() {
        baos = ByteArrayOutputStream()
        serializer.setOutput(baos, "UTF-8")
    }

    /**
     * Create a serializer that writes to the given OutputStream.
     * Used by JVM-only code that needs to write XML to a specific stream.
     */
    constructor(output: OutputStream, encoding: String) {
        baos = null
        serializer.setOutput(output, encoding)
    }

    override fun startDocument(encoding: String?, standalone: Boolean?) {
        serializer.startDocument(encoding, standalone)
    }

    override fun endDocument() {
        serializer.endDocument()
    }

    override fun setPrefix(prefix: String, namespace: String) {
        serializer.setPrefix(prefix, namespace)
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
        return baos?.toByteArray() ?: ByteArray(0)
    }
}

actual fun createXmlSerializer(): PlatformXmlSerializer = JvmXmlSerializer()

/**
 * JVM-only factory: create PlatformXmlSerializer that writes to an OutputStream.
 * Used by DataModelSerializer and other JVM code that writes XML to streams.
 */
actual fun createXmlSerializer(output: PlatformOutputStream, encoding: String): PlatformXmlSerializer =
    JvmXmlSerializer(output, encoding)
