package org.javarosa.core.util

import kotlin.jvm.JvmStatic

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
    fun <E> intersectSingle(a: ArrayList<E>, b: ArrayList<E>): E? {
        for (e in a) {
            if (b.indexOf(e) != -1) {
                return e
            }
        }
        return null
    }

    @JvmStatic
    fun <E> vectorCopy(a: ArrayList<E>?): ArrayList<E>? {
        if (a == null) {
            return null
        }
        val b = ArrayList<E>()
        for (e in a) {
            b.add(e)
        }
        return b
    }

    @JvmStatic
    fun <E> copyIntoArray(v: ArrayList<E>, a: Array<E>): Array<E> {
        var i = 0
        for (e in v) {
            a[i++] = e
        }
        return a
    }

    @JvmStatic
    fun <E> toVector(a: Array<E>): ArrayList<E> {
        val v = ArrayList<E>()
        for (e in a) {
            v.add(e)
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
