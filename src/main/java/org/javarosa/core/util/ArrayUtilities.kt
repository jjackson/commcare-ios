package org.javarosa.core.util

import java.util.Vector

/**
 * @author Clayton Sims
 */
object ArrayUtilities {
    @JvmStatic
    fun arraysEqual(array1: Array<Any>, array2: Array<Any>): Boolean {
        if (array1.size != array2.size) {
            return false
        }
        for (i in array1.indices) {
            if (array1[i] != array2[i]) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun arraysEqual(array1: ByteArray, array2: ByteArray): Boolean {
        if (array1.size != array2.size) {
            return false
        }
        for (i in array1.indices) {
            if (array1[i] != array2[i]) {
                return false
            }
        }
        return true
    }

    /**
     * Find a single intersecting element common to two lists, or null if none
     * exists. Note that no unique condition will be reported if there are multiple
     * elements which intersect, so this should likely only be used if the possible
     * size of intersection is 0 or 1
     */
    @JvmStatic
    fun <E> intersectSingle(a: Vector<E>, b: Vector<E>): E? {
        for (e in a) {
            if (b.indexOf(e) != -1) {
                return e
            }
        }
        return null
    }

    @JvmStatic
    fun <E> vectorCopy(a: Vector<E>?): Vector<E>? {
        if (a == null) {
            return null
        }
        val b = Vector<E>()
        for (e in a) {
            b.addElement(e)
        }
        return b
    }

    @JvmStatic
    fun <E> copyIntoArray(v: Vector<E>, a: Array<E>): Array<E> {
        var i = 0
        for (e in v) {
            a[i++] = e
        }
        return a
    }

    @JvmStatic
    fun <E> toVector(a: Array<E>): Vector<E> {
        val v = Vector<E>()
        for (e in a) {
            v.addElement(e)
        }
        return v
    }

    /**
     * Get the last element of a String array.
     */
    @JvmStatic
    fun last(strings: Array<String>): String {
        return strings[strings.size - 1]
    }
}
