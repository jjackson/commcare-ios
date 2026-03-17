package org.commcare.app.platform

/**
 * Platform-specific signature capture (drawing pad).
 * iOS uses custom UIView + CGContext. JVM stub returns null.
 */
expect class PlatformSignatureCapture() {
    /**
     * Launch signature capture UI.
     * @param onResult Callback with the file path of the signature image, or null if cancelled.
     */
    fun captureSignature(onResult: (String?) -> Unit)
}
