package org.commcare.app.oracle

import org.commcare.app.platform.PlatformBarcodeScanner
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Oracle tests for PlatformBarcodeScanner.
 * JVM stub always returns null — verifies the API contract.
 */
class BarcodeScannerOracleTest {

    @Test
    fun testScanBarcodeReturnsNull() {
        val scanner = PlatformBarcodeScanner()
        var result: String? = "not-called"
        scanner.scanBarcode { result = it }
        assertNull(result, "JVM stub should return null for barcode scan")
    }
}
