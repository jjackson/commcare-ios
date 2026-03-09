package org.javarosa.core.model.trace

import org.javarosa.core.model.instance.TreeReference
import org.javarosa.xpath.expr.XPathExpression
import java.util.Vector

/**
 * A bulk evaluation trace records that instead of actually running a set of predicate expressions,
 * they were handled by an optimized lookup that short-circuited the iterative evaluation of each
 * potential match.
 *
 * Created by ctsims on 1/24/2017.
 */
class BulkEvaluationTrace : EvaluationTrace("") {

    private var bulkEvaluationSucceeded: Boolean = false
    private var predicatesCovered: String? = null
    private var outputValue: String? = null

    /**
     * Set the outcome value of this evaluation step
     *
     * @param value set the outcome of evaluating this expression
     */
    override fun setOutcome(value: Any?) {
        throw RuntimeException("Bulk evaluation shouldn't have set outcome called on it")
    }

    fun setEvaluatedPredicates(
        startingSet: Vector<XPathExpression>?,
        finalSet: Vector<XPathExpression>?,
        childSet: Collection<TreeReference>?
    ) {
        this.triggerExprComplete()

        if (startingSet == null) {
            bulkEvaluationSucceeded = false
            return
        }
        val predicatesCounted = startingSet.size - finalSet!!.size
        if (predicatesCounted == 0) {
            bulkEvaluationSucceeded = false
            return
        }

        bulkEvaluationSucceeded = true

        val sb = StringBuilder()
        for (i in 0 until predicatesCounted) {
            sb.append("[").append(startingSet[i].toPrettyString()).append("]")
        }
        this.predicatesCovered = sb.toString()

        this.outputValue = "Results: " + childSet!!.size
    }

    override fun getExpression(): String? {
        return predicatesCovered
    }

    override fun getValue(): String? {
        return outputValue
    }

    fun isBulkEvaluationSucceeded(): Boolean {
        return bulkEvaluationSucceeded
    }

    fun markClosed() {
        predicatesCovered = ""
        outputValue = ""
    }
}
