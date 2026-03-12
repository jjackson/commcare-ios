package org.javarosa.xpath.analysis

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference

/**
 * Analyzes an XPath expression to determine whether it contains a reference to the main data
 * instance.
 *
 * @author Aliza Stone
 */
class ReferencesMainInstanceAnalyzer : XPathBooleanAnalyzer {

    constructor(ec: EvaluationContext) : this() {
        setContext(ec)
    }

    constructor() : super()

    @Throws(AnalysisInvalidException::class)
    override fun doNormalTreeRefAnalysis(treeRef: TreeReference) {
        if (referenceRefersToMainInstance(treeRef)) {
            this.result = true
            this.shortCircuit = true
        }
    }

    private fun referenceRefersToMainInstance(treeRef: TreeReference): Boolean {
        return treeRef.contextType == TreeReference.CONTEXT_ABSOLUTE &&
                treeRef.instanceName == null
    }

    override fun getDefaultValue(): Boolean {
        return false
    }

    override fun aggregateResults(): Boolean {
        return orResults()
    }

    override fun initSameTypeAnalyzer(): XPathAnalyzer {
        return ReferencesMainInstanceAnalyzer()
    }
}
