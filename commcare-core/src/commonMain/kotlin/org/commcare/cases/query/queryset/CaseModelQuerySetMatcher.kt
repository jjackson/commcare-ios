package org.commcare.cases.query.queryset

import org.commcare.cases.instance.CaseInstanceTreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.expr.XPathEqExpr
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathPathExpr

/**
 * Generates potential model query set lookups for references into the case database model.
 *
 * Chains entity lookups where relevant using model set transforms, which can be added dynamically.
 *
 * example:
 * [@case_id = current()/@case_id]
 *
 * can be directly returned and interpreted as an model query set lookup which gets the current
 * case without needing to compare string Id's, match on looked up values, etc.
 *
 * Created by ctsims on 2/6/2017.
 */
class CaseModelQuerySetMatcher private constructor(
    modelId: String,
    private val multiplicityMap: Map<Int, Int>
) : ModelQuerySetMatcher {

    private val membershipIndexes: Collection<XPathExpression>
    private val caseDbRoot: TreeReference
    private val querySetTransforms: ArrayList<QuerySetTransform> = ArrayList()

    constructor(multiplicityMap: Map<Int, Int>) : this("casedb", multiplicityMap)

    init {
        caseDbRoot =
            XPathReference.getPathExpr("instance('$modelId')/casedb/case").getReference()

        // Later on we need this to refer to a real element at casedb, not a virtual one
        caseDbRoot.setMultiplicity(0, 0)

        membershipIndexes = ArrayList<XPathExpression>().apply {
            add(CaseInstanceTreeElement.CASE_ID_EXPR)
            add(CaseInstanceTreeElement.CASE_ID_EXPR_TWO)
        }
        addQuerySetTransform(CaseIdentityQuerySetTransform())
    }

    fun addQuerySetTransform(transform: QuerySetTransform) {
        querySetTransforms.add(transform)
    }

    override fun getQueryLookupFromPredicate(expr: XPathExpression): QuerySetLookup? {
        if (expr is XPathEqExpr && expr.op == XPathEqExpr.EQ) {
            if (membershipIndexes.contains(expr.a)) {
                if (expr.b is XPathPathExpr) {
                    val ref = (expr.b as XPathPathExpr).getReference()
                    return getQuerySetLookup(ref)
                }
            }
        }
        return null
    }

    override fun getQuerySetLookup(ref: TreeReference): QuerySetLookup? {
        val lookup: QuerySetLookup
        val remainder: TreeReference

        if (caseDbRoot.isParentOf(ref, false)) {
            if (!ref.hasPredicates()) {
                return null
            }

            val predicates = ref.getPredicate(caseDbRoot.size() - 1)
            if (predicates == null || predicates.size > 1) {
                return null
            }

            val tempLookup = getQueryLookupFromPredicate(predicates[0]) ?: return null
            lookup = tempLookup
            remainder = ref.getRelativeReferenceAfter(caseDbRoot.size())
        } else if (isCurrentRef(ref)) {
            lookup = CaseQuerySetLookup(caseDbRoot, multiplicityMap)
            remainder = ref.getRelativeReferenceAfter(0)
        } else {
            return null
        }

        return getTransformedQuerySetLookup(lookup, remainder)
    }

    private fun getTransformedQuerySetLookup(
        lookup: QuerySetLookup,
        remainder: TreeReference
    ): QuerySetLookup? {
        for (transform in querySetTransforms) {
            val retVal = transform.getTransformedLookup(lookup, remainder)
            if (retVal != null) {
                return retVal
            }
        }
        return null
    }

    private fun isCurrentRef(ref: TreeReference): Boolean {
        return ref.contextType == TreeReference.CONTEXT_ORIGINAL
    }

    /**
     * A transform for the situation where the /@case_id step is taken relative to an existing
     * case model query set lookup.
     */
    private class CaseIdentityQuerySetTransform : QuerySetTransform {
        companion object {
            private val caseIdRef: TreeReference = CaseInstanceTreeElement.CASE_ID_EXPR.getReference()
        }

        override fun getTransformedLookup(
            incoming: QuerySetLookup,
            relativeLookup: TreeReference
        ): QuerySetLookup? {
            return if (caseIdRef == relativeLookup) {
                incoming
            } else {
                null
            }
        }
    }
}
