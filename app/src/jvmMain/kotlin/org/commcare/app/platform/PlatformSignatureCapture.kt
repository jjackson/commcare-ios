package org.commcare.app.platform

/**
 * JVM stub for signature capture — always returns null (unavailable).
 */
actual class PlatformSignatureCapture actual constructor() {
    actual fun captureSignature(onResult: (String?) -> Unit) {
        onResult(null)
    }
}
