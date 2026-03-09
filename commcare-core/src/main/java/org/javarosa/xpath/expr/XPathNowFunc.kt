package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException
import java.util.Date

open class XPathNowFunc : XPathFuncExpr, VolatileXPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, false)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return Date()
    }

    override fun rootExpressionTypeIsCacheable(): Boolean {
        return false
    }

    companion object {
        const val NAME: String = "now"
        private const val EXPECTED_ARG_COUNT: Int = 0
    }
}
