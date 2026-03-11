package org.javarosa.xform.util

import org.javarosa.core.io.BufferedInputStream
import org.javarosa.core.model.FormDef
import org.javarosa.xform.parse.QuestionExtensionParser
import org.javarosa.xform.parse.XFormParseException
import org.javarosa.xform.parse.XFormParserFactory
import org.kxml2.kdom.Element

import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream
import java.io.InputStreamReader
import java.io.UnsupportedEncodingException

/**
 * Static Utility methods pertaining to XForms.
 *
 * @author Clayton Sims
 */
class XFormUtils {
    companion object {
        private var _factory = XFormParserFactory()

        @JvmStatic
        @Throws(XFormParseException::class)
        fun getFormFromResource(resource: String): FormDef? {
            val `is` = System::class.java.getResourceAsStream(resource)
            if (`is` == null) {
                org.javarosa.core.util.platformStdErrPrintln("Can't find form resource \"$resource\". Is it in the JAR?")
                return null
            }

            return getFormFromInputStream(`is`)
        }

        @JvmStatic
        @Throws(XFormParseException::class, PlatformIOException::class)
        fun getFormRaw(isr: InputStreamReader): FormDef {
            return _factory.getXFormParser(isr).parse()
        }

        @JvmStatic
        @Throws(XFormParseException::class)
        fun getFormFromInputStream(
            `is`: PlatformInputStream,
            extensionParsers: ArrayList<QuestionExtensionParser>
        ): FormDef {
            var inputStream: PlatformInputStream = `is`
            var isr: InputStreamReader

            //Buffer the incoming data, since it's coming from disk.
            inputStream = BufferedInputStream(inputStream)

            try {
                isr = InputStreamReader(inputStream, "UTF-8")
            } catch (uee: UnsupportedEncodingException) {
                println("UTF 8 encoding unavailable, trying default encoding")
                isr = InputStreamReader(inputStream)
            }

            try {
                try {
                    val parser = _factory.getXFormParser(isr)
                    for (p in extensionParsers) {
                        parser.registerExtensionParser(p)
                    }
                    return parser.parse()
                    //TODO: Keep removing these, shouldn't be swallowing them
                } catch (e: PlatformIOException) {
                    throw XFormParseException("IO Exception during parse! " + e.message)
                }
            } finally {
                try {
                    isr.close()
                } catch (e: PlatformIOException) {
                    org.javarosa.core.util.platformStdErrPrintln("IO Exception while closing stream.")
                    e.printStackTrace()
                }
            }
        }

        @JvmStatic
        @Throws(XFormParseException::class)
        fun getFormFromInputStream(`is`: PlatformInputStream): FormDef {
            var inputStream: PlatformInputStream = `is`
            var isr: InputStreamReader

            //Buffer the incoming data, since it's coming from disk.
            inputStream = BufferedInputStream(inputStream)

            try {
                isr = InputStreamReader(inputStream, "UTF-8")
            } catch (uee: UnsupportedEncodingException) {
                println("UTF 8 encoding unavailable, trying default encoding")
                isr = InputStreamReader(inputStream)
            }

            try {
                try {
                    return _factory.getXFormParser(isr).parse()
                    //TODO: Keep removing these, shouldn't be swallowing them
                } catch (e: PlatformIOException) {
                    throw XFormParseException("IO Exception during parse! " + e.message)
                }
            } finally {
                try {
                    isr.close()
                } catch (e: PlatformIOException) {
                    org.javarosa.core.util.platformStdErrPrintln("IO Exception while closing stream.")
                    e.printStackTrace()
                }
            }
        }

        // -------------------------------------------------
        // Attribute parsing validation functions
        // -------------------------------------------------

        /**
         * Get the list of attributes in an element
         */
        @JvmStatic
        fun getAttributeList(e: Element): ArrayList<String> {
            val atts = ArrayList<String>()

            for (i in 0 until e.attributeCount) {
                atts.add(e.getAttributeName(i))
            }

            return atts
        }

        /**
         * @return ArrayList of attributes from 'e' that aren't in 'usedAtts'
         */
        @JvmStatic
        fun getUnusedAttributes(e: Element, usedAtts: ArrayList<String>): ArrayList<String> {
            val unusedAtts = getAttributeList(e)
            for (i in 0 until usedAtts.size) {
                if (unusedAtts.contains(usedAtts[i])) {
                    unusedAtts.remove(usedAtts[i])
                }
            }
            return unusedAtts
        }

        /**
         * @return String warning about which attributes from 'e' aren't in 'usedAtts'
         */
        @JvmStatic
        fun unusedAttWarning(e: Element, usedAtts: ArrayList<String>): String {
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

        /**
         * @return boolean representing whether there are any attributes in 'e' not
         * in 'usedAtts'
         */
        @JvmStatic
        fun showUnusedAttributeWarning(e: Element, usedAtts: ArrayList<String>): Boolean {
            return getUnusedAttributes(e, usedAtts).size > 0
        }

        /**
         * Is this element an Output tag?
         */
        @JvmStatic
        fun isOutput(e: Element): Boolean {
            return e.name.lowercase() == "output"
        }
    }
}
