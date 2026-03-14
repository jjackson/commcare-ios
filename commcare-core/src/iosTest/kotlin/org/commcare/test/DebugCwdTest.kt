package org.commcare.test

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.getcwd
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Temporary debug test to determine working directory on iOS CI.
 */
class DebugCwdTest {

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun printWorkingDirectory() {
        val cwdBuf = ByteArray(2048)
        val cwd = getcwd(cwdBuf.refTo(0), 2048u)?.toKString() ?: "UNKNOWN"
        println("DEBUG_CWD: $cwd")
        // Force the test to FAIL with the CWD in the message so it shows in CI logs
        assertTrue(false, "CWD=$cwd")
    }
}
