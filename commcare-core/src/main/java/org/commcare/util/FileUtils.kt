package org.commcare.util

import org.commcare.cases.util.StringUtils
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.util.ArrayUtilities
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant

/**
 * Common file operations
 */
object FileUtils {

    /**
     * Makes a copy of file represented by inputStream to dstFile
     *
     * @param inputStream inputStream for File that needs to be copied
     * @param dstFile     destination File where we need to copy the inputStream
     */
    @JvmStatic
    @Throws(PlatformIOException::class)
    fun copyFile(inputStream: PlatformInputStream?, dstFile: File) {
        if (inputStream == null) {
            return
        }
        try {
            FileOutputStream(dstFile).use { outputStream ->
                StreamsUtil.writeFromInputToOutputUnmanaged(inputStream, outputStream)
            }
        } finally {
            inputStream.close()
        }
    }

    /**
     * Tries to get content type of a file
     *
     * @param file File we need to know the content type for
     * @return content type for the given file or null
     */
    @JvmStatic
    fun getContentType(file: File): String? {
        try {
            val fis = FileInputStream(file)
            val contentType = java.net.URLConnection.guessContentTypeFromStream(fis)
            if (!StringUtils.isEmpty(contentType)) {
                return contentType
            }
        } catch (e: PlatformIOException) {
            e.printStackTrace()
        }
        return java.net.URLConnection.guessContentTypeFromName(file.name)
    }

    /**
     * Extracts extension of a file from its name
     *
     * @param file name or path for the file
     * @return extension of given file
     */
    @JvmStatic
    fun getExtension(file: String?): String {
        if (file != null && file.contains(".")) {
            return ArrayUtilities.last(file.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        }
        return ""
    }

    /**
     * Delete files inside the given folder if they were last accessed before the given cutOff time
     *
     * @param folder folder for which we want to delete the files
     * @param cutOff cutOff time before which we want to delete the files
     * @return number of files deleted
     */
    @JvmStatic
    @Throws(PlatformIOException::class)
    fun deleteFiles(folder: File, cutOff: Instant): Int {
        val files = folder.listFiles()
        var count = 0
        for (file in files!!) {
            if (file.isDirectory) {
                deleteFiles(file, cutOff)
            } else {
                deleteFile(file, cutOff)
                count++
            }
        }
        return count
    }

    /**
     * Delete given file if it was last accessed before the given cutOff time
     *
     * @param file   file to be deleted
     * @param cutOff cutOff time before which we want to delete the file
     */
    @JvmStatic
    @Throws(PlatformIOException::class)
    fun deleteFile(file: File, cutOff: Instant) {
        val attr = Files.readAttributes(Paths.get(file.path), BasicFileAttributes::class.java)
        val lastAccessTime = attr.lastAccessTime()
        if (lastAccessTime.toInstant().isBefore(cutOff)) {
            file.delete()
        }
    }
}
