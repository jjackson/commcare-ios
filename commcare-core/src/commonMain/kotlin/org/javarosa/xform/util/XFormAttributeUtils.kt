package org.javarosa.xform.util

import org.javarosa.xml.dom.XmlElement

/**
 * Cross-platform attribute utilities for XForm parsing.
 * Replaces the kxml2 Element-based methods from XFormUtils.
 */
object XFormAttributeUtils {

    fun getAttributeList(e: XmlElement): ArrayList<String> {
        val atts = ArrayList<String>()
        for (i in 0 until e.attributeCount) {
            atts.add(e.getAttributeName(i))
        }
        return atts
    }

    fun getUnusedAttributes(e: XmlElement, usedAtts: ArrayList<String>): ArrayList<String> {
        val unusedAtts = getAttributeList(e)
        for (i in 0 until usedAtts.size) {
            if (unusedAtts.contains(usedAtts[i])) {
                unusedAtts.remove(usedAtts[i])
            }
        }
        return unusedAtts
    }

    fun unusedAttWarning(e: XmlElement, usedAtts: ArrayList<String>): String {
        var warning = ""
        val unusedAtts = getUnusedAttributes(e, usedAtts)

        warning += unusedAtts.size.toString() + " unrecognized attributes found in Element [" +
                e.name + "] and will be ignored: "
        warning += "["
        for (i in 0 until unusedAtts.size) {
            warning += unusedAtts[i]
            if (i != unusedAtts.size - 1) {
                warning += ","
            }
        }
        warning += "] "

        return warning
    }

    fun showUnusedAttributeWarning(e: XmlElement, usedAtts: ArrayList<String>): Boolean {
        return getUnusedAttributes(e, usedAtts).size > 0
    }

    fun isOutput(e: XmlElement): Boolean {
        return e.name.lowercase() == "output"
    }
}
