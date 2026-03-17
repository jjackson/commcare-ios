package org.commcare.app.oracle

import org.commcare.app.platform.LocationResult
import org.commcare.app.platform.PlatformLocationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Oracle tests for PlatformLocationProvider.
 * JVM stub always returns null — verifies the API contract.
 */
class LocationProviderOracleTest {

    @Test
    fun testRequestLocationReturnsNull() {
        val provider = PlatformLocationProvider()
        var result: LocationResult? = LocationResult(0.0, 0.0, 0.0, 0.0) // sentinel
        provider.requestLocation { result = it }
        assertNull(result, "JVM stub should return null for location request")
    }

    @Test
    fun testLocationResultToGeoPointString() {
        val result = LocationResult(
            latitude = 42.3601,
            longitude = -71.0589,
            altitude = 10.0,
            accuracy = 5.0
        )
        assertEquals("42.3601 -71.0589 10.0 5.0", result.toGeoPointString())
    }
}
