package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.pivot.CmpPivot
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException
import org.javarosa.core.model.data.DecimalData
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathNodeset


class XPathCmpExpr : XPathBinaryOpExpr {

    @Suppress("unused")
    constructor() // for deserialization

    constructor(op: Int, a: XPathExpression, b: XPathExpression) : super(op, a, b)

    override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        val aval = a!!.eval(model, evalContext)
        val bval = b!!.eval(model, evalContext)
        var result = false

        //xpath spec says comparisons only defined for numbers (not defined for strings)
        val aNum = FunctionUtils.toNumeric(aval)
        val bNum = FunctionUtils.toNumeric(bval)

        val fa = aNum as Double
        val fb = bNum as Double

        when (op) {
            LT -> result = fa < fb
            GT -> result = fa > fb
            LTE -> result = fa <= fb
            GTE -> result = fa >= fb
        }

        return result
    }

    override fun toString(): String {
        var sOp: String? = null

        when (op) {
            LT -> sOp = "<"
            GT -> sOp = ">"
            LTE -> sOp = "<="
            GTE -> sOp = ">="
        }

        return super.toString(sOp)
    }

    @Throws(UnpivotableExpressionException::class)
    override fun pivot(model: DataInstance<*>?, evalContext: EvaluationContext, pivots: ArrayList<Any>, sentinal: Any?): Any? {
        val aval = a!!.pivot(model, evalContext, pivots, sentinal)
        var bval = b!!.pivot(model, evalContext, pivots, sentinal)
        if (bval is XPathNodeset) {
            bval = bval.unpack()
        }

        if (handled(aval, bval, sentinal, pivots) || handled(bval, aval, sentinal, pivots)) {
            return null
        }

        return this.eval(model, evalContext)
    }

    @Throws(UnpivotableExpressionException::class)
    private fun handled(a: Any?, b: Any?, sentinal: Any?, pivots: ArrayList<Any>): Boolean {
        if (sentinal === a) {
            if (b == null) {
                //Can't pivot on an expression which is derived from pivoted expressions
                throw UnpivotableExpressionException()
            } else if (sentinal === b) {
                //WTF?
                throw UnpivotableExpressionException()
            } else {
                var `val`: Double? = null
                //either of
                if (b is Double) {
                    `val` = b
                } else {
                    //These are probably the
                    if (b is Int) {
                        `val` = b.toDouble()
                    } else if (b is Long) {
                        `val` = b.toDouble()
                    } else if (b is Float) {
                        `val` = b.toDouble()
                    } else if (b is Short) {
                        `val` = b.toDouble()
                    } else if (b is Byte) {
                        `val` = b.toDouble()
                    } else {
                        if (b is String) {
                            try {
                                //TODO: Too expensive?
                                `val` = DecimalData().cast(UncastData(b)).getValue() as Double
                            } catch (e: Exception) {
                                throw UnpivotableExpressionException("Unrecognized numeric data in cmp expression: $b")
                            }
                        } else {
                            throw UnpivotableExpressionException("Unrecognized numeric data in cmp expression: $b")
                        }
                    }
                }

                pivots.add(CmpPivot(`val`!!, op))
                return true
            }
        }
        return false
    }

    override fun toPrettyString(): String {
        val prettyA = a!!.toPrettyString()
        val prettyB = b!!.toPrettyString()
        val opString: String
        when (op) {
            LT -> opString = " < "
            GT -> opString = " > "
            LTE -> opString = " <= "
            GTE -> opString = " >= "
            else -> return "unknown_operator($prettyA, $prettyB)"
        }

        return prettyA + opString + prettyB
    }

    override fun equals(o: Any?): Boolean {
        return (this === o) ||
                ((o is XPathCmpExpr) && binOpEquals(o))
    }

    companion object {
        const val LT = 0
        const val GT = 1
        const val LTE = 2
        const val GTE = 3
    }
}
