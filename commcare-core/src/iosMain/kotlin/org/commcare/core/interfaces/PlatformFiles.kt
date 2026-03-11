package org.commcare.core.interfaces

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringByAppendingPathComponent
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * iOS file system operations using NSFileManager.
 */
@OptIn(ExperimentalForeignApi::class)
actual object PlatformFiles {

    private val fileManager: NSFileManager
        get() = NSFileManager.defaultManager

    actual fun readBytes(path: String): ByteArray {
        val data = NSData.dataWithContentsOfFile(path)
            ?: throw RuntimeException("Cannot read file: $path")
        val size = data.length.toInt()
        if (size == 0) return ByteArray(0)
        val bytes = ByteArray(size)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
        return bytes
    }

    actual fun writeBytes(path: String, data: ByteArray) {
        val nsData = if (data.isEmpty()) {
            NSData()
        } else {
            data.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
            }
        }
        val success = nsData.writeToFile(path, atomically = true)
        if (!success) {
            throw RuntimeException("Cannot write file: $path")
        }
    }

    actual fun exists(path: String): Boolean {
        return fileManager.fileExistsAtPath(path)
    }

    actual fun delete(path: String): Boolean {
        return try {
            fileManager.removeItemAtPath(path, error = null)
            true
        } catch (_: Exception) {
            false
        }
    }

    actual fun isDirectory(path: String): Boolean {
        memScoped {
            val isDir = alloc<BooleanVar>()
            val exists = fileManager.fileExistsAtPath(path, isDirectory = isDir.ptr)
            return exists && isDir.value
        }
    }

    actual fun listDir(path: String): List<String> {
        return fileManager.contentsOfDirectoryAtPath(path, error = null)
            ?.mapNotNull { it as? String }
            ?: emptyList()
    }

    actual fun fileSize(path: String): Long {
        val attrs = fileManager.attributesOfItemAtPath(path, error = null)
            ?: return 0L
        val size = attrs[NSFileSize] as? NSNumber
        return size?.longLongValue ?: 0L
    }

    actual fun createTempFile(prefix: String, suffix: String): String {
        val tempDir = NSTemporaryDirectory()
        val uniqueId = NSProcessInfo.processInfo.globallyUniqueString
        val fileName = "$prefix$uniqueId$suffix"
        @Suppress("CAST_NEVER_SUCCEEDS")
        val path = (tempDir as NSString).stringByAppendingPathComponent(fileName)
        fileManager.createFileAtPath(path, contents = null, attributes = null)
        return path
    }
}
