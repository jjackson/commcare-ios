package org.javarosa.core.model.utils

import kotlin.jvm.JvmStatic
import kotlin.math.PI
import kotlin.math.cos

/**
 * Utility class for creating, validating, and interacting with geographic polygons
 * using geodesic (ellipsoid-aware) calculations.
 */
object PolygonUtils {

    /**
     * Creates a polygon from a flat list of lat/lon strings.
     *
     * @param latLongList Flat list of lat/lon values (e.g., [lat1, lon1, lat2, lon2, ...])
     * @return List of GeoCoordinate representing the polygon (closed)
     * @throws IllegalArgumentException if input is invalid or polygon is malformed
     */
    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun createPolygon(latLongList: List<String>): MutableList<GeoCoordinate> {
        val polygon = GeoPointUtils.createPointList(latLongList)

        // Close polygon if not already closed
        if (polygon.size > 2 && polygon[0] != polygon[polygon.size - 1]) {
            polygon.add(
                GeoCoordinate(
                    polygon[0].latitude,
                    polygon[0].longitude
                )
            )
        }

        if (polygon.size < 4) {
            throw IllegalArgumentException("Polygon must have at least three distinct vertices.")
        }

        return polygon
    }

    /**
     * Computes the closest point on the polygon border to a given test point using geodesic projection.
     *
     * @param point   The input location
     * @param polygon A closed list of polygon vertices
     * @return String representation of the closest lat/lon pair
     */
    @JvmStatic
    fun findClosestPoint(point: GeoCoordinate, polygon: List<GeoCoordinate>): String {
        var minDist = Double.MAX_VALUE
        var closest: GeoCoordinate? = null

        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]

            val proj = projectOntoSegment(point, a, b) ?: continue

            val curve = GeodesicCalculator.calculateGeodeticCurve(point, proj)
            val dist = curve.ellipsoidalDistance

            if (dist < minDist) {
                minDist = dist
                closest = proj
            }
        }

        return "${closest!!.latitude} ${closest.longitude}"
    }

    /**
     * Projects a test point onto a geodesic segment between two polygon points.
     *
     * @param point     Test point
     * @param a         Segment start
     * @param b         Segment end
     * @return Projected closest point on the segment
     */
    private fun projectOntoSegment(
        point: GeoCoordinate,
        a: GeoCoordinate,
        b: GeoCoordinate
    ): GeoCoordinate? {
        if (a.latitude == b.latitude && a.longitude == b.longitude) {
            return a
        }

        val ab = GeodesicCalculator.calculateGeodeticCurve(a, b)
        val azimuthAB = ab.azimuth
        val totalLength = ab.ellipsoidalDistance

        val ap = GeodesicCalculator.calculateGeodeticCurve(a, point)
        val azimuthAP = ap.azimuth
        val distanceAP = ap.ellipsoidalDistance

        val angleDiff = (azimuthAP - azimuthAB) * (PI / 180.0)
        val projection = distanceAP * cos(angleDiff)

        if (projection <= 0) return a
        if (projection >= totalLength) return b

        return GeodesicCalculator.calculateEndingGlobalCoordinates(a, azimuthAB, projection)
    }

    /**
     * Determines if a point lies inside or on the border of a polygon using the ray casting algorithm.
     *
     * @param point   The point to test
     * @param polygon The polygon (list of GeoCoordinate)
     * @return true if inside or on the edge; false otherwise
     */
    @JvmStatic
    fun isPointInsideOrOnPolygon(point: GeoCoordinate, polygon: List<GeoCoordinate>): Boolean {
        var intersectCount = 0
        val n = polygon.size

        val testLat = point.latitude
        val testLong = point.longitude

        for (i in 0 until n) {
            val a = polygon[i]
            val b = polygon[(i + 1) % n]

            val latA = a.latitude
            val longA = a.longitude
            val latB = b.latitude
            val longB = b.longitude

            // Vertex check
            if ((testLat == latA && testLong == longA) || (testLat == latB && testLong == longB)) {
                return true
            }

            // Ray casting
            if (((latA > testLat) != (latB > testLat)) &&
                (testLong < (longB - longA) * (testLat - latA) / (latB - latA + 1e-10) + longA)
            ) {
                intersectCount++
            }
        }

        return (intersectCount % 2 == 1)
    }
}
