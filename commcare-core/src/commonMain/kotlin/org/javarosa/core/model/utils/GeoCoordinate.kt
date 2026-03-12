package org.javarosa.core.model.utils

/**
 * A geographic coordinate represented by latitude and longitude in degrees.
 * Replacement for org.gavaghan.geodesy.GlobalCoordinates to enable cross-platform usage.
 */
data class GeoCoordinate(val latitude: Double, val longitude: Double)
