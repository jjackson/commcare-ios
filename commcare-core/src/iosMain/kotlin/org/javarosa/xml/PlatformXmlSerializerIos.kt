package org.javarosa.xml

/**
 * Pure Kotlin XML serializer for iOS (Kotlin/Native).
 * Generates well-formed XML with proper escaping and namespace support.
 *
 * Namespace handling mirrors kxml2's KXmlSerializer behavior:
 * - Call setPrefix() before startTag() to declare xmlns attributes
 * - startTag/endTag/attribute use namespace URIs to resolve prefixed names
 */
class IosXmlSerializer : PlatformXmlSerializer {
    private val sb = StringBuilder()
    private var inTag = false
    private var hasContent = false

    // namespace URI -> prefix (e.g., "http://www.w3.org/1999/xhtml" -> "h")
    private val namespaceToPrefix = mutableMapOf<String, String>()
    // Pending prefix declarations to emit as xmlns attributes on the next startTag
    private val pendingPrefixes = mutableListOf<Pair<String, String>>()

    override fun startDocument(encoding: String?, standalone: Boolean?) {
        sb.append("<?xml version=\"1.0\"")
        if (encoding != null) {
            sb.append(" encoding=\"$encoding\"")
        }
        if (standalone != null) {
            sb.append(" standalone=\"${if (standalone) "yes" else "no"}\"")
        }
        sb.append("?>")
    }

    override fun endDocument() {
        // Nothing needed
    }

    override fun setPrefix(prefix: String, namespace: String) {
        pendingPrefixes.add(prefix to namespace)
        namespaceToPrefix[namespace] = prefix
    }

    override fun startTag(namespace: String?, name: String): PlatformXmlSerializer {
        closeOpenTag()
        sb.append("<${qualifiedName(namespace, name)}")

        // Emit pending xmlns declarations
        for ((prefix, ns) in pendingPrefixes) {
            if (prefix.isEmpty()) {
                sb.append(" xmlns=\"${escapeAttributeValue(ns)}\"")
            } else {
                sb.append(" xmlns:$prefix=\"${escapeAttributeValue(ns)}\"")
            }
        }
        pendingPrefixes.clear()

        inTag = true
        hasContent = false
        return this
    }

    override fun endTag(namespace: String?, name: String): PlatformXmlSerializer {
        val qName = qualifiedName(namespace, name)
        if (inTag && !hasContent) {
            sb.append(" />")
            inTag = false
        } else {
            closeOpenTag()
            sb.append("</$qName>")
        }
        return this
    }

    override fun attribute(namespace: String?, name: String, value: String): PlatformXmlSerializer {
        sb.append(" ${qualifiedName(namespace, name)}=\"${escapeAttributeValue(value)}\"")
        return this
    }

    override fun text(text: String): PlatformXmlSerializer {
        closeOpenTag()
        sb.append(escapeTextContent(text))
        return this
    }

    override fun flush() {
        closeOpenTag()
    }

    override fun toByteArray(): ByteArray {
        flush()
        return sb.toString().encodeToByteArray()
    }

    private fun qualifiedName(namespace: String?, localName: String): String {
        if (namespace == null) return localName
        val prefix = namespaceToPrefix[namespace] ?: return localName
        return if (prefix.isEmpty()) localName else "$prefix:$localName"
    }

    private fun closeOpenTag() {
        if (inTag) {
            sb.append(">")
            inTag = false
            hasContent = true
        }
    }

    private fun escapeAttributeValue(s: String): String {
        val result = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '&' -> result.append("&amp;")
                '<' -> result.append("&lt;")
                '"' -> result.append("&quot;")
                '\n' -> result.append("&#10;")
                '\r' -> result.append("&#13;")
                '\t' -> result.append("&#9;")
                else -> result.append(c)
            }
        }
        return result.toString()
    }

    private fun escapeTextContent(s: String): String {
        val result = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '&' -> result.append("&amp;")
                '<' -> result.append("&lt;")
                '>' -> result.append("&gt;")
                else -> result.append(c)
            }
        }
        return result.toString()
    }
}

actual fun createXmlSerializer(): PlatformXmlSerializer = IosXmlSerializer()

actual fun createXmlSerializer(output: org.javarosa.core.io.PlatformOutputStream, encoding: String): PlatformXmlSerializer {
    // iOS implementation writes to in-memory buffer; output stream parameter is ignored
    // (caller should use toByteArray() and write to stream manually)
    return IosXmlSerializer()
}
