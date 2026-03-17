package org.commcare.app.platform

/**
 * Platform-specific barcode/QR code scanner.
 * iOS uses AVCaptureSession + AVCaptureMetadataOutput. JVM stub returns null.
 */
expect class PlatformBarcodeScanner() {
    /**
     * Launch barcode scanner UI.
     * @param onResult Callback with the scanned barcode string, or null if cancelled.
     */
    fun scanBarcode(onResult: (String?) -> Unit)
}
