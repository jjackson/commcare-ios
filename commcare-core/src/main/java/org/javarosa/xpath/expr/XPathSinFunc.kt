package org.javarosa.xpath.expr

import kotlin.math.sin
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathSinFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return sin(FunctionUtils.toDouble(evaluatedArgs[0]))
    }

    companion object {
        const val NAME: String = "sin"
        private const val EXPECTED_ARG_COUNT: Int = 1
    }
}
