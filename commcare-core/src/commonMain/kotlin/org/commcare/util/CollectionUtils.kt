package org.commcare.util

import kotlin.jvm.JvmStatic

/**
 * Common operations on Collections
 */
object CollectionUtils {

    /**
     * @param first  First Integer ArrayList to be merged
     * @param second Second Integer ArrayList to be merged
     * @return single merged int array of first and second
     */
    @JvmStatic
    fun mergeIntegerVectorsInArray(first: ArrayList<Int>, second: ArrayList<Int>): IntArray {
        val resultLength = first.size + second.size
        val result = IntArray(resultLength)
        var i = 0
        while (i < first.size) {
            result[i] = first[i]
            ++i
        }
        while (i < resultLength) {
            result[i] = second[i - first.size]
            ++i
        }
        return result
    }

    /**
     * Checks if any element in [subList] is contained in [superList]
     * @return true if [superList] contains any element of [subList], false otherwise
     */
    @JvmStatic
    fun containsAny(superList: ArrayList<String>, subList: ArrayList<String>): Boolean {
        for (item in subList) {
            if (superList.contains(item)) {
                return true
            }
        }
        return false
    }
}
