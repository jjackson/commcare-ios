package org.javarosa.xml.dom

import org.javarosa.core.util.Interner
import org.javarosa.xml.PlatformXmlParser

/**
 * Builds an XmlDocument (DOM tree) from a PlatformXmlParser (pull parser).
 * Replaces kxml2's Document.parse(KXmlParser).
 *
 * Handles text node consolidation (merging consecutive text nodes that arise
 * from escaped unicode sequences) which XFormParser's getXMLDocument() did.
 */
object XmlDomBuilder {

    fun parse(
        parser: PlatformXmlParser,
        stringCache: Interner<String>? = null
    ): XmlDocument {
        val root = buildElement(parser, stringCache)
        return XmlDocument(root)
    }

    /**
     * Parse from the current START_TAG to its matching END_TAG,
     * building the full subtree.
     */
    private fun buildElement(
        parser: PlatformXmlParser,
        stringCache: Interner<String>?
    ): XmlElement {
        require(parser.getEventType() == PlatformXmlParser.START_TAG) {
            "Expected START_TAG, got ${parser.getEventType()}"
        }

        val name = intern(parser.getName() ?: "", stringCache)
        val namespace = parser.getNamespace()

        val element = XmlElement(name, namespace)

        // Collect namespace declarations from the parser's namespace API.
        // KXmlParser (JVM) doesn't expose xmlns via getAttributeCount when
        // FEATURE_PROCESS_NAMESPACES is enabled — they're only available
        // through the namespace enumeration API.
        val nsCount = parser.getNamespaceCount()
        for (i in 0 until nsCount) {
            val nsPrefix = parser.getNamespacePrefix(i)
            val nsUri = intern(parser.getNamespaceUri(i) ?: "", stringCache)
            element.addNamespace(nsPrefix, nsUri)
        }

        // Collect regular attributes
        val attrCount = parser.getAttributeCount()
        for (i in 0 until attrCount) {
            val attrName = intern(parser.getAttributeName(i), stringCache)
            val attrNs = parser.getAttributeNamespace(i)
            val attrValue = intern(parser.getAttributeValue(i), stringCache)
            element.addAttribute(attrNs ?: "", attrName, attrValue)
        }

        // Process children
        var eventType = parser.next()
        while (eventType != PlatformXmlParser.END_TAG) {
            when (eventType) {
                PlatformXmlParser.START_TAG -> {
                    val child = buildElement(parser, stringCache)
                    child.parent = element
                    element.addChild(XmlNodeType.ELEMENT, child)
                }
                PlatformXmlParser.TEXT -> {
                    val text = parser.getText() ?: ""
                    // Consolidate consecutive text nodes
                    val lastIndex = element.childCount - 1
                    if (lastIndex >= 0 && element.isText(lastIndex)) {
                        // Merge with previous text node
                        val prev = element.getText(lastIndex) ?: ""
                        element.removeChild(lastIndex)
                        element.addChild(
                            XmlNodeType.TEXT,
                            intern(prev + text, stringCache)
                        )
                    } else {
                        element.addChild(
                            XmlNodeType.TEXT,
                            intern(text, stringCache)
                        )
                    }
                }
                PlatformXmlParser.END_DOCUMENT -> break
            }
            eventType = parser.next()
        }

        return element
    }

    private fun intern(s: String, cache: Interner<String>?): String {
        return cache?.intern(s) ?: s
    }
}
