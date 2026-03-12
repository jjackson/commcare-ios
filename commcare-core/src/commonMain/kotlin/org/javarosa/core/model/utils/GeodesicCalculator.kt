package org.javarosa.core.model.utils

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Geodesic calculations on the WGS84 ellipsoid using Vincenty's formulae.
 * Replaces org.gavaghan.geodesy.GeodeticCalculator for cross-platform usage.
 *
 * References:
 * - Vincenty, T. (1975). "Direct and Inverse Solutions of Geodesics on the Ellipsoid
 *   with application of nested equations". Survey Review. 23 (176): 88–93.
 */
object GeodesicCalculator {

    // WGS84 ellipsoid parameters
    private const val A = 6378137.0                // semi-major axis (meters)
    private const val F = 1.0 / 298.257223563      // flattening
    private const val B = A * (1.0 - F)            // semi-minor axis

    private const val MAX_ITERATIONS = 200
    private const val CONVERGENCE_THRESHOLD = 1e-12

    /**
     * Result of the Vincenty inverse calculation.
     */
    data class GeodeticCurve(
        val ellipsoidalDistance: Double,
        val azimuth: Double    // forward azimuth in degrees from start to end
    )

    /**
     * Vincenty inverse formula: given two points, compute distance and azimuth.
     */
    fun calculateGeodeticCurve(
        start: GeoCoordinate,
        end: GeoCoordinate
    ): GeodeticCurve {
        val phi1 = start.latitude * PI / 180.0
        val phi2 = end.latitude * PI / 180.0
        val lambda1 = start.longitude * PI / 180.0
        val lambda2 = end.longitude * PI / 180.0

        val U1 = atan((1 - F) * tan(phi1))
        val U2 = atan((1 - F) * tan(phi2))
        val sinU1 = sin(U1)
        val cosU1 = cos(U1)
        val sinU2 = sin(U2)
        val cosU2 = cos(U2)

        val L = lambda2 - lambda1
        var lambda = L
        var prevLambda: Double

        var sinSigma = 0.0
        var cosSigma = 0.0
        var sigma = 0.0
        var sinAlpha: Double
        var cos2Alpha = 0.0
        var cos2SigmaM = 0.0

        for (i in 0 until MAX_ITERATIONS) {
            val sinLambda = sin(lambda)
            val cosLambda = cos(lambda)

            sinSigma = sqrt(
                (cosU2 * sinLambda) * (cosU2 * sinLambda) +
                        (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) *
                        (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda)
            )

            if (sinSigma == 0.0) {
                // Co-incident points
                return GeodeticCurve(0.0, 0.0)
            }

            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cos2Alpha = 1.0 - sinAlpha * sinAlpha
            cos2SigmaM = if (cos2Alpha != 0.0) {
                cosSigma - 2.0 * sinU1 * sinU2 / cos2Alpha
            } else {
                0.0 // Equatorial line
            }

            val C = F / 16.0 * cos2Alpha * (4.0 + F * (4.0 - 3.0 * cos2Alpha))
            prevLambda = lambda
            lambda = L + (1.0 - C) * F * sinAlpha *
                    (sigma + C * sinSigma *
                            (cos2SigmaM + C * cosSigma *
                                    (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)))

            if (abs(lambda - prevLambda) < CONVERGENCE_THRESHOLD) {
                break
            }
        }

        val uSq = cos2Alpha * (A * A - B * B) / (B * B)
        val bigA = 1.0 + uSq / 16384.0 *
                (4096.0 + uSq * (-768.0 + uSq * (320.0 - 175.0 * uSq)))
        val bigB = uSq / 1024.0 *
                (256.0 + uSq * (-128.0 + uSq * (74.0 - 47.0 * uSq)))

        val deltaSigma = bigB * sinSigma *
                (cos2SigmaM + bigB / 4.0 *
                        (cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM) -
                                bigB / 6.0 * cos2SigmaM *
                                (-3.0 + 4.0 * sinSigma * sinSigma) *
                                (-3.0 + 4.0 * cos2SigmaM * cos2SigmaM)))

        val distance = B * bigA * (sigma - deltaSigma)

        // Forward azimuth
        val azimuthRad = atan2(
            cosU2 * sin(lambda),
            cosU1 * sinU2 - sinU1 * cosU2 * cos(lambda)
        )
        var azimuthDeg = azimuthRad * 180.0 / PI
        if (azimuthDeg < 0) azimuthDeg += 360.0

        return GeodeticCurve(distance, azimuthDeg)
    }

    /**
     * Vincenty direct formula: given start point, azimuth, and distance, compute destination.
     */
    fun calculateEndingGlobalCoordinates(
        start: GeoCoordinate,
        azimuthDeg: Double,
        distance: Double
    ): GeoCoordinate {
        val phi1 = start.latitude * PI / 180.0
        val alpha1 = azimuthDeg * PI / 180.0

        val sinAlpha1 = sin(alpha1)
        val cosAlpha1 = cos(alpha1)

        val tanU1 = (1.0 - F) * tan(phi1)
        val cosU1 = 1.0 / sqrt(1.0 + tanU1 * tanU1)
        val sinU1 = tanU1 * cosU1

        val sigma1 = atan2(tanU1, cosAlpha1)
        val sinAlpha = cosU1 * sinAlpha1
        val cos2Alpha = 1.0 - sinAlpha * sinAlpha

        val uSq = cos2Alpha * (A * A - B * B) / (B * B)
        val bigA = 1.0 + uSq / 16384.0 *
                (4096.0 + uSq * (-768.0 + uSq * (320.0 - 175.0 * uSq)))
        val bigB = uSq / 1024.0 *
                (256.0 + uSq * (-128.0 + uSq * (74.0 - 47.0 * uSq)))

        var sigma = distance / (B * bigA)
        var prevSigma: Double

        var sinSigma: Double
        var cosSigma: Double
        var cos2SigmaM: Double

        for (i in 0 until MAX_ITERATIONS) {
            cos2SigmaM = cos(2.0 * sigma1 + sigma)
            sinSigma = sin(sigma)
            cosSigma = cos(sigma)

            val deltaSigma = bigB * sinSigma *
                    (cos2SigmaM + bigB / 4.0 *
                            (cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM) -
                                    bigB / 6.0 * cos2SigmaM *
                                    (-3.0 + 4.0 * sinSigma * sinSigma) *
                                    (-3.0 + 4.0 * cos2SigmaM * cos2SigmaM)))

            prevSigma = sigma
            sigma = distance / (B * bigA) + deltaSigma

            if (abs(sigma - prevSigma) < CONVERGENCE_THRESHOLD) {
                break
            }
        }

        sinSigma = sin(sigma)
        cosSigma = cos(sigma)
        cos2SigmaM = cos(2.0 * sigma1 + sigma)

        val tmp = sinU1 * sinSigma - cosU1 * cosSigma * cosAlpha1
        val phi2 = atan2(
            sinU1 * cosSigma + cosU1 * sinSigma * cosAlpha1,
            (1.0 - F) * sqrt(sinAlpha * sinAlpha + tmp * tmp)
        )

        val lambdaVal = atan2(
            sinSigma * sinAlpha1,
            cosU1 * cosSigma - sinU1 * sinSigma * cosAlpha1
        )

        val C = F / 16.0 * cos2Alpha * (4.0 + F * (4.0 - 3.0 * cos2Alpha))
        val L = lambdaVal - (1.0 - C) * F * sinAlpha *
                (sigma + C * sinSigma *
                        (cos2SigmaM + C * cosSigma *
                                (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)))

        val lambda1 = start.longitude * PI / 180.0
        val lambda2 = lambda1 + L

        return GeoCoordinate(
            phi2 * 180.0 / PI,
            lambda2 * 180.0 / PI
        )
    }
}
