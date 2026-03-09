package org.commcare.modern.engine.cases

import org.commcare.cases.instance.CaseInstanceTreeElement
import org.commcare.cases.query.QueryContext
import org.commcare.cases.query.queryset.CaseQuerySetLookup
import org.commcare.cases.query.queryset.DerivedCaseQueryLookup
import org.commcare.cases.query.queryset.DualTableSingleMatchModelQuerySet
import org.commcare.cases.query.queryset.ModelQuerySet
import org.commcare.cases.query.queryset.QuerySetLookup
import org.commcare.cases.query.queryset.QuerySetTransform
import org.commcare.modern.util.PerformanceTuningUtil
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.trace.EvaluationTrace

/**
 * Created by ctsims on 2/6/2017.
 */
class CaseIndexQuerySetTransform(private val table: CaseIndexTable?) : QuerySetTransform {

    override fun getTransformedLookup(incoming: QuerySetLookup, relativeLookup: TreeReference): QuerySetLookup? {
        if (incoming.queryModelId == CaseQuerySetLookup.CASE_MODEL_ID) {
            if (relativeLookup.size() == 2 && "index" == relativeLookup.getName(0)) {
                val indexName = relativeLookup.getName(1)!!
                return CaseIndexQuerySetLookup(indexName, incoming, table)
            }
        }
        return null
    }

    class CaseIndexQuerySetLookup(
        internal var indexName: String,
        incoming: QuerySetLookup,
        internal var table: CaseIndexTable?
    ) : DerivedCaseQueryLookup(incoming) {

        override fun loadModelQuerySet(queryContext: QueryContext): ModelQuerySet {
            val trace = EvaluationTrace("Load Query Set Transform[" +
                    rootLookup.currentQuerySetId + "]=>[" +
                    this.currentQuerySetId + "]")

            val querySetBody = rootLookup.getLookupSetBody(queryContext)
            val ret = table!!.bulkReadIndexToCaseIdMatch(indexName, querySetBody)
            cacheCaseModelQuerySet(queryContext, ret)

            trace.setOutcome("Loaded: " + ret.getSetBody().size)

            queryContext.reportTrace(trace)
            return ret
        }

        private fun cacheCaseModelQuerySet(queryContext: QueryContext, ret: DualTableSingleMatchModelQuerySet) {
            val modelQueryMagnitude = ret.getSetBody().size
            if (modelQueryMagnitude > QueryContext.BULK_QUERY_THRESHOLD && modelQueryMagnitude < PerformanceTuningUtil.getMaxPrefetchCaseBlock()) {
                queryContext.getQueryCache(RecordSetResultCache::class.java)
                    .reportBulkRecordSet(this.currentQuerySetId,
                        CaseInstanceTreeElement.MODEL_NAME, ret.getSetBody())
            }
        }

        override val queryModelId: String
            get() = rootLookup.queryModelId

        override val currentQuerySetId: String
            get() = rootLookup.currentQuerySetId + "|index|" + indexName
    }
}
