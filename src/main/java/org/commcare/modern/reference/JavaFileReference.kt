package org.commcare.modern.reference

import org.javarosa.core.reference.Reference

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * @author ctsims
 */
open class JavaFileReference @JvmOverloads constructor(
    @JvmField val localPart: String,
    @JvmField val uri: String,
    @JvmField val authority: String = "file"
) : Reference {

    @Throws(IOException::class)
    override fun doesBinaryExist(): Boolean {
        return file().exists()
    }

    @Throws(IOException::class)
    override fun getOutputStream(): OutputStream {
        return FileOutputStream(file())
    }

    @Throws(IOException::class)
    override fun getStream(): InputStream {
        val file = file()
        if (!file.exists()) {
            if (!file.createNewFile()) {
                throw IOException("Could not create file at URI " + file.absolutePath)
            }
        }
        return FileInputStream(file)
    }

    override fun getURI(): String {
        return "jr://$authority/$uri"
    }

    override fun isReadOnly(): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun remove() {
        val file = file()
        if (!file.delete()) {
            throw IOException("Could not delete file at URI " + file.absolutePath)
        }
    }

    private fun file(): File {
        return File(localURI)
    }

    override fun getLocalURI(): String {
        return localPart + File.separator + uri
    }
}
