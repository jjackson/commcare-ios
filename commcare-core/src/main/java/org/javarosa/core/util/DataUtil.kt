package org.javarosa.core.util

import java.util.HashSet

/**
 * @author ctsims
 */
object DataUtil {
    private const val offset = 10
    private const val low = -10
    private const val high = 400
    private var iarray: Array<Int>? = null

    /**
     * Get Integer object that corresponds to int argument from a
     * pre-computed table, or build a new instance.
     *
     * @return Cached or new Integer instance that corresponds to ivalue argument
     */
    @JvmStatic
    fun integer(ivalue: Int): Int {
        // lazily populate Integer cache
        if (iarray == null) {
            iarray = Array(high - low) { i -> i + low }
        }

        return if (ivalue < high && ivalue >= low) {
            iarray!![ivalue + offset]
        } else {
            ivalue
        }
    }

    @JvmStatic
    fun <T> intersection(a: Collection<T>, b: Collection<T>): List<T> {
        if (b.size < a.size) {
            return intersection(b, a)
        }

        val setA: HashSet<T> = if (a is HashSet<*>) {
            @Suppress("UNCHECKED_CAST")
            (a as HashSet<T>).clone() as HashSet<T>
        } else {
            HashSet(a)
        }
        val setB: HashSet<T> = if (b is HashSet<*>) {
            @Suppress("UNCHECKED_CAST")
            b as HashSet<T>
        } else {
            HashSet(b)
        }
        setA.retainAll(setB)
        return ArrayList(setA)
    }

    @JvmStatic
    fun listToString(list: List<String>): String {
        val sb = StringBuilder()
        for (s in list) {
            sb.append("$s ")
        }
        return sb.toString().substring(0, sb.length - 1)
    }

    @JvmStatic
    fun stringToList(s: String): List<String> {
        return listOf(*splitOnSpaces(s))
    }

    @JvmStatic
    fun splitOnSpaces(s: String): Array<String> {
        if (s == "") {
            return emptyArray()
        }
        return s.split("[ ]+".toRegex()).toTypedArray()
    }

    @JvmStatic
    fun intArrayContains(source: IntArray, target: Int): Boolean {
        for (current in source) {
            if (current == target) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun splitOnDash(s: String): Array<String> {
        return s.split("-").toTypedArray()
    }

    @JvmStatic
    fun splitOnColon(s: String): Array<String> {
        return s.split(":").toTypedArray()
    }

    @JvmStatic
    fun splitOnPlus(s: String): Array<String> {
        return s.split("[+]".toRegex()).toTypedArray()
    }
}
