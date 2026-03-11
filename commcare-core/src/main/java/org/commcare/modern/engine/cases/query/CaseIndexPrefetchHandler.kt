package org.commcare.modern.engine.cases.query

import org.commcare.cases.model.Case
import org.commcare.cases.query.IndexedValueLookup
import org.commcare.cases.query.PredicateProfile
import org.commcare.cases.query.QueryCache
import org.commcare.cases.query.QueryContext
import org.commcare.cases.query.QueryHandler
import org.commcare.cases.util.QueryUtils
import org.commcare.modern.engine.cases.CaseIndexTable
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.trace.EvaluationTrace
import org.javarosa.xpath.expr.XPathExpression


/**
 * This handler detects contexts in which the query is likely to trip many index lookups and will
 * strategically perform a cache of relevant indexes
 *
 * Created by ctsims on 1/25/2017.
 */
class CaseIndexPrefetchHandler(private val mCaseIndexTable: CaseIndexTable?) : QueryHandler<IndexedValueLookup> {

    /**
     * This should be roughly the point at which 1 query of N items in the db will be faster
     * than N queries of 1 item, even if only one item ends up being used.
     */
    companion object {
        private const val BULK_LOAD_THRESHOLD = 500
    }

    class Cache : QueryCache {
        @JvmField
        var currentlyFetchedIndexKeys: ArrayList<String> = ArrayList()
        @JvmField
        var indexCache: HashMap<String, ArrayList<Int>> = HashMap()
    }

    //TODO: Profile table by each type of index for size to identify threshold changes.

    override fun getExpectedRuntime(): Int {
        return 1
    }

    override fun profileHandledQuerySet(profiles: ArrayList<PredicateProfile>): IndexedValueLookup? {
        val ret = QueryUtils.getFirstKeyIndexedValue(profiles)
        if (ret != null) {
            if (ret.key.startsWith(Case.INDEX_CASE_INDEX_PRE)) {
                return ret
            }
        }
        return null
    }

    override fun loadProfileMatches(querySet: IndexedValueLookup, context: QueryContext): List<Int>? {
        val indexName = querySet.key.substring(Case.INDEX_CASE_INDEX_PRE.length)
        val value = querySet.value as String

        val cache = context.getQueryCache(Cache::class.java)
        if (!cache.currentlyFetchedIndexKeys.contains(indexName)) {
            if (context.getScope() < BULK_LOAD_THRESHOLD) {
                return null
            }

            val trace = EvaluationTrace("Index Bulk Prefetch [$indexName]")
            val indexFetchSize = mCaseIndexTable?.loadIntoIndexTable(cache.indexCache, indexName) ?: 0
            trace.setOutcome("Loaded: $indexFetchSize")
            context.reportTrace(trace)
            cache.currentlyFetchedIndexKeys.add(indexName)
        }
        val cacheKey = "$indexName|$value"
        return cache.indexCache[cacheKey]
    }

    override fun updateProfiles(querySet: IndexedValueLookup, profiles: ArrayList<PredicateProfile>) {
        profiles.remove(querySet)
    }

    override fun collectPredicateProfiles(predicates: ArrayList<XPathExpression>, context: QueryContext, evaluationContext: EvaluationContext): Collection<PredicateProfile>? {
        return null
    }
}
