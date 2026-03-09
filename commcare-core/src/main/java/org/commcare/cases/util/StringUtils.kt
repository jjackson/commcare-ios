package org.commcare.cases.util

import org.commcare.modern.util.Pair
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Created by willpride on 10/27/16.
 */
object StringUtils {

    private var diacritics: Pattern? = null

    // TODO: Really not sure about this size. Also, the LRU probably isn't really the best model here
    // since we'd _like_ for these caches to get cleaned up at _some_ point.
    private const val cacheSize = 100 * 1024

    // TODO: Bro you can't just cache every fucking string ever.
    private var normalizationCache: LruCache<String, String>? = null

    /**
     * @param input A non-null string
     * @return a canonical version of the passed in string that is lower cased and has removed diacritical marks
     * like accents.
     */
    @JvmStatic
    @Synchronized
    fun normalize(input: String): String {
        if (normalizationCache == null) {
            normalizationCache = LruCache(cacheSize)
            diacritics = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        }
        val cachedString = normalizationCache!!.get(input)
        if (cachedString != null) {
            return cachedString
        }

        // Initialized the normalized string (If we can, we'll use the Normalizer API on it)
        // TODO: commented out this version check. What's up with that
        // If we're above gingerbread we'll normalize this in NFD form
        // which helps a lot. Otherwise we won't be able to clear up some of those
        // issues, but we can at least still eliminate diacritics.
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)

        val output = diacritics!!.matcher(normalized).replaceAll("").lowercase()

        normalizationCache!!.put(input, output)

        return output
    }

    /**
     * Identifies whether two strings are close enough that they are likely to be
     * intended to be the same string. Fuzzy matching is only performed on strings that are
     * longer than a certain size.
     *
     * @return A pair with two values. First value represents a match: true if the two strings
     * meet CommCare's fuzzy match definition, false otherwise. Second value is the actual string
     * distance that was matched, in order to be able to rank or otherwise interpret results.
     */
    @JvmStatic
    fun fuzzyMatch(source: String, target: String): Pair<Boolean, Int> {
        return fuzzyMatch(source, target, 2)
    }

    @JvmStatic
    fun fuzzyMatch(source: String, target: String, distanceThreshold: Int): Pair<Boolean, Int> {
        var adjustedTarget = target
        // tweakable parameter: Minimum length before edit distance
        // starts being used (this is probably not necessary, and
        // basically only makes sure that "at" doesn't match "or" or similar
        if (source.length > 3) {
            // Makes sure that we're only matching strings with distanceThreshold as
            // the maximum difference in length, otherwise LevenshteinDistance may return a
            // distance more than distanceThreshold even if the prefix matches perfectly.
            if (adjustedTarget.length > source.length + distanceThreshold) {
                adjustedTarget = adjustedTarget.substring(0, source.length + distanceThreshold)
            }
            val distance = levenshteinDistance(source, adjustedTarget)
            // tweakable parameter: edit distance past string length disparity
            if (distance <= distanceThreshold) {
                return Pair.create(true, distance)
            }
        }
        return Pair.create(false, -1)
    }

    /**
     * Computes the Levenshtein Distance between two strings.
     *
     * This code is sourced and unmodified from wikibooks under
     * the Creative Commons attribution share-alike 3.0 license and
     * by be re-used under the terms of that license.
     *
     * http://creativecommons.org/licenses/by-sa/3.0/
     *
     * TODO: re-implement for efficiency/licensing possibly.
     */
    private fun levenshteinDistance(s0: String, s1: String): Int {
        val len0 = s0.length + 1
        val len1 = s1.length + 1

        // the array of distances
        var cost = IntArray(len0)
        var newcost = IntArray(len0)

        // initial cost of skipping prefix in String s0
        for (i in 0 until len0) cost[i] = i

        // dynamically computing the array of distances

        // transformation cost for each letter in s1
        for (j in 1 until len1) {

            // initial cost of skipping prefix in String s1
            newcost[0] = j

            // transformation cost for each letter in s0
            for (i in 1 until len0) {

                // matching current letters in both strings
                val match = if (s0[i - 1] == s1[j - 1]) 0 else 1

                // computing cost for each transformation
                val costReplace = cost[i - 1] + match
                val costInsert = cost[i] + 1
                val costDelete = newcost[i - 1] + 1

                // keep minimum cost
                newcost[i] = minOf(costInsert, costDelete, costReplace)
            }

            // swap cost/newcost arrays
            val swap = cost
            cost = newcost
            newcost = swap
        }

        // the distance is the cost for transforming all letters in both strings
        return cost[len0 - 1]
    }

    /**
     * Converts a string to a list of Characters.
     */
    @JvmStatic
    fun toList(str: String): ArrayList<Char> {
        val myArrayList = ArrayList<Char>(str.length)
        for (i in str.indices) {
            myArrayList.add(i, str[i])
        }
        return myArrayList
    }

    @JvmStatic
    fun isEmpty(text: String?): Boolean {
        return text.isNullOrEmpty()
    }
}
