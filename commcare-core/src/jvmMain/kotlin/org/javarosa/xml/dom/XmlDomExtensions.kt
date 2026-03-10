package org.javarosa.xml.dom

import org.javarosa.xml.JvmXmlSerializer
import org.kxml2.io.KXmlSerializer
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStreamWriter

/**
 * JVM-only extensions for kxml2 DOM serialization.
 * These provide the bridge between PlatformXmlSerializer and kxml2's write() methods.
 */
object XmlDomWriter {

    /**
     * Serialize an Element to a String.
     */
    @JvmStatic
    fun elementToString(e: XmlElement): String? {
        val serializer = KXmlSerializer()
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        try {
            serializer.setOutput(dos, null)
            e.write(serializer)
            serializer.flush()
            return String(bos.toByteArray(), Charsets.UTF_8)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }
    }

    /**
     * Serialize a Document to UTF-8 bytes, checking for unsupported unicode surrogates.
     */
    @JvmStatic
    fun getUtfBytesFromDocument(doc: XmlDocument): ByteArray {
        val serializer = object : KXmlSerializer() {
            override fun text(text: String): org.xmlpull.v1.XmlSerializer {
                try {
                    return super.text(text)
                } catch (e: IllegalArgumentException) {
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

    class UnsupportedUnicodeSurrogatesException(message: String) : RuntimeException(message)
}
