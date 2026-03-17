package org.commcare.app.platform

/**
 * JVM stub for barcode scanner — always returns null (unavailable).
 */
actual class PlatformBarcodeScanner actual constructor() {
    actual fun scanBarcode(onResult: (String?) -> Unit) {
        onResult(null)
    }
}
