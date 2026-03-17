@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLLocation
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.darwin.NSObject

actual class PlatformLocationProvider actual constructor() {
    actual fun requestLocation(onResult: (LocationResult?) -> Unit) {
        val manager = CLLocationManager()
        val delegate = LocationDelegate(manager, onResult)
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest

        val status = CLLocationManager.authorizationStatus()
        if (status == kCLAuthorizationStatusNotDetermined) {
            manager.requestWhenInUseAuthorization()
        }

        if (status == kCLAuthorizationStatusAuthorizedWhenInUse ||
            status == kCLAuthorizationStatusAuthorizedAlways
        ) {
            manager.requestLocation()
        } else {
            // Will request after authorization change
            manager.requestWhenInUseAuthorization()
        }
    }
}

private class LocationDelegate(
    private val manager: CLLocationManager,
    private val onResult: (LocationResult?) -> Unit
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation
        if (location != null) {
            onResult(
                LocationResult(
                    latitude = location.coordinate.latitude,
                    longitude = location.coordinate.longitude,
                    altitude = location.altitude,
                    accuracy = location.horizontalAccuracy
                )
            )
        } else {
            onResult(null)
        }
        manager.delegate = null
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: platform.Foundation.NSError) {
        onResult(null)
        manager.delegate = null
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        val status = CLLocationManager.authorizationStatus()
        if (status == kCLAuthorizationStatusAuthorizedWhenInUse ||
            status == kCLAuthorizationStatusAuthorizedAlways
        ) {
            manager.requestLocation()
        }
    }
}
