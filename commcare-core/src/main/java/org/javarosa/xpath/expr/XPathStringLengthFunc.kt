package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathStringLengthFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        val s = FunctionUtils.toString(evaluatedArgs[0])
        if (s == null) {
            return java.lang.Double.valueOf(0.0)
        }
        return java.lang.Double.valueOf(s.length.toDouble())
    }

    companion object {
        const val NAME: String = "string-length"
        private const val EXPECTED_ARG_COUNT: Int = 1
    }
}
