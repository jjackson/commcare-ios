package org.javarosa.xform.parse

import org.javarosa.core.model.ItemsetBinding
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.XPathConditional
import org.javarosa.xpath.expr.XPathPathExpr
import kotlin.jvm.JvmStatic

class ItemSetParsingUtils {
    companion object {
        @JvmStatic
        fun setLabel(itemset: ItemsetBinding, labelXpath: String?) {
            var xpath = labelXpath
            var labelItext = false
            if (xpath != null) {
                if (xpath.startsWith("jr:itext(") && xpath.endsWith(")")) {
                    xpath = xpath.substring("jr:itext(".length, xpath.indexOf(")"))
                    labelItext = true
                }
            } else {
                throw XFormParseException("<label> in <itemset> requires 'ref'")
            }

            val labelPath = XPathReference.getPathExpr(xpath)
            itemset.labelRef = DataInstance.unpackReference(XFormParser.getAbsRef(XPathReference(labelPath), itemset.nodesetRef!!))!!
            itemset.labelExpr = XPathConditional(labelPath)
            itemset.labelIsItext = labelItext
        }

        @JvmStatic
        fun setValue(itemset: ItemsetBinding, valueXpath: String?) {
            if (valueXpath == null) {
                throw XFormParseException("<value> in <itemset> requires 'ref'")
            }

            val valuePath = XPathReference.getPathExpr(valueXpath)
            itemset.valueRef = DataInstance.unpackReference(XFormParser.getAbsRef(XPathReference(valuePath), itemset.nodesetRef!!))!!
            itemset.valueExpr = XPathConditional(valuePath)
            itemset.copyMode = false
        }

        @JvmStatic
        fun setSort(itemset: ItemsetBinding, sortXpathString: String?) {
            if (sortXpathString == null) {
                throw XFormParseException("<sort> in <itemset> requires 'ref'")
            }

            val sortPath = XPathReference.getPathExpr(sortXpathString)
            itemset.sortRef = DataInstance.unpackReference(XFormParser.getAbsRef(XPathReference(sortPath), itemset.nodesetRef!!))!!
            itemset.sortExpr = XPathConditional(sortPath)
        }

        @JvmStatic
        fun setNodeset(itemset: ItemsetBinding, nodesetStr: String?, elementName: String) {
            if (nodesetStr == null) {
                throw RuntimeException("No nodeset attribute in element: $elementName")
            }

            val path = XPathReference.getPathExpr(nodesetStr)
            itemset.nodesetExpr = XPathConditional(path)
            val nodesetRef = XFormParser.getAbsRef(XPathReference(path.getReference()), itemset.contextRef!!)
            itemset.nodesetRef = DataInstance.unpackReference(nodesetRef)!!
        }
    }
}
