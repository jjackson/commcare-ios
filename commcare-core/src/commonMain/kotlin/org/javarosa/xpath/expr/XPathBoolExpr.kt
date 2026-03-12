package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance

class XPathBoolExpr : XPathBinaryOpExpr {

    @Suppress("unused")
    constructor() // for deserialization

    constructor(op: Int, a: XPathExpression, b: XPathExpression) : super(op, a, b)

    override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        val aval = FunctionUtils.toBoolean(a!!.eval(model, evalContext))

        //short-circuiting
        if ((!aval && op == AND) || (aval && op == OR)) {
            return aval
        }

        val bval = FunctionUtils.toBoolean(b!!.eval(model, evalContext))

        var result = false
        when (op) {
            AND -> result = aval && bval
            OR -> result = aval || bval
        }
        return result
    }

    override fun toString(): String {
        var sOp: String? = null

        when (op) {
            AND -> sOp = "and"
            OR -> sOp = "or"
        }

        return super.toString(sOp)
    }

    override fun toPrettyString(): String {
        val prettyA = a!!.toPrettyString()
        val prettyB = b!!.toPrettyString()
        val opString: String
        when (op) {
            AND -> opString = " and "
            OR -> opString = " or "
            else -> return "unknown_operator($prettyA, $prettyB)"
        }

        return prettyA + opString + prettyB
    }

    override fun equals(o: Any?): Boolean {
        return (this === o) ||
                ((o is XPathBoolExpr) && binOpEquals(o))
    }

    companion object {
        const val AND = 0
        const val OR = 1
    }
}
