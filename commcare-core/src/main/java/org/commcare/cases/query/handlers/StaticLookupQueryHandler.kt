package org.commcare.cases.query.handlers

import org.commcare.cases.query.IndexedValueLookup
import org.commcare.cases.query.PredicateProfile
import org.commcare.cases.query.QueryContext
import org.commcare.cases.query.QueryHandler
import org.commcare.cases.util.QueryUtils
import org.javarosa.xpath.expr.FunctionUtils

/**
 * A static lookup handler is useful when a storage root keeps track of some data internally,
 * and is able to process requests for that data without needing additional work
 *
 * Created by ctsims on 1/25/2017.
 */
abstract class StaticLookupQueryHandler : QueryHandler<IndexedValueLookup> {

    protected abstract fun canHandle(key: String): Boolean
    protected abstract fun getMatches(key: String, valueToMatch: String): ArrayList<Int>

    override fun getExpectedRuntime(): Int = 1

    override fun profileHandledQuerySet(profiles: ArrayList<PredicateProfile>): IndexedValueLookup? {
        val ret = QueryUtils.getFirstKeyIndexedValue(profiles)
        if (ret != null) {
            if (canHandle(ret.key)) {
                return ret
            }
        }
        return null
    }

    override fun loadProfileMatches(querySet: IndexedValueLookup, queryContext: QueryContext): List<Int>? {
        return getMatches(querySet.key, FunctionUtils.toString(querySet.value))
    }

    override fun updateProfiles(querySet: IndexedValueLookup, profiles: ArrayList<PredicateProfile>) {
        profiles.remove(querySet)
    }
}
