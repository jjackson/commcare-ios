package org.commcare.modern.reference

import org.javarosa.core.reference.Reference

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

/**
 * An archive file reference retrieves a binary file from a path within a zip
 * file registerd with the appropriate root.
 *
 * @author ctsims
 */
class ArchiveFileReference(
    /**
     * @param zipFile    The host file
     * @param guid       The guid registered with the existing root
     * @param archiveURI a local path to the file being referenced
     */
    private val mZipFile: ZipFile,
    private val guid: String,
    private val archiveURI: String
) : Reference {

    @Throws(IOException::class)
    override fun doesBinaryExist(): Boolean {
        return true
    }

    @Throws(IOException::class)
    override fun getOutputStream(): OutputStream {
        throw IOException("Archive references are read only!")
    }

    @Throws(IOException::class)
    override fun getStream(): InputStream {
        try {
            return mZipFile.getInputStream(mZipFile.getEntry(archiveURI))
        } catch (e: NullPointerException) {
            val reference = mZipFile.name ?: ""
            val re = RuntimeException(
                String.format(
                    "ZipFile %s threw NullPointerException with URI %s in archive with GUID %s.",
                    reference, archiveURI, guid
                )
            )
            re.initCause(e)
            throw re
        }
    }

    override fun getURI(): String {
        return "jr://archive/$guid/$archiveURI"
    }

    override fun isReadOnly(): Boolean {
        return true
    }

    @Throws(IOException::class)
    override fun remove() {
        throw IOException("Cannot remove files from the archive")
    }

    override fun getLocalURI(): String? {
        return null
    }
}
