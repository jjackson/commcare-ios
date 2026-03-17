package org.commcare.app.oracle

import org.commcare.app.platform.LocationResult
import org.commcare.app.platform.PlatformBarcodeScanner
import org.commcare.app.platform.PlatformLocationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Combined oracle test for location and barcode functionality.
 * Tests the platform abstractions and data models used by FormEntryScreen.
 */
class LocationBarcodeOracleTest {

    @Test
    fun testLocationResultFormatting() {
        val loc = LocationResult(40.7128, -74.0060, 15.0, 3.5)
        assertEquals("40.7128 -74.006 15.0 3.5", loc.toGeoPointString())
    }

    @Test
    fun testLocationResultZeroValues() {
        val loc = LocationResult(0.0, 0.0, 0.0, 0.0)
        assertEquals("0.0 0.0 0.0 0.0", loc.toGeoPointString())
    }

    @Test
    fun testLocationResultNegativeCoordinates() {
        val loc = LocationResult(-33.8688, 151.2093, 0.0, 10.0)
        assertEquals("-33.8688 151.2093 0.0 10.0", loc.toGeoPointString())
    }

    @Test
    fun testLocationProviderJvmStubReturnsNull() {
        val provider = PlatformLocationProvider()
        var called = false
        provider.requestLocation { result ->
            assertNull(result)
            called = true
        }
        assertTrue(called, "Callback should be invoked synchronously on JVM")
    }

    @Test
    fun testBarcodeScannerJvmStubReturnsNull() {
        val scanner = PlatformBarcodeScanner()
        var called = false
        scanner.scanBarcode { result ->
            assertNull(result)
            called = true
        }
        assertTrue(called, "Callback should be invoked synchronously on JVM")
    }

    @Test
    fun testLocationResultDataClassEquality() {
        val a = LocationResult(42.0, -71.0, 10.0, 5.0)
        val b = LocationResult(42.0, -71.0, 10.0, 5.0)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
