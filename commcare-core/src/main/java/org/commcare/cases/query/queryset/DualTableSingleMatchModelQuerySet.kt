package org.commcare.cases.query.queryset

import java.util.LinkedHashSet

/**
 * A model query set implementation for queries which are one-to-one results.
 *
 * Maintains the original order of arguments received to ensure that lookups happen
 * in a predictable manner (and short-term caches will maximize hits)
 *
 * Created by ctsims on 2/6/2017.
 */
class DualTableSingleMatchModelQuerySet : ModelQuerySet {
    private val map: MutableMap<Int, Int> = HashMap()
    private val body: LinkedHashSet<Int> = LinkedHashSet()

    fun loadResult(key: Int, value: Int) {
        map[key] = value
        body.add(value)
    }

    override fun getMatchingValues(i: Int): Collection<Int>? {
        val result = map[i] ?: return null
        val ret = ArrayList<Int>()
        ret.add(result)
        return ret
    }

    override fun getSetBody(): LinkedHashSet<Int> {
        return body
    }
}
