package org.commcare.test

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell

@OptIn(ExperimentalForeignApi::class)
actual object TestResources {
    actual fun loadResource(path: String): ByteArray {
        val relativePath = path.trimStart('/')

        // Use the absolute path generated at build time (IOS_TEST_RESOURCE_PATH).
        // Kotlin/Native simulator tests run via xcrun simctl with an unpredictable
        // working directory, so relative paths are unreliable.
        val candidates = listOf(
            "$IOS_TEST_RESOURCE_PATH/$relativePath",
            // Fallbacks for local dev where CWD may be the project directory
            "src/commonTest/resources/$relativePath",
            "commcare-core/src/commonTest/resources/$relativePath"
        )

        for (candidate in candidates) {
            val file = fopen(candidate, "rb") ?: continue
            try {
                fseek(file, 0, SEEK_END)
                val size = ftell(file).toInt()
                fseek(file, 0, SEEK_SET)

                val buffer = ByteArray(size)
                fread(buffer.refTo(0), 1u, size.toULong(), file)
                return buffer
            } finally {
                fclose(file)
            }
        }

        throw IllegalArgumentException(
            "Test resource not found: $path\n" +
            "  Tried: ${candidates.joinToString("\n         ")}"
        )
    }
}
