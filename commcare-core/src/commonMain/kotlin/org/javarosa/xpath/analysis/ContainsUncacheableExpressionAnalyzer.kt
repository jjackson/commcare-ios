package org.javarosa.xpath.analysis

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.xpath.expr.VolatileXPathFuncExpr
import org.javarosa.xpath.expr.XPathFuncExpr

/**
 * Analyzes an XPath expression to determine whether it is or contains an XPathFuncExpr that is
 * un-cacheable by its nature (such as now() or random()).
 *
 * @author Aliza Stone
 */
class ContainsUncacheableExpressionAnalyzer : XPathBooleanAnalyzer {

    constructor(ec: EvaluationContext) : super() {
        setContext(ec)
    }

    constructor() : super()

    override fun doAnalysis(expr: XPathFuncExpr?) {
        if (expr is VolatileXPathFuncExpr) {
            this.result = true
            this.shortCircuit = true
        }
    }

    override fun getDefaultValue(): Boolean {
        return false
    }

    override fun aggregateResults(): Boolean {
        return orResults()
    }

    override fun initSameTypeAnalyzer(): XPathAnalyzer {
        return ContainsUncacheableExpressionAnalyzer()
    }
}
