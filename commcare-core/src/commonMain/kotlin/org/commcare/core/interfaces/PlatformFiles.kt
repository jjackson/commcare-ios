package org.commcare.core.interfaces

/**
 * Cross-platform file system operations.
 * Replaces java.io.File and java.nio.file for KMP compatibility.
 */
expect object PlatformFiles {
    fun readBytes(path: String): ByteArray
    fun writeBytes(path: String, data: ByteArray)
    fun exists(path: String): Boolean
    fun delete(path: String): Boolean
    fun isDirectory(path: String): Boolean
    fun listDir(path: String): List<String>
    fun fileSize(path: String): Long
    fun createTempFile(prefix: String, suffix: String): String
}
