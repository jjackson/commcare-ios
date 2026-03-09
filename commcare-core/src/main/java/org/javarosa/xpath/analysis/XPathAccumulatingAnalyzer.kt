package org.javarosa.xpath.analysis

import org.javarosa.core.model.instance.TreeReference

/**
 * A type of XPathAnalyzer which collects and aggregates a specified type of information from
 * wherever it is present in the expression.
 *
 * IMPORTANT NOTE: An accumulating analyzer may analyze the same sub-expression or context ref of
 * an expression multiple times in a single analysis pass. This means:
 * - An AccumulatingAnalyzer is NOT appropriate to use for answering questions such as
 * "How many times is X referenced in this expression?"
 * - An accumulating analyzer IS appropriate to use for answering questions such as
 * "What is the set of all things of X type which are referenced at least one time in this expression?"
 *
 * @author Aliza Stone
 */
abstract class XPathAccumulatingAnalyzer<T> : XPathAnalyzer() {

    private var accumulated: MutableCollection<T> = ArrayList()

    internal fun addToResult(t: T) {
        accumulated.add(t)
    }

    internal fun size(): Int {
        return accumulated.size
    }

    override fun spawnSubAnalyzer(subContext: TreeReference?): XPathAnalyzer {
        @Suppress("UNCHECKED_CAST")
        val subAnalyzer = super.spawnSubAnalyzer(subContext) as XPathAccumulatingAnalyzer<T>
        subAnalyzer.accumulated = if (this.accumulated is Set<*>) HashSet() else ArrayList()
        return subAnalyzer
    }

    fun accumulate(rootExpression: XPathAnalyzable): Set<T>? {
        return try {
            accumulated = HashSet()
            rootExpression.applyAndPropagateAnalyzer(this)
            val resultSet = HashSet<T>()
            aggregateResults(resultSet)
            resultSet
        } catch (e: AnalysisInvalidException) {
            null
        }
    }

    // FOR TESTING PURPOSES ONLY -- This cannot be relied upon to not return duplicates
    fun accumulateAsList(rootExpression: XPathAnalyzable): List<T>? {
        return try {
            accumulated = ArrayList()
            rootExpression.applyAndPropagateAnalyzer(this)
            val resultList = ArrayList<T>()
            aggregateResults(resultList)
            resultList
        } catch (e: AnalysisInvalidException) {
            null
        }
    }

    private fun aggregateResults(resultCollection: MutableCollection<T>) {
        resultCollection.addAll(this.accumulated)
        for (subAnalyzer in this.subAnalyzers) {
            @Suppress("UNCHECKED_CAST")
            (subAnalyzer as XPathAccumulatingAnalyzer<T>).aggregateResults(resultCollection)
        }
    }
}
