package org.commcare.test.utilities

import org.kxml2.io.KXmlParser
import org.kxml2.kdom.Document
import org.kxml2.kdom.Element
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.InputStreamReader

/**
 * @author ctsims
 */
class XmlComparator {
    companion object {
        @JvmStatic
        fun getDocumentFromStream(inputStream: InputStream): Document {
            val parser = KXmlParser()
            val document = Document()

            try {
                val reader = InputStreamReader(inputStream, "UTF-8")
                parser.setInput(reader)
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                document.parse(parser)
                return document
            } catch (e: Exception) {
                throw RuntimeException(e.message)
            }
        }

        /**
         * Takes two DOM documents and compares them to see whether they have semantically identical
         * data. For our purposes semantic equality means:
         * - Both documents have the same elements in the same order
         * - All DOM elements have the same attributes with the same values
         * - All DOM elements with values contain the same value
         *
         * If any of these conditions are not met, a runtime exception containing a user readable message
         * will be thrown, but may be lacking robust context for where the DOM's fail to match.
         */
        @JvmStatic
        fun isDOMEqual(a: Document, b: Document) {
            isDOMEqualRecursive(a.rootElement, b.rootElement)
            isDOMEqualRecursive(b.rootElement, a.rootElement)
        }

        @JvmStatic
        fun isDOMEqualRecursive(left: Element, right: Element) {
            if (left.name != right.name) {
                throw RuntimeException("Mismatched element names '${left.name}' and '${right.name}'")
            }

            if (left.attributeCount != right.attributeCount) {
                throw RuntimeException("Mismatched attributes for node '${left.name}' ")
            }

            val leftAttr = attrTable(left)
            val rightAttr = attrTable(right)

            for (key in leftAttr.keys) {
                if (!rightAttr.containsKey(key)) {
                    throw RuntimeException("Mismatched attributes for node '${left.name}' ")
                }

                if (leftAttr[key] != rightAttr[key]) {
                    throw RuntimeException("Mismatched attributes for node '${left.name}' ")
                }
            }

            if (left.childCount != right.childCount) {
                throw RuntimeException("Mismatched child count (${left.childCount},${right.childCount}) for node '${left.name}' ")
            }

            for (i in 0 until left.childCount) {
                val l = left.getChild(i)
                val r = right.getChild(i)

                if (left.getType(i) != right.getType(i)) {
                    throw RuntimeException("Mismatched children for node '${left.name}' ")
                }

                if (l is Element) {
                    isDOMEqualRecursive(l, r as Element)
                } else if (l is String) {
                    if (l != r) {
                        throw RuntimeException("Mismatched element values '$l' and '$r'")
                    }
                }
            }
        }

        private fun attrTable(element: Element): HashMap<String, String> {
            val attr = HashMap<String, String>()
            for (i in 0 until element.attributeCount) {
                attr[element.getAttributeName(i)] = element.getAttributeValue(i)
            }
            return attr
        }
    }
}
