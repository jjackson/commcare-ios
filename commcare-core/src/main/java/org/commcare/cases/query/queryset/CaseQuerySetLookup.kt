package org.commcare.cases.query.queryset

import org.commcare.cases.query.QueryCache
import org.commcare.cases.query.QueryContext
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference

/**
 * The "Root" lookup into the case query set, matches a current index into a case model id, and
 * provides a basis for transformed lookups.
 *
 * Created by ctsims on 2/6/2017.
 */
class CaseQuerySetLookup(
    private val caseDbRoot: TreeReference,
    private val multiplicityMap: Map<Int, Int>
) : QuerySetLookup {

    companion object {
        const val CASE_MODEL_ID = "case"
    }

    override fun isValid(lookupIdKey: TreeReference?, context: QueryContext): Boolean {
        if (lookupIdKey == null) {
            return false
        } else {
            val set = context.getQueryCache(QuerySetCache::class.java)
                .getModelQuerySet(CurrentModelQuerySet.CURRENT_QUERY_SET_ID)
            return set != null
        }
    }

    override val queryModelId: String
        get() = CASE_MODEL_ID

    override val currentQuerySetId: String
        get() = CurrentModelQuerySet.CURRENT_QUERY_SET_ID

    override fun getLookupIdKey(evaluationContext: EvaluationContext): TreeReference? {
        val current = evaluationContext.getOriginalContext() ?: return null
        if (current.size() < 1) {
            return null
        }
        val generic = current.genericizeAfter(current.size() - 1)
        if (generic == caseDbRoot) {
            return current
        }
        return null
    }

    override fun performSetLookup(lookupIdKey: TreeReference, queryContext: QueryContext): List<Int>? {
        val match = queryContext.getQueryCache(CaseQuerySetLookupCache::class.java)
            .lookupQuerySetMatch(queryContext, lookupIdKey, multiplicityMap)
            ?: return null
        val returnVal = ArrayList<Int>()
        returnVal.add(match)
        return returnVal
    }

    override fun getLookupSetBody(queryContext: QueryContext): Set<Int> {
        return queryContext.getQueryCache(CaseQuerySetLookupCache::class.java)
            .getLookupSetBody(queryContext, multiplicityMap)
    }

    class CaseQuerySetLookupCache : QueryCache {
        private var caseQueryIndex: MutableMap<TreeReference, Int>? = null
        private var lookupSetBody: MutableSet<Int>? = null

        fun lookupQuerySetMatch(
            context: QueryContext,
            currentRef: TreeReference,
            multiplicityMap: Map<Int, Int>
        ): Int? {
            if (caseQueryIndex == null) {
                loadCaseQuerySetCache(context, multiplicityMap)
            }
            return caseQueryIndex!![currentRef]
        }

        private fun loadCaseQuerySetCache(context: QueryContext, multiplicityMap: Map<Int, Int>) {
            val set = context
                .getQueryCache(QuerySetCache::class.java)
                .getModelQuerySet(CurrentModelQuerySet.CURRENT_QUERY_SET_ID) as CurrentModelQuerySet

            val index = HashMap<TreeReference, Int>()
            val body = LinkedHashSet<Int>()
            for (ref in set.currentQuerySet) {
                val mult = ref.getMultiplicity(ref.size() - 1)
                val modelId = multiplicityMap[mult]
                index[ref] = modelId!!
                body.add(modelId)
            }
            caseQueryIndex = index
            lookupSetBody = body
        }

        fun getLookupSetBody(context: QueryContext, multiplicityMap: Map<Int, Int>): Set<Int> {
            if (caseQueryIndex == null) {
                loadCaseQuerySetCache(context, multiplicityMap)
            }
            return lookupSetBody!!
        }
    }
}
