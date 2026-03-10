package org.commcare.cases.query.queryset

import org.commcare.cases.query.QueryCache

/**
 * Created by ctsims on 2/6/2017.
 */
class QuerySetCache : QueryCache {

    private val querySetMap: MutableMap<String, ModelQuerySet> = HashMap()

    fun getModelQuerySet(querySetId: String): ModelQuerySet? {
        return querySetMap[querySetId]
    }

    fun addModelQuerySet(querySetId: String, set: ModelQuerySet) {
        querySetMap[querySetId] = set
    }
}
