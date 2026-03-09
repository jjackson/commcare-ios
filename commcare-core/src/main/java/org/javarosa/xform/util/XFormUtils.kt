package org.javarosa.xform.util

import org.javarosa.core.io.BufferedInputStream
import org.javarosa.core.model.FormDef
import org.javarosa.xform.parse.QuestionExtensionParser
import org.javarosa.xform.parse.XFormParseException
import org.javarosa.xform.parse.XFormParserFactory
import org.kxml2.kdom.Element

import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.UnsupportedEncodingException
import java.util.Vector

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
                System.err.println("Can't find form resource \"$resource\". Is it in the JAR?")
                return null
            }

            return getFormFromInputStream(`is`)
        }

        @JvmStatic
        @Throws(XFormParseException::class, IOException::class)
        fun getFormRaw(isr: InputStreamReader): FormDef {
            return _factory.getXFormParser(isr).parse()
        }

        @JvmStatic
        @Throws(XFormParseException::class)
        fun getFormFromInputStream(
            `is`: InputStream,
            extensionParsers: Vector<QuestionExtensionParser>
        ): FormDef {
            var inputStream: InputStream = `is`
            var isr: InputStreamReader

            //Buffer the incoming data, since it's coming from disk.
            inputStream = BufferedInputStream(inputStream)

            try {
                isr = InputStreamReader(inputStream, "UTF-8")
            } catch (uee: UnsupportedEncodingException) {
                System.out.println("UTF 8 encoding unavailable, trying default encoding")
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
                } catch (e: IOException) {
                    throw XFormParseException("IO Exception during parse! " + e.message)
                }
            } finally {
                try {
                    isr.close()
                } catch (e: IOException) {
                    System.err.println("IO Exception while closing stream.")
                    e.printStackTrace()
                }
            }
        }

        @JvmStatic
        @Throws(XFormParseException::class)
        fun getFormFromInputStream(`is`: InputStream): FormDef {
            var inputStream: InputStream = `is`
            var isr: InputStreamReader

            //Buffer the incoming data, since it's coming from disk.
            inputStream = BufferedInputStream(inputStream)

            try {
                isr = InputStreamReader(inputStream, "UTF-8")
            } catch (uee: UnsupportedEncodingException) {
                System.out.println("UTF 8 encoding unavailable, trying default encoding")
                isr = InputStreamReader(inputStream)
            }

            try {
                try {
                    return _factory.getXFormParser(isr).parse()
                    //TODO: Keep removing these, shouldn't be swallowing them
                } catch (e: IOException) {
                    throw XFormParseException("IO Exception during parse! " + e.message)
                }
            } finally {
                try {
                    isr.close()
                } catch (e: IOException) {
                    System.err.println("IO Exception while closing stream.")
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
        fun getAttributeList(e: Element): Vector<String> {
            val atts = Vector<String>()

            for (i in 0 until e.attributeCount) {
                atts.addElement(e.getAttributeName(i))
            }

            return atts
        }

        /**
         * @return Vector of attributes from 'e' that aren't in 'usedAtts'
         */
        @JvmStatic
        fun getUnusedAttributes(e: Element, usedAtts: Vector<String>): Vector<String> {
            val unusedAtts = getAttributeList(e)
            for (i in 0 until usedAtts.size) {
                if (unusedAtts.contains(usedAtts.elementAt(i))) {
                    unusedAtts.removeElement(usedAtts.elementAt(i))
                }
            }
            return unusedAtts
        }

        /**
         * @return String warning about which attributes from 'e' aren't in 'usedAtts'
         */
        @JvmStatic
        fun unusedAttWarning(e: Element, usedAtts: Vector<String>): String {
            var warning = ""
            val unusedAtts = getUnusedAttributes(e, usedAtts)

            warning += unusedAtts.size.toString() + " unrecognized attributes found in Element [" +
                    e.name + "] and will be ignored: "
            warning += "["
            for (i in 0 until unusedAtts.size) {
                warning += unusedAtts.elementAt(i)
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
        fun showUnusedAttributeWarning(e: Element, usedAtts: Vector<String>): Boolean {
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
