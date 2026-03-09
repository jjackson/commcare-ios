package org.commcare.modern.reference

import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.core.reference.Reference
import org.javarosa.core.reference.ReferenceFactory
import org.javarosa.core.reference.ReferenceManager
import org.javarosa.core.util.PropertyUtils

import java.util.HashMap
import java.util.zip.ZipFile

/**
 * @author wspride
 *         This class managers references between GUIDs and the associated path in the file system
 *         To register an archive file with this system call addArchiveFile(filepath) - this will return a GUID
 *         This GUID will allow you to derive files from this location using the ArchiveFileRefernece class
 */
open class ArchiveFileRoot : ReferenceFactory {

    @Throws(InvalidReferenceException::class)
    override fun derive(guidPath: String): Reference {
        return ArchiveFileReference(guidToFolderMap[getGUID(guidPath)]!!, getGUID(guidPath), getPath(guidPath))
    }

    @Throws(InvalidReferenceException::class)
    override fun derive(URI: String, context: String): Reference {
        var ctx = context
        if (ctx.lastIndexOf('/') != -1) {
            ctx = ctx.substring(0, ctx.lastIndexOf('/') + 1)
        }
        return ReferenceManager.instance().DeriveReference(ctx + URI)
    }

    override fun derives(URI: String): Boolean {
        return URI.lowercase().startsWith("jr://archive/")
    }

    fun addArchiveFile(zipFile: ZipFile): String {
        return addArchiveFile(zipFile, null)
    }

    fun addArchiveFile(zip: ZipFile, appId: String?): String {
        val mGUID: String
        if (appId == null) {
            mGUID = PropertyUtils.genGUID(GUID_LENGTH)
        } else {
            mGUID = appId
        }
        guidToFolderMap[mGUID] = zip
        return mGUID
    }

    protected fun getGUID(jrpath: String): String {
        val prependRemoved = jrpath.substring("jr://archive/".length)
        val slashindex = prependRemoved.indexOf("/")
        return prependRemoved.substring(0, slashindex)
    }

    protected fun getPath(jrpath: String): String {
        val mGUID = getGUID(jrpath)
        val mIndex = jrpath.indexOf(mGUID)
        return jrpath.substring(mIndex + mGUID.length + 1)
    }

    companion object {
        @JvmStatic
        protected val guidToFolderMap = HashMap<String, ZipFile>()

        @JvmStatic
        protected val GUID_LENGTH = 10
    }
}
