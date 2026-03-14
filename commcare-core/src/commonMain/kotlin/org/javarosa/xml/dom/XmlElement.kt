package org.javarosa.xml.dom

/**
 * Cross-platform XML element, replacing kxml2's Element/Node.
 * Supports mutable children (XFormParser modifies the tree during parsing).
 */
class XmlElement(
    val name: String,
    val namespace: String?,
    private val _attributes: MutableList<XmlAttribute> = mutableListOf(),
    private val _children: MutableList<XmlChild> = mutableListOf(),
    private val _namespaces: MutableList<XmlNamespace> = mutableListOf(),
    var parent: Any? = null
) {
    val childCount: Int get() = _children.size
    val attributeCount: Int get() = _attributes.size
    val namespaceCount: Int get() = _namespaces.size

    fun getType(index: Int): Int = _children[index].type

    fun getElement(index: Int): XmlElement? {
        val child = _children[index]
        return if (child.type == XmlNodeType.ELEMENT) child.element else null
    }

    fun getChild(index: Int): Any? {
        val child = _children[index]
        return when (child.type) {
            XmlNodeType.ELEMENT -> child.element
            else -> child.text
        }
    }

    fun getText(index: Int): String? = _children[index].text

    fun isText(index: Int): Boolean =
        _children[index].type == XmlNodeType.TEXT

    fun getAttributeValue(namespace: String?, name: String): String? {
        for (attr in _attributes) {
            if (attr.name == name) {
                if (namespace == null || namespace.isEmpty() || attr.namespace == namespace) {
                    return attr.value
                }
            }
        }
        return null
    }

    fun getAttributeValue(index: Int): String = _attributes[index].value
    fun getAttributeName(index: Int): String = _attributes[index].name
    fun getAttributeNamespace(index: Int): String = _attributes[index].namespace

    fun getNamespaceUri(index: Int): String? = _namespaces[index].uri
    fun getNamespacePrefix(index: Int): String? = _namespaces[index].prefix

    fun getNamespaceUri(prefix: String?): String? {
        // Search this element's declarations first
        for (ns in _namespaces) {
            if (ns.prefix == prefix || (ns.prefix == null && prefix == null)) {
                return ns.uri
            }
        }
        // Walk up the tree
        val p = parent
        if (p is XmlElement) {
            return p.getNamespaceUri(prefix)
        }
        return null
    }

    fun removeChild(index: Int) {
        _children.removeAt(index)
    }

    fun addChild(index: Int, type: Int, data: Any?) {
        val child = when (type) {
            XmlNodeType.ELEMENT -> {
                val elem = data as XmlElement
                elem.parent = this
                XmlChild(type, element = elem)
            }
            else -> XmlChild(type, text = data?.toString() ?: "")
        }
        _children.add(index, child)
    }

    fun addChild(type: Int, data: Any?) {
        addChild(_children.size, type, data)
    }

    fun addAttribute(namespace: String, name: String, value: String) {
        _attributes.add(XmlAttribute(namespace, name, value))
    }

    fun addNamespace(prefix: String?, uri: String) {
        _namespaces.add(XmlNamespace(prefix, uri))
    }

    /**
     * Write this element to a PlatformXmlSerializer.
     */
    fun write(serializer: org.javarosa.xml.PlatformXmlSerializer) {
        val ns = namespace ?: ""
        serializer.startTag(ns, name)

        // Write namespace declarations
        for (decl in _namespaces) {
            serializer.setPrefix(decl.prefix ?: "", decl.uri ?: "")
        }

        // Write attributes
        for (attr in _attributes) {
            serializer.attribute(attr.namespace, attr.name, attr.value)
        }

        // Write children
        for (child in _children) {
            when (child.type) {
                XmlNodeType.ELEMENT -> child.element?.write(serializer)
                XmlNodeType.TEXT, XmlNodeType.IGNORABLE_WHITESPACE -> {
                    serializer.text(child.text ?: "")
                }
            }
        }

        serializer.endTag(ns, name)
    }
}

data class XmlAttribute(
    val namespace: String,
    val name: String,
    val value: String
)

data class XmlNamespace(
    val prefix: String?,
    val uri: String?
)

data class XmlChild(
    val type: Int,
    val element: XmlElement? = null,
    val text: String? = null
)
