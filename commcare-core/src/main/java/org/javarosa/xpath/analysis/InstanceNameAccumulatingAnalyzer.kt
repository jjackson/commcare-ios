package org.javarosa.xpath.analysis

import org.javarosa.core.model.instance.TreeReference

/**
 * An XPathAccumulatingAnalyzer that collects all of the instance names that are referenced
 * in an expression
 *
 * @author Aliza Stone
 */
class InstanceNameAccumulatingAnalyzer : XPathAccumulatingAnalyzer<String> {

    constructor() : super()

    constructor(contextRef: TreeReference?) : super() {
        setContext(contextRef)
    }

    @Throws(AnalysisInvalidException::class)
    override fun doNormalTreeRefAnalysis(treeRef: TreeReference) {
        if (treeRef.contextType == TreeReference.CONTEXT_INSTANCE) {
            addToResult(treeRef.instanceName)
        }
    }

    @Throws(AnalysisInvalidException::class)
    override fun doAnalysisForRelativeTreeRef(expressionWithContextTypeRelative: TreeReference) {
        if (!this.isSubAnalyzer) {
            // For instance accumulation, relative refs only introduce something new to analyze
            // if they are in the top-level expression
            super.doAnalysisForRelativeTreeRef(expressionWithContextTypeRelative)
        } else {
            doNormalTreeRefAnalysis(expressionWithContextTypeRelative)
        }
    }

    override fun initSameTypeAnalyzer(): XPathAnalyzer {
        return InstanceNameAccumulatingAnalyzer()
    }
}
