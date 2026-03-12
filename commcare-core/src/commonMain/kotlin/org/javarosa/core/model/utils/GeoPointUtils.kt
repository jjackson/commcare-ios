package org.javarosa.core.model.utils

import org.gavaghan.geodesy.GlobalCoordinates
import org.javarosa.core.model.data.GeoPointData
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Static utility methods for GeoPointData.
 *
 * Distance calculation based off Android library:
 * https://github.com/googlemaps/android-maps-utils/blob/master/library/src/com/google/maps/android/SphericalUtil.java
 *
 * @author ftong
 */
object GeoPointUtils {

    @JvmField
    val EARTH_RADIUS: Double = 6371009.0  // Earth's radius, in meters

    /**
     * Returns the distance between two GeoPointData locations, in meters.
     * Ignores altitude and accuracy.
     */
    @JvmStatic
    fun computeDistanceBetween(from: GeoPointData, to: GeoPointData): Double {
        return EARTH_RADIUS * distanceRadians(
            from.getLatitude() * (PI / 180.0),
            from.getLongitude() * (PI / 180.0),
            to.getLatitude() * (PI / 180.0),
            to.getLongitude() * (PI / 180.0)
        )
    }

    /**
     * Returns distance on the unit sphere; the arguments are in radians.
     */
    private fun distanceRadians(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        return arcHav(havDistance(lat1, lat2, lng1 - lng2))
    }

    /**
     * Returns haversine(angle-in-radians).
     * hav(x) == (1 - cos(x)) / 2 == sin(x / 2)^2.
     */
    private fun hav(x: Double): Double {
        val sinHalf = sin(x * 0.5)
        return sinHalf * sinHalf
    }

    /**
     * Computes inverse haversine. Has good numerical stability around 0.
     * arcHav(x) == acos(1 - 2 * x) == 2 * asin(sqrt(x)).
     * The argument must be in [0, 1], and the result is positive.
     */
    private fun arcHav(x: Double): Double {
        return 2 * asin(sqrt(x))
    }

    /**
     * Returns hav() of distance from (lat1, lng1) to (lat2, lng2) on the unit sphere.
     */
    private fun havDistance(lat1: Double, lat2: Double, dLng: Double): Double {
        return hav(lat1 - lat2) + hav(dLng) * cos(lat1) * cos(lat2)
    }

    /**
     * Checks if coordinates are within valid bounds for latitude and longitude.
     *
     * @param latitude  Latitude in degrees
     * @param longitude Longitude in degrees
     * @throws IllegalArgumentException if values are outside geographic bounds
     */
    @JvmStatic
    fun validateCoordinates(latitude: Double, longitude: Double) {
        if ((latitude < -90.0 || latitude > 90.0) || (longitude < -180.0 || longitude > 180.0)) {
            throw IllegalArgumentException("Invalid coordinates")
        }
    }

    /**
     * Creates a point list from a flat list of lat/lon strings.
     *
     * @param latLongList Flat list of lat/lon values (e.g., [lat1, lon1, lat2, lon2, ...])
     * @return List of GlobalCoordinates representing the list of points
     * @throws IllegalArgumentException if input is invalid (odd number of elements)
     */
    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun createPointList(latLongList: List<String>?): MutableList<GlobalCoordinates> {
        if (latLongList == null || latLongList.size % 2 != 0) {
            throw IllegalArgumentException(
                "Input must contain a list of lat/lng pairs, and must be even-sized."
            )
        }

        val numPoints = latLongList.size / 2
        val pointList = mutableListOf<GlobalCoordinates>()

        for (i in 0 until numPoints) {
            val latitude = latLongList[i * 2].toDouble()
            val longitude = latLongList[i * 2 + 1].toDouble()
            validateCoordinates(latitude, longitude)
            pointList.add(GlobalCoordinates(latitude, longitude))
        }

        return pointList
    }
}
