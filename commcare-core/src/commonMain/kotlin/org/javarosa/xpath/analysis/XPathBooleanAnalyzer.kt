package org.javarosa.xpath.analysis

/**
 * A type of XPathAnalyzer that can evaluate an XPath expression for whether a given condition
 * is true or false for the expression as a whole.
 *
 * Implementing classes should implement doAnalysis() methods for any XPathAnalyzables that are
 * relevant to the condition they are interested in, and those methods should set the value of
 * `result` accordingly.
 *
 * @author Aliza Stone
 */
abstract class XPathBooleanAnalyzer : XPathAnalyzer() {

    protected var result: Boolean = getDefaultValue()

    protected abstract fun getDefaultValue(): Boolean

    protected abstract fun aggregateResults(): Boolean

    @Throws(AnalysisInvalidException::class)
    fun computeResult(rootExpression: XPathAnalyzable): Boolean {
        rootExpression.applyAndPropagateAnalyzer(this)
        return aggregateResults()
    }

    internal fun orResults(): Boolean {
        for (subAnalyzer in this.subAnalyzers) {
            if ((subAnalyzer as XPathBooleanAnalyzer).result) {
                return true
            }
        }
        return this.result
    }

    internal fun andResults(): Boolean {
        for (subAnalyzer in this.subAnalyzers) {
            if (!(subAnalyzer as XPathBooleanAnalyzer).result) {
                return false
            }
        }
        return this.result
    }
}
