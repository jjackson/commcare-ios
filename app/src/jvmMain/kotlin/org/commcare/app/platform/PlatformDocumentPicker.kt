package org.commcare.app.platform

/**
 * JVM stub for document picker — always returns null (unavailable).
 */
actual class PlatformDocumentPicker actual constructor() {
    actual fun pickDocument(onResult: (String?) -> Unit) {
        onResult(null)
    }
}
