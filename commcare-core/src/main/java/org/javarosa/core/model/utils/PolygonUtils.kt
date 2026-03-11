package org.javarosa.core.model.utils

import org.gavaghan.geodesy.Ellipsoid
import org.gavaghan.geodesy.GeodeticCalculator
import org.gavaghan.geodesy.GlobalCoordinates
import kotlin.jvm.JvmStatic

/**
 * Utility class for creating, validating, and interacting with geographic polygons
 * using geodesic (ellipsoid-aware) calculations.
 */
object PolygonUtils {

    /**
     * Creates a polygon from a flat list of lat/lon strings.
     *
     * @param latLongList Flat list of lat/lon values (e.g., [lat1, lon1, lat2, lon2, ...])
     * @return List of GlobalCoordinates representing the polygon (closed)
     * @throws IllegalArgumentException if input is invalid or polygon is malformed
     */
    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun createPolygon(latLongList: List<String>): MutableList<GlobalCoordinates> {
        val polygon = GeoPointUtils.createPointList(latLongList)

        // Close polygon if not already closed
        if (polygon.size > 2 && polygon[0] != polygon[polygon.size - 1]) {
            polygon.add(
                GlobalCoordinates(
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
    fun findClosestPoint(point: GlobalCoordinates, polygon: List<GlobalCoordinates>): String {
        val calc = GeodeticCalculator()
        val ellipsoid = Ellipsoid.WGS84

        var minDist = Double.MAX_VALUE
        var closest: GlobalCoordinates? = null

        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]

            val proj = projectOntoSegment(point, a, b, calc, ellipsoid) ?: continue

            val curve = calc.calculateGeodeticCurve(ellipsoid, point, proj)
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
     * @param calc      Geodetic calculator
     * @param ellipsoid The ellipsoid reference (WGS84)
     * @return Projected closest point on the segment
     */
    private fun projectOntoSegment(
        point: GlobalCoordinates,
        a: GlobalCoordinates,
        b: GlobalCoordinates,
        calc: GeodeticCalculator,
        ellipsoid: Ellipsoid
    ): GlobalCoordinates? {
        if (a.latitude == b.latitude && a.longitude == b.longitude) {
            return a
        }

        val ab = calc.calculateGeodeticCurve(ellipsoid, a, b)
        val azimuthAB = ab.azimuth
        val totalLength = ab.ellipsoidalDistance

        val ap = calc.calculateGeodeticCurve(ellipsoid, a, point)
        val azimuthAP = ap.azimuth
        val distanceAP = ap.ellipsoidalDistance

        val angleDiff = Math.toRadians(azimuthAP - azimuthAB)
        val projection = distanceAP * Math.cos(angleDiff)

        if (projection <= 0) return a
        if (projection >= totalLength) return b

        return calc.calculateEndingGlobalCoordinates(ellipsoid, a, azimuthAB, projection)
    }

    /**
     * Determines if a point lies inside or on the border of a polygon using the ray casting algorithm.
     *
     * @param point   The point to test
     * @param polygon The polygon (list of GlobalCoordinates)
     * @return true if inside or on the edge; false otherwise
     */
    @JvmStatic
    fun isPointInsideOrOnPolygon(point: GlobalCoordinates, polygon: List<GlobalCoordinates>): Boolean {
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
