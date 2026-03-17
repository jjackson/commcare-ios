package org.commcare.app.oracle

import org.commcare.app.platform.PlatformImageCapture
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Oracle tests for PlatformImageCapture.
 * JVM stub always returns null — verifies the API contract.
 */
class ImageCaptureOracleTest {

    @Test
    fun testCaptureFromCameraReturnsNull() {
        val capture = PlatformImageCapture()
        var result: String? = "not-called"
        capture.captureFromCamera { result = it }
        assertNull(result, "JVM stub should return null for camera capture")
    }

    @Test
    fun testPickFromGalleryReturnsNull() {
        val capture = PlatformImageCapture()
        var result: String? = "not-called"
        capture.pickFromGallery { result = it }
        assertNull(result, "JVM stub should return null for gallery pick")
    }
}
