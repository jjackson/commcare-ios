package org.javarosa.core.reference

import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * A Resource Reference is a reference to a file which
 * is a Java Resource, which is accessible with the
 * 'getResourceAsStream' method from the Java Classloader.
 *
 * Resource References are read only, and can identify with
 * certainty whether a binary file is located at them.
 *
 * @author ctsims
 */
open class ResourceReference(private val uri: String) : Reference {

    @Throws(IOException::class)
    override fun doesBinaryExist(): Boolean {
        // Figure out if there's a file by trying to open
        // a stream to it and determining if it's null.
        val stream = javaClass.getResourceAsStream(uri)
        return if (stream == null) {
            false
        } else {
            stream.close()
            true
        }
    }

    @Throws(IOException::class)
    override fun getStream(): InputStream {
        return javaClass.getResourceAsStream(uri)
            ?: throw FileNotFoundException("File not found when opening resource as stream")
    }

    override fun getURI(): String {
        return "jr://resource$uri"
    }

    override fun getLocalURI(): String {
        return uri
    }

    override fun isReadOnly(): Boolean {
        return true
    }

    override fun equals(other: Any?): Boolean {
        return other is ResourceReference && uri == other.uri
    }

    override fun hashCode(): Int {
        return uri.hashCode()
    }

    @Throws(IOException::class)
    override fun getOutputStream(): OutputStream {
        throw IOException("Resource references are read-only URI's")
    }

    @Throws(IOException::class)
    override fun remove() {
        throw IOException("Resource references are read-only URI's")
    }
}
