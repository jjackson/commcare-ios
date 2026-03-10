package org.javarosa.xform.util

import org.kxml2.io.KXmlSerializer
import org.kxml2.kdom.Document
import org.kxml2.kdom.Element
import org.xmlpull.v1.XmlSerializer

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.io.OutputStreamWriter
import java.io.UnsupportedEncodingException

class XFormSerializer {
    companion object {
        @JvmStatic
        fun elementToString(e: Element): String? {
            val serializer = KXmlSerializer()

            val bos = ByteArrayOutputStream()
            val dos = DataOutputStream(bos)
            try {
                serializer.setOutput(dos, null)
                e.write(serializer)
                serializer.flush()
                return String(bos.toByteArray(), Charsets.UTF_8)
            } catch (uce: UnsupportedEncodingException) {
                uce.printStackTrace()
            } catch (ex: Exception) {
                ex.printStackTrace()
                return null
            }

            return null
        }

        /**
         * Formats an XML document into a UTF-8 (no BOM) compatible format
         *
         * @return The raw bytes of the utf-8 encoded doc
         * @throws PlatformIOException                           If there is an issue transferring
         *                                               the bytes to a byte stream.
         * @throws UnsupportedUnicodeSurrogatesException If the document contains values
         *                                               that are not UTF-8 encoded.
         */
        @JvmStatic
        @Throws(PlatformIOException::class)
        fun getUtfBytesFromDocument(doc: Document): ByteArray {
            val serializer = object : KXmlSerializer() {
                @Throws(PlatformIOException::class)
                override fun text(text: String): XmlSerializer {
                    try {
                        return super.text(text)
                    } catch (e: IllegalArgumentException) {
                        // certain versions of Android have trouble encoding
                        // unicode characters that require "surrogates".
                        throw UnsupportedUnicodeSurrogatesException(text)
                    }
                }
            }
            val bos = ByteArrayOutputStream()
            val osw = OutputStreamWriter(bos, "UTF-8")
            serializer.setOutput(osw)
            doc.write(serializer)
            serializer.flush()
            return bos.toByteArray()
        }
    }

    class UnsupportedUnicodeSurrogatesException(message: String) : RuntimeException(message)
}
