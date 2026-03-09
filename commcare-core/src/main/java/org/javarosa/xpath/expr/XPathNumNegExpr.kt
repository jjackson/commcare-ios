package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance

class XPathNumNegExpr : XPathUnaryOpExpr {
    @Suppress("unused")
    constructor() // for deserialization

    constructor(a: XPathExpression) : super(a)

    override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        val aval = FunctionUtils.toNumeric(a!!.eval(model, evalContext))
        return -aval
    }

    override fun toString(): String {
        return "{unop-expr:num-neg,${a.toString()}}"
    }

    override fun equals(o: Any?): Boolean {
        if (o is XPathNumNegExpr) {
            return super.equals(o)
        } else {
            return false
        }
    }

    override fun toPrettyString(): String {
        return "-${a!!.toPrettyString()}"
    }
}
