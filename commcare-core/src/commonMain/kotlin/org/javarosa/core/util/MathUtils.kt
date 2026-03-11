package org.javarosa.core.util

import kotlin.random.Random

/**
 * Static utility functions for mathematical operations
 *
 * @author ctsims
 */
object MathUtils {
    private var r: Random? = null

    // a - b * floor(a / b)
    fun modLongNotSuck(a: Long, b: Long): Long {
        return ((a % b) + b) % b
    }

    fun divLongNotSuck(a: Long, b: Long): Long {
        return (a - modLongNotSuck(a, b)) / b
    }

    fun getRand(): Random {
        if (r == null) {
            r = Random.Default
        }
        return r!!
    }
}
