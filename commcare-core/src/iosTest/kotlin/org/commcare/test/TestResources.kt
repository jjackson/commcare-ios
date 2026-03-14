package org.commcare.test

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import kotlinx.cinterop.refTo

@OptIn(ExperimentalForeignApi::class)
actual object TestResources {
    /**
     * Load test resource from the commonTest/resources directory.
     * On iOS/Native, reads from the filesystem relative to the project root.
     *
     * The path should start with "/" (e.g., "/test_all_question_types.xml").
     */
    actual fun loadResource(path: String): ByteArray {
        // Strip leading "/" from resource path
        val relativePath = path.trimStart('/')

        // Try multiple base paths that may apply depending on where tests run
        val candidates = listOf(
            "src/commonTest/resources/$relativePath",
            "../commcare-core/src/commonTest/resources/$relativePath",
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
            "Test resource not found: $path (tried: ${candidates.joinToString()})"
        )
    }
}
