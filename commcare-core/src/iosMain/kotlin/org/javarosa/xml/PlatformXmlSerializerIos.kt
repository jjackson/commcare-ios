package org.javarosa.xml

/**
 * Pure Kotlin XML serializer for iOS (Kotlin/Native).
 * Generates well-formed XML with proper escaping and namespace support.
 *
 * Namespace handling mirrors kxml2's KXmlSerializer behavior:
 * - Call setPrefix() before startTag() to declare xmlns attributes
 * - startTag auto-declares any namespace that isn't already declared in
 *   the current element-stack scope (so a `<case xmlns="...">` child of
 *   `<data xmlns="...other">` gets its xmlns emitted automatically)
 * - endTag pops the scope so auto-declarations don't leak upward
 */
class IosXmlSerializer(
    private val outputStream: org.javarosa.core.io.PlatformOutputStream? = null
) : PlatformXmlSerializer {
    private val sb = StringBuilder()
    private var inTag = false
    private var hasContent = false

    // namespace URI -> prefix (e.g., "http://www.w3.org/1999/xhtml" -> "h")
    private val namespaceToPrefix = mutableMapOf<String, String>()
    // Pending prefix declarations to emit as xmlns attributes on the next startTag
    private val pendingPrefixes = mutableListOf<Pair<String, String>>()
    // Stack of default namespaces active at each element depth. Top of stack
    // is the current element's default xmlns. Used to detect when a child
    // element has a different namespace and needs a fresh xmlns declaration.
    private val defaultNsStack = ArrayDeque<String>().apply { addLast("") }

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
        flush()
    }

    override fun setPrefix(prefix: String, namespace: String) {
        pendingPrefixes.add(prefix to namespace)
        namespaceToPrefix[namespace] = prefix
    }

    override fun startTag(namespace: String?, name: String): PlatformXmlSerializer {
        closeOpenTag()

        // Auto-declare a default xmlns if this element's namespace differs
        // from the current inherited default AND isn't already pending via
        // setPrefix(). Required so a <case xmlns="..."/> child of a
        // <data xmlns="other"> element emits its namespace.
        val ns = namespace ?: ""
        val currentDefault = defaultNsStack.last()
        val explicitPrefixKnown = ns.isNotEmpty() &&
            namespaceToPrefix[ns]?.isNotEmpty() == true
        val alreadyPendingDefault = pendingPrefixes.any { it.first.isEmpty() && it.second == ns }
        val needsDefaultDecl = ns.isNotEmpty() && ns != currentDefault &&
            !explicitPrefixKnown && !alreadyPendingDefault
        if (needsDefaultDecl) {
            // Register as the default namespace (empty prefix) so
            // qualifiedName emits the localName without a prefix.
            namespaceToPrefix[ns] = ""
            pendingPrefixes.add("" to ns)
        }

        sb.append("<${qualifiedName(namespace, name)}")

        // Emit pending xmlns declarations
        for ((prefix, nsUri) in pendingPrefixes) {
            if (prefix.isEmpty()) {
                sb.append(" xmlns=\"${escapeAttributeValue(nsUri)}\"")
            } else {
                sb.append(" xmlns:$prefix=\"${escapeAttributeValue(nsUri)}\"")
            }
        }
        pendingPrefixes.clear()

        // Push the effective default namespace for the child scope.
        // If this element declared a new default OR inherited via setPrefix,
        // that's the new default; otherwise children inherit the parent's.
        val newDefault = if (ns.isNotEmpty() && namespaceToPrefix[ns] == "") ns else currentDefault
        defaultNsStack.addLast(newDefault)

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
        // Pop the default namespace scope for this element.
        if (defaultNsStack.size > 1) defaultNsStack.removeLast()
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
        if (outputStream != null && sb.isNotEmpty()) {
            val bytes = sb.toString().encodeToByteArray()
            outputStream.write(bytes)
            outputStream.flush()
            sb.clear()
        }
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
    return IosXmlSerializer(outputStream = output)
}
