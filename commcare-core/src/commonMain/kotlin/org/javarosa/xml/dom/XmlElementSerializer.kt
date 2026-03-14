package org.javarosa.xml.dom

import org.javarosa.xml.createXmlSerializer

/**
 * Cross-platform serialization of XmlElement to string.
 * Replaces kxml2-based XFormSerializer.elementToString().
 */
object XmlElementSerializer {

    fun elementToString(e: XmlElement): String? {
        return try {
            val serializer = createXmlSerializer()
            e.write(serializer)
            serializer.flush()
            serializer.toByteArray().decodeToString()
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }
}
