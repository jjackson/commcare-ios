package org.javarosa.xml

/**
 * Cross-platform XML serializer interface mirroring XmlSerializer/KXmlSerializer API.
 * On JVM, implemented by wrapping kxml2's KXmlSerializer.
 * On iOS, implemented with string-based XML generation.
 */
interface PlatformXmlSerializer {
    fun startDocument(encoding: String?, standalone: Boolean?)
    fun endDocument()
    fun setPrefix(prefix: String, namespace: String)
    fun startTag(namespace: String?, name: String): PlatformXmlSerializer
    fun endTag(namespace: String?, name: String): PlatformXmlSerializer
    fun attribute(namespace: String?, name: String, value: String): PlatformXmlSerializer
    fun text(text: String): PlatformXmlSerializer
    fun flush()
    fun toByteArray(): ByteArray
}

/**
 * Factory function to create a platform-specific XML serializer
 * that writes to an in-memory buffer.
 */
expect fun createXmlSerializer(): PlatformXmlSerializer
