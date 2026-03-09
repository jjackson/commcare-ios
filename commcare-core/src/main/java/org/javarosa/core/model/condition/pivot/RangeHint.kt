package org.javarosa.core.model.condition.pivot

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.IConditionExpr
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.xpath.expr.FunctionUtils
import java.util.Vector

/**
 * @author ctsims
 */
abstract class RangeHint<T : IAnswerData> : ConstraintHint {

    private var minValue: Double? = null
    private var maxValue: Double? = null

    private var minCast: T? = null
    private var maxCast: T? = null

    var isMinInclusive: Boolean = false
        private set
    var isMaxInclusive: Boolean = false
        private set

    @Throws(UnpivotableExpressionException::class)
    override fun init(c: EvaluationContext?, conditional: IConditionExpr?, instance: FormInstance?) {
        val pivots = conditional!!.pivot(instance, c)

        val internalPivots = Vector<CmpPivot>()
        for (p in pivots) {
            if (p !is CmpPivot) {
                throw UnpivotableExpressionException()
            }
            internalPivots.addElement(p)
        }

        if (internalPivots.size > 1) {
            // For now.
            throw UnpivotableExpressionException()
        }

        for (pivot in internalPivots) {
            evaluatePivot(pivot, conditional, c!!, instance!!)
        }
    }

    fun getMin(): T? {
        return if (minValue == null) null else minCast
    }

    fun getMax(): T? {
        return if (maxValue == null) null else maxCast
    }

    @Throws(UnpivotableExpressionException::class)
    private fun evaluatePivot(
        pivot: CmpPivot,
        conditional: IConditionExpr,
        c: EvaluationContext,
        instance: FormInstance
    ) {
        val unit = unit()
        val value = pivot.getVal()
        val lt = value - unit
        val gt = value + unit

        c.isConstraint = true

        c.candidateValue = castToValue(value)
        val eq = FunctionUtils.toBoolean(conditional.eval(instance, c))

        c.candidateValue = castToValue(lt)
        val ltr = FunctionUtils.toBoolean(conditional.eval(instance, c))

        c.candidateValue = castToValue(gt)
        val gtr = FunctionUtils.toBoolean(conditional.eval(instance, c))

        if (ltr && !gtr) {
            maxValue = value
            isMaxInclusive = eq
            maxCast = castToValue(maxValue!!)
        }

        if (!ltr && gtr) {
            minValue = value
            isMinInclusive = eq
            minCast = castToValue(minValue!!)
        }
    }

    @Throws(UnpivotableExpressionException::class)
    protected abstract fun castToValue(value: Double): T

    protected abstract fun unit(): Double
}
