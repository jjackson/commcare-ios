package org.javarosa.xpath.analysis

import org.javarosa.core.model.instance.TreeReference

/**
 * An XPathAccumulatingAnalyzer that collects all of the TreeReference context types that are relevant
 * to the evaluation of JUST the top level of the given expression, i.e. not including predicates
 *
 * @author Aliza Stone
 */
class TopLevelContextTypesAnalyzer : XPathAccumulatingAnalyzer<Int>() {

    @Throws(AnalysisInvalidException::class)
    override fun doAnalysis(ref: TreeReference) {
        addToResult(ref.contextType)
    }

    override fun initSameTypeAnalyzer(): XPathAnalyzer {
        return TopLevelContextTypesAnalyzer()
    }

    override fun shouldIncludePredicates(): Boolean {
        return false
    }

    override fun shortCircuit(): Boolean {
        // If we've gotten them all then no need to keep going
        return size() == TreeReference.CONTEXT_TYPES.size
    }
}
