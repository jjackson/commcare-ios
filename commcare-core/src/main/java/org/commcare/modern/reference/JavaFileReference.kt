package org.commcare.modern.reference

import org.javarosa.core.reference.Reference

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream
import org.javarosa.core.io.PlatformOutputStream
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * @author ctsims
 */
open class JavaFileReference @JvmOverloads constructor(
    @JvmField val localPart: String,
    @JvmField val uri: String,
    @JvmField val authority: String = "file"
) : Reference {

    @Throws(PlatformIOException::class)
    override fun doesBinaryExist(): Boolean {
        return file().exists()
    }

    @Throws(PlatformIOException::class)
    override fun getOutputStream(): PlatformOutputStream {
        return FileOutputStream(file())
    }

    @Throws(PlatformIOException::class)
    override fun getStream(): PlatformInputStream {
        val file = file()
        if (!file.exists()) {
            if (!file.createNewFile()) {
                throw PlatformIOException("Could not create file at URI " + file.absolutePath)
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

    @Throws(PlatformIOException::class)
    override fun remove() {
        val file = file()
        if (!file.delete()) {
            throw PlatformIOException("Could not delete file at URI " + file.absolutePath)
        }
    }

    private fun file(): File {
        return File(localURI)
    }

    override fun getLocalURI(): String {
        return localPart + File.separator + uri
    }
}
