package org.commcare.modern.engine.cases

import org.commcare.cases.model.Case
import org.commcare.cases.query.queryset.DualTableSingleMatchModelQuerySet

import java.util.HashMap
import java.util.LinkedHashSet
import java.util.Vector

/**
 * Created by willpride on 2/21/17.
 */
interface CaseIndexTable {
    fun loadIntoIndexTable(indexCache: HashMap<String, Vector<Int>>, indexName: String): Int

    fun bulkReadIndexToCaseIdMatch(indexName: String, cuedCases: Collection<Int>): DualTableSingleMatchModelQuerySet

    fun getCasesMatchingValueSet(indexName: String, valueSet: Array<String>): LinkedHashSet<Int>

    fun getCasesMatchingIndex(indexName: String, value: String): LinkedHashSet<Int>

    fun indexCase(c: Case)

    fun clearCaseIndices(idsToClear: Collection<Int>)

    fun delete()

    fun isStorageExists(): Boolean
}
