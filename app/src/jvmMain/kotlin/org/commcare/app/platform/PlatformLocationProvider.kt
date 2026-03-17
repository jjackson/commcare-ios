package org.commcare.app.platform

/**
 * JVM stub for location provider — always returns null (unavailable).
 */
actual class PlatformLocationProvider actual constructor() {
    actual fun requestLocation(onResult: (LocationResult?) -> Unit) {
        onResult(null)
    }
}
