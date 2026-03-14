package org.commcare.test

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.getcwd

@OptIn(ExperimentalForeignApi::class)
actual object TestResources {
    actual fun loadResource(path: String): ByteArray {
        val relativePath = path.trimStart('/')

        // Try multiple base paths — the working directory varies between
        // local dev, CI, and where the native test binary runs from.
        val candidates = listOf(
            // From project root (commcare-core/)
            "src/commonTest/resources/$relativePath",
            // From repo root
            "commcare-core/src/commonTest/resources/$relativePath",
            // From repo root (via ..)
            "../commcare-core/src/commonTest/resources/$relativePath",
            // From build output directory (build/bin/iosSimulatorArm64/debugTest/)
            "../../../../src/commonTest/resources/$relativePath",
            // From build directory (build/)
            "../src/commonTest/resources/$relativePath"
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

        // Get CWD for debugging
        val cwdBuf = ByteArray(1024)
        val cwd = getcwd(cwdBuf.refTo(0), 1024u)?.toKString() ?: "unknown"

        throw IllegalArgumentException(
            "Test resource not found: $path\n" +
            "  CWD: $cwd\n" +
            "  Tried: ${candidates.joinToString("\n         ")}"
        )
    }
}
