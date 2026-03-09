package org.commcare.cases.query.handlers

import org.commcare.cases.query.PredicateProfile
import org.commcare.cases.query.QueryContext
import org.commcare.cases.query.QueryHandler
import org.commcare.cases.query.queryset.ModelQueryLookup
import org.commcare.cases.query.queryset.ModelQuerySetMatcher
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.trace.EvaluationTrace
import org.javarosa.xpath.expr.XPathExpression
import java.util.Vector

/**
 * Optimizes bulk queries which match model sets detected in the predicates.
 *
 * Relies on a QuerySetMatcher to break down the predicate patterns which match the current model.
 *
 * Created by ctsims on 1/31/2017.
 */
class ModelQueryLookupHandler(private val matcher: ModelQuerySetMatcher) : QueryHandler<ModelQueryLookup> {

    override fun getExpectedRuntime(): Int = 1

    override fun profileHandledQuerySet(profiles: Vector<PredicateProfile>): ModelQueryLookup? {
        if (profiles[0] is ModelQueryLookup) {
            return profiles[0] as ModelQueryLookup
        }
        return null
    }

    override fun loadProfileMatches(querySet: ModelQueryLookup, queryContext: QueryContext): List<Int>? {
        val lookup = querySet.setLookup

        val trace = EvaluationTrace("QuerySetLookup|" + lookup.currentQuerySetId)

        val lookupData = lookup.performSetLookup(querySet.rootLookupRef, queryContext)

        if (lookupData != null) {
            trace.setOutcome("Results: " + lookupData.size)
            queryContext.reportTrace(trace)
        }

        return lookupData
    }

    override fun updateProfiles(querySet: ModelQueryLookup, profiles: Vector<PredicateProfile>) {
        profiles.remove(querySet)
    }

    override fun collectPredicateProfiles(
        predicates: Vector<XPathExpression>,
        context: QueryContext,
        evaluationContext: EvaluationContext
    ): Collection<PredicateProfile>? {
        val lookup = matcher.getQueryLookupFromPredicate(predicates.elementAt(0)) ?: return null

        val ref = lookup.getLookupIdKey(evaluationContext)

        if (!lookup.isValid(ref, context)) {
            return null
        }

        val validRef = ref ?: return null
        val newProfile = Vector<PredicateProfile>()
        newProfile.add(ModelQueryLookup(lookup.queryModelId, lookup, validRef))
        return newProfile
    }
}
