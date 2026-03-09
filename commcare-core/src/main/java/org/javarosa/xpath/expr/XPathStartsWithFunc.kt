package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathStartsWithFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return FunctionUtils.toString(evaluatedArgs[0]).startsWith(FunctionUtils.toString(evaluatedArgs[1]))
    }

    companion object {
        const val NAME: String = "starts-with"
        private const val EXPECTED_ARG_COUNT: Int = 2
    }
}
