package org.commcare.cases.query.queryset

import org.commcare.cases.query.QueryContext
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference

/**
 * A derived query lookup is the result of an operation which translates one set of cases to another.
 *
 * As an example, when running the query in a vacuum
 *
 * instance('casedb')/casedb/case
 *                   [@case_type='FOO'][@case_id = current()/index/parent][somevalue = 'blank']
 *
 * After the first predicate executes a model query set may be generated containing all matches,
 * that would be the "current()" model query set
 *
 * A QuerySet Transform could then produce a DerivedCaseQueryLookup based on the original query
 * set that instead of returning the current case's model id would instead produce the current
 * case's parent ID.
 *
 * To do this the implementing class will need to provide an implementation of the
 *
 * loadModelQuerySet()
 *
 * method. This method should take the provided root model set and generate its own model set based
 * on it. This class will handle the relevant caching and lookup of values into that model set
 *
 * Created by ctsims on 2/6/2017.
 */
abstract class DerivedCaseQueryLookup(
    protected val rootLookup: QuerySetLookup
) : QuerySetLookup {

    override fun isValid(lookupIdKey: TreeReference?, context: QueryContext): Boolean {
        return lookupIdKey != null && rootLookup.isValid(lookupIdKey, context)
    }

    override fun getLookupIdKey(evaluationContext: EvaluationContext): TreeReference? {
        return rootLookup.getLookupIdKey(evaluationContext)
    }

    override fun performSetLookup(lookupIdKey: TreeReference, queryContext: QueryContext): List<Int>? {
        val rootResult = rootLookup.performSetLookup(lookupIdKey, queryContext) ?: return null
        val set = getOrLoadCachedQuerySet(queryContext) ?: return null

        val returnSet: MutableList<Int> = ArrayList()
        for (i in rootResult) {
            val matching = set.getMatchingValues(i) ?: return null
            returnSet.addAll(matching)
        }
        return returnSet
    }

    override fun getLookupSetBody(queryContext: QueryContext): Set<Int> {
        val set = getOrLoadCachedQuerySet(queryContext)
        return set!!.getSetBody()!!
    }

    private fun getOrLoadCachedQuerySet(queryContext: QueryContext): ModelQuerySet? {
        val cache = queryContext.getQueryCache(QuerySetCache::class) { QuerySetCache() }

        var set = cache.getModelQuerySet(this.currentQuerySetId)
        if (set == null) {
            set = loadModelQuerySet(queryContext)
            cache.addModelQuerySet(this.currentQuerySetId, set)
        }
        return set
    }

    protected abstract fun loadModelQuerySet(queryContext: QueryContext): ModelQuerySet
}
