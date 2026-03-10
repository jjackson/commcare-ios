package org.javarosa.xform.util

import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.xml.dom.XmlDocument
import org.javarosa.xml.dom.XmlDomWriter
import org.javarosa.xml.dom.XmlElement

class XFormSerializer {
    companion object {
        @JvmStatic
        fun elementToString(e: XmlElement): String? {
            return XmlDomWriter.elementToString(e)
        }

        /**
         * Formats an XML document into a UTF-8 (no BOM) compatible format
         *
         * @return The raw bytes of the utf-8 encoded doc
         * @throws PlatformIOException If there is an issue transferring
         *                             the bytes to a byte stream.
         * @throws UnsupportedUnicodeSurrogatesException If the document contains values
         *                                               that are not UTF-8 encoded.
         */
        @JvmStatic
        @Throws(PlatformIOException::class)
        fun getUtfBytesFromDocument(doc: XmlDocument): ByteArray {
            return XmlDomWriter.getUtfBytesFromDocument(doc)
        }
    }

    class UnsupportedUnicodeSurrogatesException(message: String) : RuntimeException(message)
}
