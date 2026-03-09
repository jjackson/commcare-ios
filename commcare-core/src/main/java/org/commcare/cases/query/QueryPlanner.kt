package org.commcare.cases.query

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.xpath.expr.XPathExpression
import java.util.Collections
import java.util.Vector

/**
 * Class that loads, runs, and manages the QueryHandlers during storage lookups
 *
 * Created by ctsims on 1/25/2017.
 */
open class QueryPlanner {

    private val handlers: MutableList<QueryHandler<*>> = Vector()

    /**
     * @param profiles the predicate profiles to be attempted to run
     * @param currentQueryContext the QueryContext of the current lookup
     * @return null if the query could not be handled by this planner
     *
     * Note: Profiles that have been run should be removed by the handler
     */
    fun attemptProfiledQuery(
        profiles: Vector<PredicateProfile>,
        currentQueryContext: QueryContext
    ): List<Int>? {
        for (i in 0 until handlers.size) {
            @Suppress("UNCHECKED_CAST")
            val handler = handlers[i] as QueryHandler<Any?>
            val queryPlan = handler.profileHandledQuerySet(profiles)
            if (queryPlan != null) {
                val retVal = handler.loadProfileMatches(queryPlan, currentQueryContext)
                if (retVal != null) {
                    handler.updateProfiles(queryPlan, profiles)
                    return retVal
                }
            }
        }
        return null
    }

    fun addQueryHandler(handler: QueryHandler<*>) {
        handlers.add(handler)
        Collections.sort(handlers) { first, second ->
            first.getExpectedRuntime() - second.getExpectedRuntime()
        }
    }

    fun collectPredicateProfiles(
        predicates: Vector<XPathExpression>?,
        queryContext: QueryContext,
        evalContext: EvaluationContext
    ): Collection<PredicateProfile>? {
        if (predicates == null) {
            return null
        }
        val returnProfile = Vector<PredicateProfile>()
        for (i in 0 until handlers.size) {
            val profile = handlers[i].collectPredicateProfiles(predicates, queryContext, evalContext)
            if (profile != null) {
                returnProfile.addAll(profile)
            }
        }
        return returnProfile
    }
}
