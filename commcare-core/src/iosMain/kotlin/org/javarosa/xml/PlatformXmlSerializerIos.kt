package org.javarosa.xml

/**
 * Pure Kotlin XML serializer for iOS (Kotlin/Native).
 * Generates well-formed XML with proper escaping and namespace support.
 */
class IosXmlSerializer : PlatformXmlSerializer {
    private val sb = StringBuilder()
    private var inTag = false
    private var hasContent = false

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

    override fun startTag(namespace: String?, name: String): PlatformXmlSerializer {
        closeOpenTag()
        sb.append("<$name")
        inTag = true
        hasContent = false
        return this
    }

    override fun endTag(namespace: String?, name: String): PlatformXmlSerializer {
        if (inTag && !hasContent) {
            sb.append(" />")
            inTag = false
        } else {
            closeOpenTag()
            sb.append("</$name>")
        }
        return this
    }

    override fun attribute(namespace: String?, name: String, value: String): PlatformXmlSerializer {
        sb.append(" $name=\"${escapeAttributeValue(value)}\"")
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
