package org.commcare.app.oracle

import org.commcare.app.platform.PlatformSignatureCapture
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Oracle tests for PlatformSignatureCapture.
 * JVM stub always returns null — verifies the API contract.
 */
class SignatureCaptureOracleTest {

    @Test
    fun testCaptureSignatureReturnsNull() {
        val capture = PlatformSignatureCapture()
        var result: String? = "not-called"
        capture.captureSignature { result = it }
        assertNull(result, "JVM stub should return null for signature capture")
    }
}
