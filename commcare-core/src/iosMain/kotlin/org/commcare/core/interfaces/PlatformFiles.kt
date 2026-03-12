@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.core.interfaces

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.posix.memcpy

/**
 * iOS file system operations using NSFileManager.
 */
actual object PlatformFiles {
    private val fileManager get() = NSFileManager.defaultManager

    actual fun readBytes(path: String): ByteArray {
        val data = fileManager.contentsAtPath(path)
            ?: throw RuntimeException("Cannot read file: $path")
        return data.toByteArray()
    }

    actual fun writeBytes(path: String, data: ByteArray) {
        val nsPath = NSString.create(string = path)
        val parentPath = nsPath.stringByDeletingLastPathComponent
        if (parentPath.isNotEmpty()) {
            val parentExists = fileManager.fileExistsAtPath(parentPath)
            if (!parentExists) {
                fileManager.createDirectoryAtPath(
                    parentPath,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null
                )
            }
        }
        val nsData = data.toNSData()
        nsData.writeToFile(path, atomically = true)
    }

    actual fun exists(path: String): Boolean {
        return fileManager.fileExistsAtPath(path)
    }

    actual fun delete(path: String): Boolean {
        return fileManager.removeItemAtPath(path, error = null)
    }

    actual fun isDirectory(path: String): Boolean {
        memScoped {
            val isDir = alloc<BooleanVar>()
            val exists = fileManager.fileExistsAtPath(path, isDirectory = isDir.ptr)
            return exists && isDir.value
        }
    }

    actual fun listDir(path: String): List<String> {
        val contents = fileManager.contentsOfDirectoryAtPath(path, error = null)
        @Suppress("UNCHECKED_CAST")
        return (contents as? List<String>) ?: emptyList()
    }

    actual fun fileSize(path: String): Long {
        val attrs = fileManager.attributesOfItemAtPath(path, error = null) ?: return 0L
        val size = attrs[NSFileSize]
        return (size as? NSNumber)?.longValue ?: 0L
    }

    actual fun createTempFile(prefix: String, suffix: String): String {
        val tempDir = NSTemporaryDirectory()
        val uuid = NSUUID().UUIDString
        val path = "${tempDir}${prefix}${uuid}${suffix}"
        fileManager.createFileAtPath(path, contents = null, attributes = null)
        return path
    }
}

private fun NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    if (size == 0) return ByteArray(0)
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}

private fun ByteArray.toNSData(): NSData {
    if (this.isEmpty()) return NSData()
    return this.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
    }
}
