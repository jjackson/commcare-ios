package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance

class XPathArithExpr : XPathBinaryOpExpr {

    @Suppress("unused")
    constructor() // for deserialization

    constructor(op: Int, a: XPathExpression, b: XPathExpression) : super(op, a, b)

    override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        val aval = FunctionUtils.toNumeric(a!!.eval(model, evalContext))
        val bval = FunctionUtils.toNumeric(b!!.eval(model, evalContext))

        var result = 0.0
        when (op) {
            ADD -> result = aval + bval
            SUBTRACT -> result = aval - bval
            MULTIPLY -> result = aval * bval
            DIVIDE -> result = aval / bval
            MODULO -> result = aval % bval
        }
        return java.lang.Double.valueOf(result)
    }

    override fun toString(): String {
        var sOp: String? = null

        when (op) {
            ADD -> sOp = "+"
            SUBTRACT -> sOp = "-"
            MULTIPLY -> sOp = "*"
            DIVIDE -> sOp = "/"
            MODULO -> sOp = "%"
        }

        return super.toString(sOp)
    }

    override fun toPrettyString(): String {
        val prettyA = a!!.toPrettyString()
        val prettyB = b!!.toPrettyString()
        val opString: String
        when (op) {
            ADD -> opString = " + "
            SUBTRACT -> opString = " - "
            MULTIPLY -> opString = " * "
            DIVIDE -> opString = " div "
            MODULO -> opString = " mod "
            else -> return "unknown_operator($prettyA, $prettyB)"
        }

        return prettyA + opString + prettyB
    }

    override fun equals(o: Any?): Boolean {
        return (this === o) ||
                ((o is XPathArithExpr) && binOpEquals(o))
    }

    companion object {
        const val ADD = 0
        const val SUBTRACT = 1
        const val MULTIPLY = 2
        const val DIVIDE = 3
        const val MODULO = 4
    }
}
