package org.commcare.modern.reference

import org.javarosa.core.reference.Reference

import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream
import org.javarosa.core.io.PlatformOutputStream
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

    @Throws(PlatformIOException::class)
    override fun doesBinaryExist(): Boolean {
        return true
    }

    @Throws(PlatformIOException::class)
    override fun getOutputStream(): PlatformOutputStream {
        throw PlatformIOException("Archive references are read only!")
    }

    @Throws(PlatformIOException::class)
    override fun getStream(): PlatformInputStream {
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

    @Throws(PlatformIOException::class)
    override fun remove() {
        throw PlatformIOException("Cannot remove files from the archive")
    }

    override fun getLocalURI(): String? {
        return null
    }
}
