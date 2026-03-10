package org.javarosa.xml.dom

import org.javarosa.core.util.Interner
import org.kxml2.io.KXmlParser
import java.io.Reader

/**
 * JVM-only helper for parsing XML documents using kxml2's DOM.
 * Encapsulates the KXmlParser creation and Document.parse() calls
 * so that consumer code doesn't need to import kxml2 directly.
 */
object XmlDocumentHelper {

    /**
     * Parse an XML document from a Reader into a kxml2 Document.
     * Optionally applies string interning for memory efficiency.
     */
    @JvmStatic
    fun parseDocument(reader: Reader, stringCache: Interner<String>?): XmlDocument {
        val parser: KXmlParser = if (stringCache != null) {
            InterningKXmlParserJvm(stringCache)
        } else {
            KXmlParser()
        }

        parser.setInput(reader)
        parser.setFeature(KXmlParser.FEATURE_PROCESS_NAMESPACES, true)

        val doc = XmlDocument()
        doc.parse(parser)
        return doc
    }
}
