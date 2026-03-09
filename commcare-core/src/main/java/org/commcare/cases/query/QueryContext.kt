package org.commcare.cases.query

import org.commcare.cases.query.queryset.CurrentModelQuerySet
import org.commcare.cases.query.queryset.QuerySetCache
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.trace.EvaluationTrace

/**
 * A Query Context object is responsible for keeping track of relevant metadata about where a
 * query is executing that may make it possible for the planner to better identify when to
 * trigger certain query handlers.
 *
 * For instance, if an individual query is only looking for one matching case, it may do a single
 * DB read. The context can provide a cue that the query is likely to be run over many other cases,
 * which can provide the planner with a hint to fetch those cases in bulk proactively
 *
 * The QueryContext Object's lifecycle is also used to limit the scope of any of that bulk caching.
 * Since the object lifecycle is paired with the EC of the query, large chunks of memory can be
 * allocated into this object and it will be removed when the context is no longer relevant.
 *
 * Created by ctsims on 1/26/2017.
 */
open class QueryContext {

    //TODO: This is a bad reason to keep the EC around here, and locks the lifecycle of this object
    //into the EC
    private var traceRoot: EvaluationContext? = null

    private var cache: QueryCacheHost

    private var potentialSpawnedContext: QueryContext? = null

    /**
     * Context scope roughly keeps track of "how many times is the current query possibly going to
     * run". For instance, when evaluating an xpath like
     *
     * instance('casedb')/casedb/case[@case_type='person'][complex_filter = 'pass']
     *
     * If 500 <case/> nodes match the first predicate (='person') the context scope will escalate
     * to 500. This lets individual expressions later (like 'complex_filter') identify that it's
     * worth them doing a bit of extra work if they can anticipate making the 'next' evaluation
     * faster.
     */
    private var contextScope = 1

    constructor() {
        cache = QueryCacheHost()
    }

    private constructor(parent: QueryContext) {
        this.traceRoot = parent.traceRoot
        this.cache = QueryCacheHost(parent.cache)
        this.contextScope = parent.contextScope
    }

    /**
     * @param newScope the magnitude of the new query
     * @return either the existing QueryContext or a new (child) QueryContext
     * if the magnitude of the new query exceeds the parent sufficiently
     */
    fun checkForDerivativeContextAndReturn(newScope: Int): QueryContext {
        //TODO: I think we may need to clear the spawned context's spawned context (maybe?) if it
        // was generated
        potentialSpawnedContext = null
        val newContext = QueryContext(this)
        newContext.contextScope = newScope

        return if (dominates(newContext.contextScope, this.contextScope)) {
            this.reportContextEscalation(newContext, "New")
            newContext
        } else {
            this
        }
    }

    /**
     * While performing a query, the result of one part of some internal query may be of sufficient
     * scope that even though the current context is small (O(10)), the scope of the internal query
     * may be much, much larger.
     *
     * In those cases an "inline" or temporary context can be spawned for the remainder of the
     * internal evaluation. This may either activate optimizations which would otherwise remain
     * dormant, or provide a new context cache which can be cleared/reclaimed after the internal
     * query finishes.
     *
     * @return either this context or a new query context to be used when evaluating subsequent
     * aspects of a partially completed query.
     */
    fun testForInlineScopeEscalation(newScope: Int): QueryContext {
        return if (dominates(newScope, contextScope)) {
            val spawned = QueryContext(this)
            spawned.contextScope = newScope
            potentialSpawnedContext = spawned
            reportContextEscalation(spawned, "Temporary")
            spawned
        } else {
            this
        }
    }

    fun getScope(): Int {
        return this.contextScope
    }

    /**
     * @param newScope the scope of the new query
     * @param existingScope the scope of the existing (parent) query
     * @return Whether the new scope is larger than the current, exceeds the threshold for
     * performing a bulk query, and exceeds 10x the existing scope
     */
    private fun dominates(newScope: Int, existingScope: Int): Boolean {
        return newScope > existingScope &&
                newScope > BULK_QUERY_THRESHOLD &&
                newScope / existingScope > 10
    }

    private fun reportContextEscalation(newContext: QueryContext, label: String) {
        val trace = EvaluationTrace("$label Query Context [${newContext.contextScope}]")
        trace.setOutcome("")
        reportTrace(trace)
    }

    fun reportTrace(trace: EvaluationTrace) {
        traceRoot?.reportSubtrace(trace)
    }

    fun setTraceRoot(traceRoot: EvaluationContext?) {
        this.traceRoot = traceRoot
    }

    fun <T : QueryCache> getQueryCache(cacheType: Class<T>): T {
        return cache.getQueryCache(cacheType)
    }

    fun <T : QueryCache> getQueryCacheOrNull(cacheType: Class<T>): T? {
        return cache.getQueryCacheOrNull(cacheType)
    }

    fun setHackyOriginalContextBody(hackyOriginalContextBody: CurrentModelQuerySet?) {
        if (hackyOriginalContextBody != null) {
            getQueryCache(QuerySetCache::class.java)
                .addModelQuerySet(CurrentModelQuerySet.CURRENT_QUERY_SET_ID, hackyOriginalContextBody)
        }
    }

    /**
     * Creates a new child context from this base context
     */
    fun forceNewChildContext(): QueryContext {
        return QueryContext(this)
    }

    companion object {
        /**
         * Tuning parameter to not trigger optimizations for low volume queries.
         *
         * Currently not easy to evaluate this programmatically, but should be set at a point which
         * prevents super basic queries from setting up too much overhead.
         */
        const val BULK_QUERY_THRESHOLD = 50
    }
}
