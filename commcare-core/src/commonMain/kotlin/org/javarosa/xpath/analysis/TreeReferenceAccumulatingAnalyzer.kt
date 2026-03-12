package org.javarosa.xpath.analysis

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference

/**
 * Accumulates all of the treereferences that are included in a given xpath expression.
 *
 * TODO: Once we use the fork/join pattern, this analyzer should possibly detect in "no-context"
 * mode that current() was dereferenced to mean "./", which is a huge potential source of issues.
 *
 * Created by ctsims on 9/15/2017.
 */
class TreeReferenceAccumulatingAnalyzer : XPathAccumulatingAnalyzer<TreeReference> {

    constructor() : super()

    constructor(context: EvaluationContext) : super() {
        setContext(context)
    }

    @Throws(AnalysisInvalidException::class)
    override fun doNormalTreeRefAnalysis(treeRef: TreeReference) {
        addToResult(treeRef.removePredicates())
    }

    override fun initSameTypeAnalyzer(): XPathAnalyzer {
        return TreeReferenceAccumulatingAnalyzer()
    }
}
