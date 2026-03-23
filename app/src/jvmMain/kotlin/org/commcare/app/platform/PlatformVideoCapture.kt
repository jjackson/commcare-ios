package org.commcare.app.platform

/**
 * JVM stub for video capture — always returns null (unavailable).
 */
actual class PlatformVideoCapture actual constructor() {
    actual fun captureVideo(onResult: (String?) -> Unit) {
        onResult(null)
    }
}
