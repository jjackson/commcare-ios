package org.commcare.core.interfaces

/**
 * iOS file system operations.
 * Will use NSFileManager via Kotlin/Native interop.
 */
actual object PlatformFiles {
    actual fun readBytes(path: String): ByteArray {
        TODO("iOS readBytes requires NSFileManager cinterop")
    }

    actual fun writeBytes(path: String, data: ByteArray) {
        TODO("iOS writeBytes requires NSFileManager cinterop")
    }

    actual fun exists(path: String): Boolean {
        TODO("iOS exists requires NSFileManager cinterop")
    }

    actual fun delete(path: String): Boolean {
        TODO("iOS delete requires NSFileManager cinterop")
    }

    actual fun isDirectory(path: String): Boolean {
        TODO("iOS isDirectory requires NSFileManager cinterop")
    }

    actual fun listDir(path: String): List<String> {
        TODO("iOS listDir requires NSFileManager cinterop")
    }

    actual fun fileSize(path: String): Long {
        TODO("iOS fileSize requires NSFileManager cinterop")
    }

    actual fun createTempFile(prefix: String, suffix: String): String {
        TODO("iOS createTempFile requires NSTemporaryDirectory cinterop")
    }
}
