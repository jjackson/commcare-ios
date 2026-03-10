package org.javarosa.xml

import org.kxml2.io.KXmlSerializer
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * JVM implementation of PlatformXmlSerializer wrapping kxml2's KXmlSerializer.
 */
class JvmXmlSerializer private constructor(
    internal val serializer: KXmlSerializer,
    private val baos: ByteArrayOutputStream?
) : PlatformXmlSerializer {

    constructor() : this(KXmlSerializer(), ByteArrayOutputStream()) {
        serializer.setOutput(baos!!, "UTF-8")
    }

    constructor(stream: OutputStream, encoding: String) : this(KXmlSerializer(), null) {
        serializer.setOutput(stream, encoding)
    }

    companion object {
        /**
         * Wrap an existing KXmlSerializer instance as a PlatformXmlSerializer.
         * The KXmlSerializer should already be configured (setOutput called).
         */
        @JvmStatic
        fun wrap(serializer: KXmlSerializer): JvmXmlSerializer = JvmXmlSerializer(serializer, null)
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
        return baos?.toByteArray() ?: throw UnsupportedOperationException(
            "toByteArray() not supported for stream-based serializer"
        )
    }
}

actual fun createXmlSerializer(): PlatformXmlSerializer = JvmXmlSerializer()
