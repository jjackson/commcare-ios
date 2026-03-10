package org.commcare.core.interfaces

import java.io.File

actual object PlatformFiles {
    actual fun readBytes(path: String): ByteArray = File(path).readBytes()
    actual fun writeBytes(path: String, data: ByteArray) = File(path).writeBytes(data)
    actual fun exists(path: String): Boolean = File(path).exists()
    actual fun delete(path: String): Boolean = File(path).delete()
    actual fun isDirectory(path: String): Boolean = File(path).isDirectory
    actual fun listDir(path: String): List<String> = File(path).list()?.toList() ?: emptyList()
    actual fun fileSize(path: String): Long = File(path).length()
    actual fun createTempFile(prefix: String, suffix: String): String =
        File.createTempFile(prefix, suffix).absolutePath
}
