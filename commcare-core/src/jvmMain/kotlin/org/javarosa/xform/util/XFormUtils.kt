package org.javarosa.xform.util

import org.javarosa.core.model.FormDef
import org.javarosa.xform.parse.QuestionExtensionParser
import org.javarosa.xform.parse.XFormParseException
import org.javarosa.xform.parse.XFormParserFactory

import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream
import java.io.InputStreamReader
import kotlin.jvm.JvmStatic

/**
 * Static Utility methods pertaining to XForms.
 *
 * JVM-only convenience layer over the cross-platform XFormParser.
 * Handles PlatformInputStream → ByteArray conversion for backward compatibility.
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
            val bytes = isr.readText().toByteArray(Charsets.UTF_8)
            return _factory.getXFormParser(bytes).parse()
        }

        @JvmStatic
        @Throws(XFormParseException::class)
        fun getFormFromInputStream(
            `is`: PlatformInputStream,
            extensionParsers: ArrayList<QuestionExtensionParser>
        ): FormDef {
            try {
                val bytes = `is`.readBytes()
                val parser = _factory.getXFormParser(bytes)
                for (p in extensionParsers) {
                    parser.registerExtensionParser(p)
                }
                return parser.parse()
            } catch (e: PlatformIOException) {
                throw XFormParseException("IO Exception during parse! " + e.message)
            }
        }

        @JvmStatic
        @Throws(XFormParseException::class)
        fun getFormFromInputStream(`is`: PlatformInputStream): FormDef {
            try {
                val bytes = `is`.readBytes()
                return _factory.getXFormParser(bytes).parse()
            } catch (e: PlatformIOException) {
                throw XFormParseException("IO Exception during parse! " + e.message)
            }
        }
    }
}
