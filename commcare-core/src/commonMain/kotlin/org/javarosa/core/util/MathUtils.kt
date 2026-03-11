package org.javarosa.core.util

import kotlin.jvm.JvmStatic
import kotlin.random.Random

/**
 * Static utility functions for mathematical operations
 *
 * @author ctsims
 */
object MathUtils {
    private var r: Random? = null

    // a - b * floor(a / b)
    @JvmStatic
    fun modLongNotSuck(a: Long, b: Long): Long {
        return ((a % b) + b) % b
    }

    @JvmStatic
    fun divLongNotSuck(a: Long, b: Long): Long {
        return (a - modLongNotSuck(a, b)) / b
    }

    @JvmStatic
    fun getRand(): Random {
        if (r == null) {
            r = Random.Default
        }
        return r!!
    }
}
