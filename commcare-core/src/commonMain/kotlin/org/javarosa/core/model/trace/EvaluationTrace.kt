package org.javarosa.core.model.trace

import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.expr.FunctionUtils

/**
 * Captures details about the outcome of a "Step" of expression execution, and
 * its recursive subexpressions.
 *
 * @author ctsims
 */
open class EvaluationTrace(private val expression: String) {

    private val exprStartNano: Long = org.javarosa.core.util.platformNanoTime()
    private var runtimeNano: Long = 0

    private var parent: EvaluationTrace? = null
    private var value: Any? = null
    private var usedExpressionCache: Boolean = false

    private val children: ArrayList<EvaluationTrace> = ArrayList()

    fun setParent(parent: EvaluationTrace?) {
        if (this.parent != null) {
            throw RuntimeException("A trace's parent can only be set once")
        }
        this.parent = parent
    }

    /**
     * @return The parent step of this trace. Null if
     * this is the root of the expression evaluation
     */
    fun getParent(): EvaluationTrace? {
        return parent
    }

    /**
     * Set the outcome value of this evaluation step
     *
     * @param value set the outcome of evaluating this expression
     */
    open fun setOutcome(value: Any?) {
        setOutcome(value, false)
    }

    open fun setOutcome(value: Any?, fromCache: Boolean) {
        this.value = value
        this.usedExpressionCache = fromCache
        triggerExprComplete()
    }

    protected fun triggerExprComplete() {
        runtimeNano = org.javarosa.core.util.platformNanoTime() - exprStartNano
    }

    open fun getRuntimeInNanoseconds(): Long {
        return runtimeNano
    }

    fun addSubTrace(child: EvaluationTrace?) {
        this.children.add(child!!)
    }

    open fun getSubTraces(): ArrayList<EvaluationTrace> {
        return children
    }

    open fun getExpression(): String? {
        return expression
    }

    /**
     * @return The outcome of the expression's execution.
     */
    open fun getValue(): String? {
        // Temporarily deal with this in a flat manner until we can evaluate
        // more robustly
        val currentValue = value
        if (currentValue is XPathNodeset) {
            return FunctionUtils.getSerializedNodeset(currentValue)
        }
        return FunctionUtils.toString(currentValue)
    }

    open fun evaluationUsedExpressionCache(): Boolean {
        return usedExpressionCache
    }

    open fun getProfileReport(): String? {
        return null
    }

    open fun getCacheReport(): String {
        return "" + usedExpressionCache
    }
}
