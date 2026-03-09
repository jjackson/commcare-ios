package org.commcare.cases.query.handlers

import org.commcare.cases.query.IndexedValueLookup
import org.commcare.cases.query.PredicateProfile
import org.commcare.cases.query.QueryContext
import org.commcare.cases.query.QueryHandler
import org.commcare.cases.util.LruCache
import org.commcare.cases.util.QueryUtils
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.xpath.expr.XPathExpression
import java.util.Vector

/**
 * For generic StorageBacked tree root models, each time one is looked up, provide a small cache
 * that can be used to keep the lookup result in memory.
 *
 * This object manages its own data lifecycle (by keeping track of very little data) and is only
 * expected to provide results for rapidly-repeated queries for the same object, rather than long
 * term query planning.
 *
 * Created by ctsims on 1/25/2017.
 */
class BasicStorageBackedCachingQueryHandler : QueryHandler<IndexedValueLookup> {
    private val caches = HashMap<String, LruCache<Any, List<Int>>>()

    override fun getExpectedRuntime(): Int = 10

    override fun profileHandledQuerySet(profiles: Vector<PredicateProfile>): IndexedValueLookup? {
        val ret = QueryUtils.getFirstKeyIndexedValue(profiles)
        if (ret != null) {
            if (caches.containsKey(ret.key)) {
                return ret
            }
        }
        return null
    }

    override fun loadProfileMatches(querySet: IndexedValueLookup, queryContext: QueryContext): List<Int>? {
        val cache = caches[querySet.key] ?: return null
        return cache.get(querySet.value)
    }

    override fun updateProfiles(querySet: IndexedValueLookup, profiles: Vector<PredicateProfile>) {
        profiles.remove(querySet)
    }

    override fun collectPredicateProfiles(
        predicates: Vector<XPathExpression>,
        context: QueryContext,
        evaluationContext: EvaluationContext
    ): Collection<PredicateProfile>? = null

    fun cacheResult(key: String, value: Any, results: List<Int>) {
        // TODO: It's great that we're feeding these back, but it's really dangerous that this
        // handler prevents the creation of RecordSets. Maybe minimize that by limiting the number
        // of elements in the results cache?

        val cache = if (!caches.containsKey(key)) {
            LruCache<Any, List<Int>>(10).also { caches[key] = it }
        } else {
            caches[key]!!
        }
        cache.put(value, results)
    }
}
