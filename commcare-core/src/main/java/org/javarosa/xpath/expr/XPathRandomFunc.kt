package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.MathUtils
import org.javarosa.xpath.parser.XPathSyntaxException

// non-standard
open class XPathRandomFunc : XPathFuncExpr, VolatileXPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, false)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        //calculated expressions may be recomputed w/o warning! use with caution!!
        return MathUtils.getRand().nextDouble()
    }

    override fun rootExpressionTypeIsCacheable(): Boolean {
        return false
    }

    companion object {
        const val NAME: String = "random"
        private const val EXPECTED_ARG_COUNT: Int = 0
    }
}
