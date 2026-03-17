package org.commcare.app.platform

/**
 * Platform-specific GPS location provider.
 * iOS uses CLLocationManager. JVM stub returns null.
 */
expect class PlatformLocationProvider() {
    /**
     * Request a single location fix.
     * @param onResult Callback with LocationResult containing lat/lon/alt/accuracy, or null if unavailable.
     */
    fun requestLocation(onResult: (LocationResult?) -> Unit)
}

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Double
) {
    /**
     * Format as CommCare geopoint string: "lat lon alt accuracy"
     */
    fun toGeoPointString(): String = "$latitude $longitude $altitude $accuracy"
}
