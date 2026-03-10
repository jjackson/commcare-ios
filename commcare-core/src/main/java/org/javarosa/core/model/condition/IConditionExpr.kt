package org.javarosa.core.model.condition

import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.Externalizable

/**
 * A condition expression is an expression which is evaluated against the current
 * model and produces a value. These objects should keep track of the expression that
 * they evaluate, as well as being able to identify what references will require the
 * condition to be triggered.
 *
 * As a metaphor into an XForm, a Condition expression represents an XPath expression
 * which you can query for a value (in a calculate or relevancy condition, for instance),
 * can tell you what nodes its value will depend on, and optionally what values it "Pivots"
 * around. (IE: if an expression's value is true if a node is > 25, and false otherwise, it
 * has a "Comparison Pivot" around 25).
 *
 * @author ctsims
 */
interface IConditionExpr : Externalizable {

    /**
     * Evaluate this expression against the current models and
     * context and provide a true or false value.
     */
    fun eval(model: DataInstance<*>?, evalContext: EvaluationContext?): Boolean

    /**
     * Evaluate this expression against the current models and
     * context and provide the final value of the expression, without
     * forcing a cast to a boolean value.
     */
    fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext?): Any?

    /**
     * Used for itemsets. Fill this documentation in.
     */
    fun evalReadable(model: DataInstance<*>?, evalContext: EvaluationContext?): String?

    /**
     * Used for itemsets. Fill this documentation in.
     */
    fun evalNodeset(model: DataInstance<*>?, evalContext: EvaluationContext?): ArrayList<TreeReference>

    /**
     * Provides a list of all of the references that this expression's value depends upon
     * directly. These values can't be contextualized fully (since these triggers are necessary
     * before runtime), but should only need to be contextualized to be a complete set.
     *
     * @param originalContextRef context reference pointing to the nodeset
     *                           reference; used for expanding 'current()'
     * @return References that are dependant on this expression's value.
     */
    fun getExprsTriggers(originalContextRef: TreeReference?): ArrayList<TreeReference>

    /**
     * Provide a list of Pivots around which this Condition Expression depends.
     *
     * Optional to implement. If not implemented, throw an Unpivotable Expression exception
     * to signal that the expression cannot be statically evaluated.
     */
    @Throws(UnpivotableExpressionException::class)
    fun pivot(model: DataInstance<*>?, evalContext: EvaluationContext?): ArrayList<Any>
}
