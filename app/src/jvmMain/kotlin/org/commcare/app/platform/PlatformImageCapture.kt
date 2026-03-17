package org.commcare.app.platform

/**
 * JVM stub for image capture — always returns null (unavailable).
 */
actual class PlatformImageCapture actual constructor() {
    actual fun captureFromCamera(onResult: (String?) -> Unit) {
        onResult(null)
    }

    actual fun pickFromGallery(onResult: (String?) -> Unit) {
        onResult(null)
    }
}
